package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.AtividadeRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.DemandaDetalhe;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.FiscalizacaoRequest;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.HistoricoItem;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante idempotência nas operações de sincronização do modo campo (Issue #147): início da execução, registro de
 * atividades e registro de fiscalização.
 *
 * <p>A verificação e a gravação ocorrem na mesma transação do negócio, logo após o lock pessimista em {@code DEMANDA}
 * ({@link DemandaRepository#buscarParaEscrita}) e a conferência de escopo ({@link ExecucaoSuporte#exigirFiscalDesignado}):
 * requisições concorrentes com a mesma chave aguardam o commit da primeira e recebem o mesmo resultado sem duplicar
 * histórico, fiscalização ou auditoria; erros de negócio revertem a transação sem prender a chave; reutilização da
 * chave com outro payload responde {@code 409 Conflict}.
 */
@Service
public class IdempotenciaSincronizacaoService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotenciaSincronizacaoService.class);

    private final DemandaRepository demandas;
    private final ChaveIdempotenciaRepository chaves;
    private final AcompanhamentoService acompanhamento;
    private final FiscalizacaoService fiscalizacaoService;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;
    private final Clock clock;
    private final Duration ttl;

    /** Resposta de operação idempotente: HTTP status original, DTO e indicador de replay. */
    public record ResultadoIdempotente<T>(int statusHttp, T corpo, boolean repetida) {
    }

    @Autowired
    public IdempotenciaSincronizacaoService(
            DemandaRepository demandas,
            ChaveIdempotenciaRepository chaves,
            AcompanhamentoService acompanhamento,
            FiscalizacaoService fiscalizacaoService,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${semanexus.idempotencia.ttl-horas:24}") long ttlHoras) {
        this(demandas, chaves, acompanhamento, fiscalizacaoService, objectMapper, clock,
                Duration.ofHours(Math.max(1L, ttlHoras)));
    }

    IdempotenciaSincronizacaoService(
            DemandaRepository demandas,
            ChaveIdempotenciaRepository chaves,
            AcompanhamentoService acompanhamento,
            FiscalizacaoService fiscalizacaoService,
            ObjectMapper objectMapper,
            Clock clock,
            Duration ttl) {
        this.demandas = demandas;
        this.chaves = chaves;
        this.acompanhamento = acompanhamento;
        this.fiscalizacaoService = fiscalizacaoService;
        this.objectMapper = objectMapper;
        this.canonicalMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
        this.ttl = ttl;
    }

    @Transactional
    public ResultadoIdempotente<DemandaDetalhe> iniciar(UsuarioAutenticado ator, Long id, String chave) {
        return executar(ator, id, OperacaoIdempotente.INICIO, "INICIO", HttpStatus.OK.value(),
                DemandaDetalhe.class, () -> acompanhamento.iniciar(ator, id), chave);
    }

    @Transactional
    public ResultadoIdempotente<HistoricoItem> registrarAtividade(UsuarioAutenticado ator, Long id,
                                                                   AtividadeRequest pedido, String chave) {
        return executar(ator, id, OperacaoIdempotente.ATIVIDADE, pedido, HttpStatus.CREATED.value(),
                HistoricoItem.class, () -> acompanhamento.registrarAtividade(ator, id, pedido), chave);
    }

    @Transactional
    public ResultadoIdempotente<DemandaDetalhe> registrarFiscalizacao(UsuarioAutenticado ator, Long id,
                                                                      FiscalizacaoRequest pedido, String chave) {
        return executar(ator, id, OperacaoIdempotente.FISCALIZACAO, pedido, HttpStatus.OK.value(),
                DemandaDetalhe.class, () -> fiscalizacaoService.registrar(ator, id, pedido), chave);
    }

    /** Limpeza diária (03:23 UTC) das chaves de idempotência expiradas. */
    @Scheduled(cron = "${IDEMPOTENCIA_LIMPEZA_CRON:0 23 3 * * *}", zone = "UTC")
    @Transactional
    public int limparExpiradas() {
        LocalDateTime agora = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        int removidas = chaves.removerExpiradas(agora);
        if (removidas > 0) {
            LOG.info("Limpeza de chaves de idempotência: {} registro(s) expirado(s) removido(s).", removidas);
        }
        return removidas;
    }

    private <T> ResultadoIdempotente<T> executar(
            UsuarioAutenticado ator,
            Long demandaId,
            OperacaoIdempotente operacao,
            Object payloadCanonic,
            int statusSucesso,
            Class<T> tipoResposta,
            Supplier<T> acao,
            String chave) {
        String chaveValida = ChaveIdempotencia.validarChave(chave);
        String resumoRequisicao = resumoCanonic(operacao, payloadCanonic);

        Demanda demanda = demandas.buscarParaEscrita(demandaId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Demanda", demandaId));
        ExecucaoSuporte.exigirFiscalDesignado(ator, demanda);

        LocalDateTime agora = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        Optional<ChaveIdempotencia> existente = chaves.buscarParaEscrita(ator.id(), operacao, demandaId, chaveValida);
        if (existente.isPresent()) {
            ChaveIdempotencia registro = existente.get();
            if (registro.isConcluida() && registro.getExpiraEm().isAfter(agora)) {
                if (!MessageDigest.isEqual(
                        registro.getHashRequisicao().getBytes(StandardCharsets.UTF_8),
                        resumoRequisicao.getBytes(StandardCharsets.UTF_8))) {
                    throw new ConflitoException("A chave de idempotência já foi utilizada com outro conteúdo.");
                }
                T respostaSalva = desserializar(registro.getCorpoResposta(), tipoResposta);
                LOG.debug("Reenvio idempotente atendido na operação {} da demanda {}.", operacao, demandaId);
                return new ResultadoIdempotente<>(registro.getStatusHttp(), respostaSalva, true);
            }
            chaves.delete(registro);
            chaves.flush();
        }

        T resultado = acao.get();
        String corpoJson = serializar(resultado);
        chaves.save(new ChaveIdempotencia(ator.id(), demandaId, operacao, chaveValida, resumoRequisicao,
                statusSucesso, corpoJson, agora, agora.plus(ttl)));
        return new ResultadoIdempotente<>(statusSucesso, resultado, false);
    }

    private String resumoCanonic(OperacaoIdempotente operacao, Object payloadCanonic) {
        try {
            String json = canonicalMapper.writeValueAsString(payloadCanonic);
            byte[] entrada = (operacao.name() + ":" + json).getBytes(StandardCharsets.UTF_8);
            byte[] digesto = MessageDigest.getInstance("SHA-256").digest(entrada);
            return HexFormat.of().formatHex(digesto);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Falha ao calcular resumo canônico da requisição.", e);
        }
    }

    private String serializar(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar resposta idempotente.", e);
        }
    }

    private <T> T desserializar(String json, Class<T> tipo) {
        try {
            return objectMapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao desserializar resposta idempotente salva.", e);
        }
    }
}

package br.com.nexusbuilderlab.semanexus.core.setor;

import br.com.nexusbuilderlab.semanexus.common.exception.ConflitoException;
import br.com.nexusbuilderlab.semanexus.common.exception.RecursoNaoEncontradoException;
import br.com.nexusbuilderlab.semanexus.core.auditoria.Alteracoes;
import br.com.nexusbuilderlab.semanexus.core.auditoria.AuditService;
import br.com.nexusbuilderlab.semanexus.core.setor.dto.SetorDto;
import br.com.nexusbuilderlab.semanexus.core.setor.dto.SetorRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta e cadastro/edição de setores (Issues #7 e #24); toda alteração é auditada. */
@Service
public class SetorService {

    static final String ENTIDADE = "SETOR";

    private final SetorRepository setores;
    private final AuditService auditoria;

    public SetorService(SetorRepository setores, AuditService auditoria) {
        this.setores = setores;
        this.auditoria = auditoria;
    }

    /** Setores por sigla; {@code ativo == null} traz todos. */
    @Transactional(readOnly = true)
    public List<SetorDto> listar(Boolean ativo) {
        var lista = ativo == null ? setores.findAllByOrderBySiglaAsc() : setores.findByAtivoOrderBySiglaAsc(ativo);
        return lista.stream().map(SetorDto::de).toList();
    }

    /** Cria o setor (ativo por padrão); sigla duplicada responde 409. A sigla é gravada em maiúsculas. */
    @Transactional
    public SetorDto criar(SetorRequest req) {
        String sigla = sigla(req.sigla());
        setores.findBySigla(sigla).ifPresent(s -> {
            throw new ConflitoException("Já existe um setor com a sigla " + sigla + ".");
        });
        Setor setor = new Setor(sigla, req.nome().trim());
        setor.setAtivo(req.ativo() == null || req.ativo());
        setores.save(setor);
        auditoria.registrar("CRIAR", ENTIDADE, String.valueOf(setor.getId()), "Setor criado: " + sigla + ".");
        return SetorDto.de(setor);
    }

    @Transactional
    public SetorDto atualizar(Long id, SetorRequest req) {
        Setor setor = setores.findById(id).orElseThrow(() -> RecursoNaoEncontradoException.de("Setor", id));
        String sigla = sigla(req.sigla());
        setores.findBySigla(sigla).filter(s -> !s.getId().equals(id)).ifPresent(s -> {
            throw new ConflitoException("Já existe um setor com a sigla " + sigla + ".");
        });
        Alteracoes alteracoes = Alteracoes.nova().campo("sigla", setor.getSigla(), sigla)
                .campo("nome", setor.getNome(), req.nome().trim());
        if (req.ativo() != null) {
            alteracoes.campo("ativo", setor.isAtivo(), req.ativo());
        }
        setor.setSigla(sigla);
        setor.setNome(req.nome().trim());
        if (req.ativo() != null) {
            setor.setAtivo(req.ativo());
        }
        setores.save(setor);
        auditoria.registrarAlteracao("ATUALIZAR", ENTIDADE, String.valueOf(id),
                "Setor atualizado: " + sigla + (setor.isAtivo() ? " (ativo)." : " (inativo)."), alteracoes);
        return SetorDto.de(setor);
    }

    private static String sigla(String sigla) {
        return sigla.trim().toUpperCase(Locale.ROOT);
    }
}

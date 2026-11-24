package br.com.nexusbuilderlab.semanexus.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proxies cujo {@code X-Forwarded-For} é aceito no limite de requisições (Issue #134). Configurado por
 * {@code LIMITE_PROXY_CONFIAVEL}: lista separada por vírgula de IPs, faixas CIDR ou nomes de host (ex.: {@code web}, o
 * Nginx do compose, resolvido pela rede interna do Docker). O loopback é sempre confiável; vazio = só loopback.
 * Nomes são resolvidos sob demanda e guardados por {@link #VALIDADE_RESOLUCAO} (o container pode ser recriado com outro
 * IP). Qualquer outra origem tem o cabeçalho ignorado — vale o IP da conexão.
 */
public class ProxiesConfiaveis {

    private static final Logger LOG = LoggerFactory.getLogger(ProxiesConfiaveis.class);

    static final Duration VALIDADE_RESOLUCAO = Duration.ofSeconds(30);
    static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");
    private static final Pattern NOME_HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,252})$");

    /** Faixa de endereços: endereço base + tamanho do prefixo em bits. */
    private record Faixa(byte[] base, int prefixo) {
        boolean contem(byte[] ip) {
            if (ip.length != base.length) {
                return false;
            }
            int bits = prefixo;
            for (int i = 0; i < ip.length && bits > 0; i++, bits -= 8) {
                int mascara = bits >= 8 ? 0xFF : (0xFF << (8 - bits)) & 0xFF;
                if ((ip[i] & mascara) != (base[i] & mascara)) {
                    return false;
                }
            }
            return true;
        }
    }

    private final List<Faixa> faixas = new ArrayList<>();
    private final List<String> nomes = new ArrayList<>();
    private final Clock clock;
    private volatile List<byte[]> resolvidos = List.of();
    private volatile long resolvidoEmMs = Long.MIN_VALUE;

    public ProxiesConfiaveis(String configuracao, Clock clock) {
        this.clock = clock;
        if (configuracao == null) {
            return;
        }
        for (String bruto : configuracao.split(",")) {
            String item = bruto.strip();
            if (item.isEmpty()) {
                continue;
            }
            Faixa faixa = faixa(item);
            if (faixa != null) {
                faixas.add(faixa);
            } else if (NOME_HOST.matcher(item).matches()) {
                nomes.add(item.toLowerCase(Locale.ROOT));
            } else {
                LOG.warn("LIMITE_PROXY_CONFIAVEL: item ignorado (não é IP, CIDR nem nome de host válido).");
            }
        }
    }

    /** Só o loopback (padrão quando a configuração não existe, ex.: fatias de teste web). */
    public static ProxiesConfiaveis somenteLoopback() {
        return new ProxiesConfiaveis("", Clock.systemUTC());
    }

    /** true quando {@code endereco} (IP literal da conexão) é o loopback ou um proxy configurado. */
    public boolean confiavel(String endereco) {
        byte[] ip = literal(endereco);
        if (ip == null) {
            return false;
        }
        try {
            if (InetAddress.getByAddress(ip).isLoopbackAddress()) {
                return true;
            }
        } catch (UnknownHostException e) {
            return false;
        }
        for (Faixa f : faixas) {
            if (f.contem(ip)) {
                return true;
            }
        }
        for (byte[] r : nomesResolvidos()) {
            if (Arrays.equals(r, ip)) {
                return true;
            }
        }
        return false;
    }

    private List<byte[]> nomesResolvidos() {
        if (nomes.isEmpty()) {
            return List.of();
        }
        long agora = clock.millis();
        if (resolvidoEmMs != Long.MIN_VALUE && agora - resolvidoEmMs < VALIDADE_RESOLUCAO.toMillis()) {
            return resolvidos;
        }
        List<byte[]> novos = new ArrayList<>();
        for (String nome : nomes) {
            try {
                for (InetAddress a : InetAddress.getAllByName(nome)) {
                    novos.add(a.getAddress());
                }
            } catch (UnknownHostException e) {
                LOG.debug("Proxy confiável {} não resolvido agora.", nome);
            }
        }
        resolvidos = List.copyOf(novos);
        resolvidoEmMs = agora;
        return resolvidos;
    }

    private static Faixa faixa(String item) {
        String[] partes = item.split("/", -1);
        if (partes.length > 2) {
            return null;
        }
        byte[] base = literal(partes[0]);
        if (base == null) {
            return null;
        }
        int maximo = base.length * 8;
        int prefixo = maximo;
        if (partes.length == 2) {
            try {
                prefixo = Integer.parseInt(partes[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (prefixo < 0 || prefixo > maximo) {
                return null;
            }
        }
        return new Faixa(base, prefixo);
    }

    /** Bytes de um IP literal (IPv4 ou IPv6) ou nulo; nunca consulta DNS. */
    static byte[] literal(String texto) {
        if (texto == null || !IP_LITERAL.matcher(texto).matches()) {
            return null;
        }
        if (texto.contains(":")) {
            // IPv6 literal: o InetAddress valida sem consultar DNS (texto com ':' nunca é nome de host).
            try {
                return InetAddress.getByName(texto).getAddress();
            } catch (UnknownHostException e) {
                return null;
            }
        }
        // IPv4 validado aqui (ex.: "999.1.1.1" ou "abc" iriam para o DNS como nome de host).
        String[] octetos = texto.split("\\.", -1);
        if (octetos.length != 4) {
            return null;
        }
        byte[] ip = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (!octetos[i].matches("\\d{1,3}")) {
                return null;
            }
            int valor = Integer.parseInt(octetos[i]);
            if (valor > 255) {
                return null;
            }
            ip[i] = (byte) valor;
        }
        return ip;
    }
}

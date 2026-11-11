package br.com.nexusbuilderlab.semanexus.flux.demanda;

import br.com.nexusbuilderlab.semanexus.core.catalogo.StatusDemanda;
import br.com.nexusbuilderlab.semanexus.core.usuario.Perfil;
import br.com.nexusbuilderlab.semanexus.core.usuario.Usuario;
import br.com.nexusbuilderlab.semanexus.flux.demanda.dto.TipoHistorico;
import br.com.nexusbuilderlab.semanexus.security.UsuarioAutenticado;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

/** Regras comuns às etapas de execução (#13): fiscal designado e limite de texto do histórico. */
final class ExecucaoSuporte {

    static final int LIMITE_TEXTO = 2000;

    private ExecucaoSuporte() {
    }

    /** FISCAL só age na demanda em que é o fiscal designado (403); GESTOR e ADMIN agem em qualquer uma. */
    static void exigirFiscalDesignado(UsuarioAutenticado ator, Demanda demanda) {
        if (ator.perfil() != Perfil.FISCAL) {
            return;
        }
        if (demanda.getFiscal() == null || !Objects.equals(demanda.getFiscal().getId(), ator.id())) {
            throw new AccessDeniedException("Somente o fiscal designado para a demanda pode executar esta ação.");
        }
    }

    static String limitar(String texto) {
        return texto.length() <= LIMITE_TEXTO ? texto : texto.substring(0, LIMITE_TEXTO - 3) + "...";
    }

    static DemandaHistorico evento(Demanda d, TipoHistorico tipo, StatusDemanda de, StatusDemanda para, String texto,
                                   Usuario autor) {
        return new DemandaHistorico(d, tipo, de, para, limitar(texto), autor);
    }
}

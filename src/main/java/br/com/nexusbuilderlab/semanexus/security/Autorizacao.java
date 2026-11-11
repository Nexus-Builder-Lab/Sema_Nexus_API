package br.com.nexusbuilderlab.semanexus.security;

/**
 * Expressões de autorização por perfil, reutilizadas nos {@code @PreAuthorize} dos controllers.
 * Seguem a matriz de {@code docs/ARCHITECTURE.md} §6. O escopo fino (ex.: fiscal só na sua equipe) é
 * aplicado nos services.
 */
public final class Autorizacao {

    private Autorizacao() {
    }

    /** Admin e Gestor: distribuição, conclusão, consulta de estrutura. */
    public static final String ADMIN_GESTOR = "hasAnyRole('ADMIN','GESTOR')";

    /** Admin, Gestor e Analista: cadastro, edição e triagem de demandas. */
    public static final String ADMIN_GESTOR_ANALISTA = "hasAnyRole('ADMIN','GESTOR','ANALISTA')";

    /** Admin, Gestor e Fiscal: acompanhamento e fiscalização (o fiscal precisa ser o designado). */
    public static final String ADMIN_GESTOR_FISCAL = "hasAnyRole('ADMIN','GESTOR','FISCAL')";

    /** Todos exceto Consulta: upload e vínculo de documentos. */
    public static final String ESCRITA_DOCUMENTO = "hasAnyRole('ADMIN','GESTOR','FISCAL','ANALISTA')";

    /** Qualquer perfil autenticado (leituras; o escopo do FISCAL é aplicado no Service). */
    public static final String AUTENTICADO = "isAuthenticated()";

    /** Somente Admin. */
    public static final String SOMENTE_ADMIN = "hasRole('ADMIN')";
}

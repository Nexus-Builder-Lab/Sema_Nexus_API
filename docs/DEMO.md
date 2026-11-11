# SEMA NEXUS — Demonstração de 27/11/2026

> **Status:** Implementado (Issue #21) — roteiro cronometrado sobre o seed da Issue #18. Ensaio completo, com cronômetro, previsto para 26/11 (ainda **não** realizado). Mostre **somente** o que está implementado.
> **AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.** Nenhum dado oficial, real ou pessoal.

## Credenciais demonstrativas

| Perfil | E-mail | Senha | Situação |
|---|---|---|---|
| Administrador | `admin@admin.com` | `12345678` | Ativo — **usuário de teste padrão** |
| Gestor | `gestor@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Fiscal | `fiscal1@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Fiscal | `fiscal2@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Analista | `analista@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Consulta | `consulta@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |

Setores fictícios: `DEMO-GAB` (Gabinete), `DEMO-FISC` (Fiscalização), `DEMO-DOC` (Análise e Documentação). Equipes fictícias (setor `DEMO-FISC`): Alfa (responsável Fiscal 1), Bravo (Fiscal 2), Charlie (Fiscal 1; membros Fiscal 1 e 2), Delta (Gestor; membros Fiscal 2 e Analista). Todos os nomes e e-mails são simulados.

Regras: essas contas existem **apenas** nos profiles `demo`/`dev`, são gravadas só com hash BCrypt e a API se recusa a subir em outro profile se `admin@admin.com` existir. Não reutilize essas senhas em nenhum outro sistema.

## Preparação (antes da apresentação)

1. Subir: `.\scripts\demo-up.ps1` (ou `./scripts/demo-up.sh`); aguarda os healthchecks e imprime as URLs.
2. `docker compose ps`: `oracle`, `api` e `web` **healthy**.
3. Restaurar a massa: `.\scripts\demo-reset.ps1` (apaga os volumes do projeto; exige confirmação `RESETAR` ou `DEMO_RESET_CONFIRM=yes`). Aguardar o seed (120 demandas, 12 documentos).
4. Abrir http://localhost:3000 (ou `WEB_PORT` do `.env`) e entrar com `admin@admin.com` / `12345678`.
5. **Sem internet:** desconectar a rede e recarregar. Tudo roda local (Oracle, API, web em contêineres, arquivos no volume). Se o mapa de MT exibir camada de fundo externa, ela pode não carregar; conferir no ensaio (a confirmar) e, se necessário, apresentar o mapa apenas com os polígonos/marcadores.
6. Para refazer a demonstração, rode `demo-reset` de novo: o cenário volta ao estado inicial.

Usuário único recomendado para o fluxo inteiro: **admin** (perfil com todas as ações). Para mostrar perfis (3–6 min, opcional): `gestor@demo.semanexus.local`, `fiscal1@demo.semanexus.local`, `analista@demo.semanexus.local` e `consulta@demo.semanexus.local` (senha `Demo@Sema2026`; consulta só lê).

## Roteiro (~10 minutos)

Mesmos registros do começo ao fim (tabela de cenário abaixo; localizar sempre pelo **título**, pois o número `DEM-AAAA/NNNN` varia com o dia).

| Tempo | Bloco | Passos (tela → ação) | Registro |
|---|---|---|---|
| 0–1 | Apresentação | Home: problema (demandas ambientais, fiscalização e documentos dispersos), objetivo, três módulos (Intelligence, Flux, DocIA), faixa "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS" | — |
| 1–3 | Intelligence | `/intelligence`: indicadores (total, abertas, atrasadas), séries mensais (queimadas concentradas em jul–out), distribuição por bioma (Amazônia 11 · Cerrado 10 · Pantanal 5 em municípios) e por categoria; `/intelligence/inteligencia-ambiental`: mapa de MT com filtros. **Anotar o número de Pendentes e de Finalizadas** para comparar no minuto 8 | todos |
| 3–6 | Flux | `/flux/demandas`, filtrar/buscar. (a) **Cadastrar** nova demanda fictícia (botão "Nova"; ex.: "Demonstração ao vivo — Sinop") e mostrar o status inicial. Em seguida seguir o ciclo nos registros prontos, para ganhar tempo: (b) **Triagem** de C4 (Pendente → iniciar triagem → triar); (c) **Distribuição** de C3 a uma equipe/fiscal; (d) **Acompanhamento**: abrir C6 (distribuída, atrasada) e mostrar prazo/histórico; (e) **Fiscalização** em C2 (Em andamento): registrar fiscalização; (f) **Conclusão** de C1 (Aguardando conclusão) pelo gestor | C4, C3, C6, C2, C1 |
| 6–8 | DocIA | `/docia/documentos`: pesquisar `queimada`, depois `parecer`; abrir o "Parecer Técnico nº 014/2026" (versões 1 e 2: mostrar as duas e o histórico); **upload** de um documento fictício e **vínculo** a uma demanda (ex.: C2); baixar um arquivo | C1, C2 |
| 8–9 | Integração | Abrir C1 e C2: documentos vinculados e histórico com o evento de documento; voltar a `/intelligence` e mostrar os indicadores alterados (Pendentes −1 por C4, Finalizadas +1 por C1, total +1 pela nova demanda) | C1, C2, C4 |
| 9–10 | Arquitetura | React + Spring Boot 3.5 + Oracle 23ai Free, Flyway, JWT/BCrypt com autorização por perfil no backend, OpenAPI (`/swagger-ui.html`), arquivos em volume, Docker Compose (`demo-up`/`demo-reset`), CI; limitações e próximos passos (seção abaixo) | — |

Dicas: se uma ação falhar por perfil, entre como `admin`. Se estourar o tempo, cortar (a) cadastro ao vivo e o download no DocIA. Antes de afirmar "indicadores atualizados" confirme no ensaio que o dashboard refletiu as mudanças (a validar em 26/11).

## Massa e cenário fixo do seed (Implementado — Issue #18)

Após `demo-reset` (ou na primeira subida `demo`/`dev`) a API cria sempre o mesmo conjunto fictício: **120 demandas** em 26 municípios de MT (Amazônia 11, Cerrado 10, Pantanal 5), nos 7 status (10 Pendente · 10 Em triagem · 12 Aguardando distribuição · 16 Distribuída · 26 Em andamento · 16 Aguardando conclusão · 30 Finalizada), com fiscalização registrada nas 46 em Aguardando conclusão/Finalizada, algumas atrasadas, e **12 documentos** (PDF/TXT) vinculados. As datas são relativas ao dia da subida (últimos 12 meses; queimadas concentradas em jul–out); o **número** `DEM-AAAA/NNNN` depende do dia, por isso o roteiro identifica os registros pelo **título**. Rodar o seed de novo não duplica nada.

| Chave | Demanda (título, fictícia) | Status | Uso no roteiro | Documentos vinculados |
|---|---|---|---|---|
| C4 | Descarte irregular de resíduos — Sinop (cenário de demonstração) | Pendente | Triagem ao vivo | Termo de Denúncia — Sinop |
| C3 | Pesca irregular em período de defeso — Barão de Melgaço (cenário de demonstração) | Aguardando distribuição | Distribuição ao vivo | Ofício de Solicitação de Vistoria — Barão de Melgaço |
| C2 | Desmatamento ilegal em área de floresta — Colniza (cenário de demonstração) | Em andamento | Registro de fiscalização ao vivo | Relatório de Monitoramento de Desmatamento — Colniza; Guia de Procedimentos de Fiscalização Ambiental |
| C1 | Queimada em pastagem e vegetação nativa — Cáceres (cenário de demonstração) | Aguardando conclusão | Conclusão pelo gestor; comparação de versões | **Parecer Técnico nº 014/2026 — versão 1** e **versão 2**; Relatório de Vistoria — Cáceres |
| C6 | Foco de queimada com prazo vencido — Poconé (cenário de demonstração) | Distribuída (atrasada) | Indicador de atrasadas | Nota Técnica — Focos de Calor em Julho a Outubro; Instrução Normativa Demonstrativa sobre Queimadas; Boletim Mensal de Focos de Calor (simulado) |
| C5 | Captação irregular de recursos hídricos — Primavera do Leste (cenário de demonstração) | Finalizada | Histórico completo | Laudo de Recursos Hídricos — Primavera do Leste; Relatório Final de Fiscalização — Primavera do Leste |

Pesquisas sugeridas no DocIA: `queimada`, `parecer`, `outorga`, `defeso`. Todos os textos trazem "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS".

## Limitações e futuro

**Limitações atuais (implementado como descrito):**
- Dados exclusivamente fictícios; sem integração com sistemas institucionais.
- Busca documental **textual** (ignora caixa e acento); sem OCR.
- Sem comparação automática de versões de documentos: as duas versões do Parecer 014/2026 existem como documentos distintos e são abertas lado a lado manualmente.
- Sem assistente de IA, sem geração de autos de infração, sem Kanban.
- Satélite/INPE e ocorrências georreferenciadas em tempo real não estão no escopo apresentado.

**Futuro (não apresentar como implementado):** busca semântica, OCR, comparação e assistente DocIA, Kanban, ocorrências ambientais georreferenciadas, camada de satélite com dados públicos do INPE (identificados), integrações institucionais autorizadas.

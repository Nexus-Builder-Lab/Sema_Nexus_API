# SEMA NEXUS — Demonstração de 27/11/2026

> **Status:** Implementado (Issue #21) — roteiro cronometrado sobre o seed da Issue #18. **Ensaiado pelo proprietário em 12/11/2026** no Docker, após `demo-reset`: coube em 10 minutos. Mostre **somente** o que está implementado.
> **AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.** Nenhum dado oficial, real ou pessoal.

## Credenciais demonstrativas

| Perfil | E-mail | Senha | Situação |
|---|---|---|---|
| Administrador | `admin@admin.com` | `12345678` | Ativo — **usuário de teste padrão** |
| Gestor | `gestor@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Fiscal | `fiscal1@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5); equipes Alfa–Delta, **sem demandas** desde a #91 |
| Fiscal | `fiscal2@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Analista | `analista@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Consulta | `consulta@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Fiscal (Cuiabá) | `fiscal.cba1@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91); **conta do botão "Fiscal"** do login (Issue #112) |
| Fiscal (Cuiabá) | `fiscal.cba2@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |
| Fiscal (Cáceres) | `fiscal.cac1@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |
| Fiscal (Cáceres) | `fiscal.cac2@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |
| Fiscal (Rondonópolis) | `fiscal.roo1@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |
| Fiscal (Rondonópolis) | `fiscal.roo2@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |
| Fiscal (Barra do Garças) | `fiscal.bag1@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |
| Fiscal (Barra do Garças) | `fiscal.bag2@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |
| Fiscal (Sinop) | `fiscal.sno1@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |
| Fiscal (Sinop) | `fiscal.sno2@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |

Setores fictícios: `DEMO-GAB` (Gabinete), `DEMO-FISC` (Fiscalização), `DEMO-DOC` (Análise e Documentação). Equipes fictícias (setor `DEMO-FISC`): Alfa (responsável Fiscal 1), Bravo (Fiscal 2), Charlie (Fiscal 1; membros Fiscal 1 e 2), Delta (Gestor; membros Fiscal 2 e Analista). Todos os nomes e e-mails são simulados.

**Núcleos Regionais de Fiscalização (fictícios — Issue #91):** um setor por região intermediária do IBGE (valor de `MUNICIPIO.REGIAO`), cada um com 1 equipe ("Equipe <região> (demonstrativa)", responsável o fiscal 1) e 2 fiscais: `NR-CBA` (Cuiabá), `NR-CAC` (Cáceres), `NR-ROO` (Rondonópolis), `NR-BAG` (Barra do Garças) e `NR-SNO` (Sinop). As demandas distribuídas da massa pertencem à equipe do núcleo da região do município; as em PENDENTE, EM_TRIAGEM e AGUARDANDO_DISTRIBUICAO ficam sem equipe. As equipes Alfa a Delta (setor `DEMO-FISC`) continuam existindo, sem demandas da massa. Após atualizar, rode `demo-reset` para recriar a massa.

Regras: essas contas existem **apenas** nos profiles `demo`/`dev`, são gravadas só com hash BCrypt e a API se recusa a subir em outro profile se `admin@admin.com` existir. Não reutilize essas senhas em nenhum outro sistema.

## Preparação (antes da apresentação)

> **Acesse sempre por `http://127.0.0.1:3000`** (aplicação) e `http://127.0.0.1:18081` (API/Swagger, porta `API_PORT` do `.env`). Na máquina do proprietário, `http://localhost:3000` **não funciona**: o Windows resolve `localhost` para IPv6 (`::1`), onde as portas 3000 e 8080 são de outro serviço. Os scripts `demo-up`/`demo-reset` ainda imprimem `localhost` — troque por `127.0.0.1`.
>
> Rode `demo-reset` **um ou dois dias antes** da apresentação: as datas da massa são relativas ao dia do reset (prazos, atrasadas e finalizadas recentes no Fluxo de atividades).

1. Subir: `.\scripts\demo-up.ps1` (ou `./scripts/demo-up.sh`); aguarda os healthchecks e imprime as URLs.
2. `docker compose ps`: `oracle`, `api` e `web` **healthy**.
3. Restaurar a massa: `.\scripts\demo-reset.ps1` (apaga apenas os volumes do projeto `sema_nexus`, sem afetar outros projetos; exige confirmação `RESETAR` ou `DEMO_RESET_CONFIRM=yes`). Aguardar o seed (120 demandas, 12 documentos).
4. Abrir **http://127.0.0.1:3000** (ou `WEB_PORT` do `.env`; não use `localhost`) e entrar com `admin@admin.com` / `12345678`.
5. **Sem internet:** o mapa de MT funciona offline (camadas OpenStreetMap e Visão de satélite desligadas por padrão; ambas exigem internet, e o satélite exige `ESRI_API_KEY` no `.env` — ver `docs/DOCKER.md`) e todo o ambiente roda local (Oracle, API, web, arquivos no volume). Desconectar a rede e recarregar para confirmar.
6. Para refazer a demonstração, rode `demo-reset` de novo: o cenário volta ao estado inicial.

Usuário único recomendado para o fluxo inteiro: **admin** (perfil com todas as ações). Para mostrar perfis (3–6 min, opcional): `gestor@demo.semanexus.local`, `fiscal.cba1@demo.semanexus.local` (Núcleo Cuiabá; o fiscal só vê as demandas da sua equipe ou atribuídas a ele), `analista@demo.semanexus.local` e `consulta@demo.semanexus.local` (senha `Demo@Sema2026`; consulta só lê).

## Roteiro (~10 minutos)

Mesmos registros do começo ao fim (tabela de cenário abaixo; localizar sempre pelo **título**, pois o número `DEM-AAAA/NNNN` varia com o dia).

| Tempo | Bloco | Passos (tela → ação) | Registro |
|---|---|---|---|
| 0–1 | Apresentação | Home: problema (demandas ambientais, fiscalização e documentos dispersos), objetivo, três módulos (Intelligence, Flux, DocIA), faixa "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS" | — |
| 1–3 | Intelligence | `/intelligence`: indicadores (total, abertas, atrasadas), séries mensais (queimadas concentradas em jul–out), distribuição por bioma (Amazônia 11 · Cerrado 10 · Pantanal 5 em municípios) e por categoria; `/intelligence/inteligencia-ambiental`: mapa de MT com filtros. **Anotar o total e o número de Pendentes e de Finalizadas** para comparar no minuto 8 | todos |
| 3–6 | Flux | `/flux/demandas`, filtrar/buscar. (a) **Cadastrar** nova demanda fictícia (botão "Nova"; ex.: "Demonstração ao vivo — Sinop") e mostrar o status inicial. Em seguida seguir o ciclo nos registros prontos, para ganhar tempo: (b) **Triagem** de C4 (Pendente → iniciar triagem → triar); (c) **Distribuição** de C3 a uma equipe/fiscal; (d) **Acompanhamento**: abrir C6 (distribuída, atrasada) e mostrar prazo/histórico; (e) **Fiscalização** em C2 (Em andamento): registrar fiscalização; (f) **Conclusão** de C1 (Aguardando conclusão) pelo gestor | C4, C3, C6, C2, C1 |
| 6–8 | DocIA | `/docia/documentos`: pesquisar `queimada`, depois `parecer`; abrir o "Parecer Técnico nº 014/2026" (abrir as versões 1 e 2; elas não têm histórico de upload, pois vêm do seed. Mostrar o histórico nas demandas C1/C2 ou no documento enviado ao vivo); **upload** de um documento fictício e **vínculo** a uma demanda (ex.: C2); baixar um arquivo | C1, C2 |
| 8–9 | Integração | Abrir C1 e C2: documentos vinculados e histórico com o evento de documento; voltar a `/intelligence` e mostrar os indicadores alterados (Pendentes inalterado: a demanda nova entra como Pendente (+1) e a triagem de C4 tira uma (−1); Finalizadas +1 por C1; Atrasadas −1, porque C1 está atrasada e passa a Finalizada; total +1 pela nova demanda) | C1, C2, C4 |
| 9–10 | Arquitetura | React + Spring Boot 3.5 + Oracle 23ai Free, Flyway, JWT/BCrypt com autorização por perfil no backend, OpenAPI (`/swagger-ui.html`), arquivos em volume, Docker Compose (`demo-up`/`demo-reset`), CI; limitações e próximos passos (seção abaixo) | — |

Dicas: se uma ação falhar por perfil, entre como `admin`. Se estourar o tempo, cortar (a) cadastro ao vivo e o download no DocIA. **Ensaio:** feito pelo proprietário em 12/11/2026 — o roteiro coube em 10 minutos.

## Massa e cenário fixo do seed (Implementado — Issue #18)

Após `demo-reset` (ou na primeira subida `demo`/`dev`) a API cria sempre o mesmo conjunto fictício: **120 demandas** em 26 municípios de MT (Amazônia 11, Cerrado 10, Pantanal 5), nos 7 status (10 Pendente · 10 Em triagem · 12 Aguardando distribuição · 16 Distribuída · 26 Em andamento · 16 Aguardando conclusão · 30 Finalizada), com fiscalização registrada nas 46 em Aguardando conclusão/Finalizada, algumas atrasadas, e **12 documentos** (PDF/TXT) vinculados. As datas são relativas ao dia da subida (últimos 12 meses; queimadas concentradas em jul–out); o **número** `DEM-AAAA/NNNN` depende do dia, por isso o roteiro identifica os registros pelo **título**. Das 30 finalizadas, cerca de metade foi concluída nos últimos 30 dias (as demais, antes), 6 por núcleo regional, com tempo médio de conclusão diferente em cada núcleo (de ~7 a ~21 dias). Rodar o seed de novo não duplica nada. As contagens absolutas (120 demandas, 12 documentos) valem **somente em volume limpo, logo após `demo-reset`**.

| Chave | Demanda (título, fictícia) | Status | Uso no roteiro | Documentos vinculados |
|---|---|---|---|---|
| C4 | Descarte irregular de resíduos — Sinop (cenário de demonstração) | Pendente | Triagem ao vivo | Termo de Denúncia — Sinop |
| C3 | Pesca irregular em período de defeso — Barão de Melgaço (cenário de demonstração) | Aguardando distribuição | Distribuição ao vivo | Ofício de Solicitação de Vistoria — Barão de Melgaço |
| C2 | Desmatamento ilegal em área de floresta — Colniza (cenário de demonstração) | Em andamento | Registro de fiscalização ao vivo | Relatório de Monitoramento de Desmatamento — Colniza; Guia de Procedimentos de Fiscalização Ambiental |
| C1 | Queimada em pastagem e vegetação nativa — Cáceres (cenário de demonstração) | Aguardando conclusão | Conclusão pelo gestor; abrir as duas versões do parecer | **Parecer Técnico nº 014/2026 — versão 1** e **versão 2**; Relatório de Vistoria — Cáceres |
| C6 | Foco de queimada com prazo vencido — Poconé (cenário de demonstração) | Distribuída (atrasada) | Indicador de atrasadas | Nota Técnica — Focos de Calor em Julho a Outubro; Instrução Normativa Demonstrativa sobre Queimadas; Boletim Mensal de Focos de Calor (simulado) |
| C5 | Captação irregular de recursos hídricos — Primavera do Leste (cenário de demonstração) | Finalizada | Histórico completo | Laudo de Recursos Hídricos — Primavera do Leste; Relatório Final de Fiscalização — Primavera do Leste |

Pesquisas sugeridas no DocIA: `queimada`, `parecer`, `outorga`, `defeso`. Todos os textos trazem "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS".

## Camada de satélite do INPE (Implementado no backend — Issue #34)

- Após `demo-reset`, a API carrega o snapshot de dados **públicos reais** do INPE (focos de calor e alertas DETER de MT; período em `GET /api/intelligence/referencia/metadados`). Funciona sem internet.
- Como mencionar: "além dos dados fictícios da demonstração, o mapa mostra uma camada de referência com dados públicos do INPE, identificada e separada; não é dado oficial da SEMA-MT e não altera os indicadores".
- Atualizar o snapshot (com internet): `scripts/referencia/baixar-inpe.ps1`.

## Limitações e futuro

- Dados fictícios, exceto a camada de referência pública do INPE (identificada); sem integração com sistemas institucionais.
- Busca documental textual (semântica é futura); sem OCR.
- Assistente DocIA, se presente, em modo demonstrativo identificado (sem IA generativa) salvo provider externo configurado.
- Sem geração de autos de infração.
- Futuro (não apresentar como implementado): busca semântica, OCR, ocorrências ambientais georreferenciadas em tempo real, integrações institucionais autorizadas.

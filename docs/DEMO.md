# SEMA NEXUS — Demonstração de 27/11/2026

> **Status:** Planejado. O roteiro definitivo, com os registros exatos do seed, é entregue pela Issue API#21 e ensaiado em 26/11. Mostre na apresentação **somente** o que estiver implementado.
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
| Fiscal (Cuiabá) | `fiscal.cba1@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #91) |
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

1. Subir o ambiente: `.\scripts\demo-up.ps1` (ou `./scripts/demo-up.sh`) — Implementado (Issue #9); aguarda os healthchecks e imprime as URLs.
2. Conferir `docker compose ps`: `oracle`, `api` e `web` **healthy**.
3. Restaurar a massa demonstrativa: `.\scripts\demo-reset.ps1` (confirmação explícita).
4. Abrir http://localhost:3000 e entrar com `admin@admin.com` / `12345678`.
5. Desconectar a internet e confirmar que mapa e telas continuam funcionando.

## Roteiro (~10 minutos) — esqueleto

| Tempo | Bloco | Mostrar |
|---|---|---|
| 0–1 | Apresentação | Problema, objetivo do SEMA NEXUS, faixa "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS" |
| 1–3 | Intelligence | Dashboard, indicadores, distribuição territorial por bioma, mapa de MT |
| 3–6 | Flux | Cadastrar demanda fictícia → triagem → distribuição → início → registro de fiscalização → conclusão |
| 6–8 | DocIA | Upload de documento, pesquisa, vínculo à demanda; comparação ou assistente **só se implementados** |
| 8–9 | Integração | Demanda com documento vinculado; indicadores do Intelligence atualizados |
| 9–10 | Arquitetura | React + Spring Boot + Oracle, Docker Compose, CI, próximos passos |

Use os **mesmos registros** do começo ao fim (cenário definido na Issue API#18).

## Massa e cenário fixo do seed (Implementado — Issue #18)

Após `demo-reset` (ou na primeira subida `demo`/`dev`) a API cria sempre o mesmo conjunto fictício: **120 demandas** em 26 municípios de MT (Amazônia 11, Cerrado 10, Pantanal 5), nos 7 status (10 Pendente · 10 Em triagem · 12 Aguardando distribuição · 16 Distribuída · 26 Em andamento · 16 Aguardando conclusão · 30 Finalizada), com fiscalização registrada nas 46 em Aguardando conclusão/Finalizada, algumas atrasadas, e **12 documentos** (PDF/TXT) vinculados. As datas são relativas ao dia da subida (últimos 12 meses; queimadas concentradas em jul–out); o **número** `DEM-AAAA/NNNN` depende do dia, por isso o roteiro identifica os registros pelo **título**. Das 30 finalizadas, cerca de metade foi concluída nos últimos 30 dias (as demais, antes), 6 por núcleo regional, com tempo médio de conclusão diferente em cada núcleo (de ~7 a ~21 dias). Rodar o seed de novo não duplica nada.

| Chave | Demanda (título, fictícia) | Status | Uso no roteiro | Documentos vinculados |
|---|---|---|---|---|
| C4 | Descarte irregular de resíduos — Sinop (cenário de demonstração) | Pendente | Triagem ao vivo | Termo de Denúncia — Sinop |
| C3 | Pesca irregular em período de defeso — Barão de Melgaço (cenário de demonstração) | Aguardando distribuição | Distribuição ao vivo | Ofício de Solicitação de Vistoria — Barão de Melgaço |
| C2 | Desmatamento ilegal em área de floresta — Colniza (cenário de demonstração) | Em andamento | Registro de fiscalização ao vivo | Relatório de Monitoramento de Desmatamento — Colniza; Guia de Procedimentos de Fiscalização Ambiental |
| C1 | Queimada em pastagem e vegetação nativa — Cáceres (cenário de demonstração) | Aguardando conclusão | Conclusão pelo gestor; comparação de versões | **Parecer Técnico nº 014/2026 — versão 1** e **versão 2**; Relatório de Vistoria — Cáceres |
| C6 | Foco de queimada com prazo vencido — Poconé (cenário de demonstração) | Distribuída (atrasada) | Indicador de atrasadas | Nota Técnica — Focos de Calor em Julho a Outubro; Instrução Normativa Demonstrativa sobre Queimadas; Boletim Mensal de Focos de Calor (simulado) |
| C5 | Captação irregular de recursos hídricos — Primavera do Leste (cenário de demonstração) | Finalizada | Histórico completo | Laudo de Recursos Hídricos — Primavera do Leste; Relatório Final de Fiscalização — Primavera do Leste |

Pesquisas sugeridas no DocIA: `queimada`, `parecer`, `outorga`, `defeso`. Todos os textos trazem "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS".

## Camada de satélite do INPE (Implementado no backend — Issue #34)

- Após `demo-reset`, a API carrega o snapshot de dados **públicos reais** do INPE (focos de calor e alertas DETER de MT; período em `GET /api/intelligence/referencia/metadados`). Funciona sem internet.
- Como mencionar: "além dos dados fictícios da demonstração, o mapa mostra uma camada de referência com dados públicos do INPE, identificada e separada; não é dado oficial da SEMA-MT e não altera os indicadores".
- Atualizar o snapshot (com internet): `scripts/referencia/baixar-inpe.ps1`.

## Limitações e futuro (a confirmar em 26/11)

- Dados fictícios, exceto a camada de referência pública do INPE (identificada); sem integração com sistemas institucionais.
- Busca documental textual (semântica é futura); sem OCR.
- Assistente DocIA, se presente, em modo demonstrativo identificado (sem IA generativa) salvo provider externo configurado.
- Sem geração de autos de infração.
- Futuro: busca semântica, OCR, ocorrências ambientais georreferenciadas, Kanban, integrações institucionais autorizadas.

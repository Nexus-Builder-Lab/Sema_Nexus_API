# SEMA ALERTA — Contrato da API

> **Status:** Em desenvolvimento — **Implementado:** demandas — cadastro, edição, listagem filtrada, detalhes, histórico e exclusão lógica (Issue #11), triagem, distribuição e `GET /api/equipes/{id}/demandas` (Issue #12); autenticação (`/api/auth/*`, Issue #4), catálogos e estrutura (`/api/catalogos/*`, `/api/setores`, `/api/equipes[/{id}/membros]`, Issue #7). **Implementado também:** upload `POST /api/documentos` e download `GET /api/documentos/{id}/arquivo` (Issue #15). **Implementado também:** biblioteca `GET /api/documentos`, detalhes `GET /api/documentos/{id}` e vínculos `/api/demandas/{id}/documentos` (Issue #16). **Contrato publicado, ainda respondendo `501`:** indicadores, mapa e dashboard (Issue #7 publica o contrato; cada endpoint é implementado na Issue indicada na tabela). **Implementado também:** consulta de auditoria `GET /api/auditoria` (Issue #14; filtros `de`, `ate`, `usuarioId`, `acao`, `entidade`; ordenação por data decrescente por padrão; detalhes têm senhas e tokens mascarados). **Implementado também:** gestão de usuários, setores e equipes (Issue #24, seção "Gestão de usuários, setores e equipes"). **Planejado:** os demais endpoints P1.
>
> A fonte da verdade é o OpenAPI gerado pelo springdoc (`/v3/api-docs`, `/swagger-ui.html`) e exportado pelo CI como artefato `openapi.json` (job **Exportar contrato OpenAPI**). Este documento registra os **padrões**; alterou endpoint, atualize as anotações e este arquivo. O teste `ContratoOpenApiTest` falha se um endpoint P0 sair do contrato ou se um endpoint novo não for registrado nele.

## Padrões

| Tema | Regra |
|---|---|
| Base | Prefixo `/api`, JSON UTF-8. Datas `AAAA-MM-DD` (`2026-11-27`); instantes sem deslocamento no fuso `America/Cuiaba` (`2026-11-27T14:30:00`). Campos nulos são omitidos da resposta |
| Autenticação | `Authorization: Bearer <jwt>`; públicos: `POST /api/auth/login`, `GET /api/auth/config`, `POST /api/auth/demo/{perfil}` (só profile `demo`), `GET /actuator/health` |
| Autorização | Por perfil no backend (`@PreAuthorize`); matriz em [ARCHITECTURE.md](ARCHITECTURE.md) §6. Escopo fino (fiscal só na sua equipe) no service |
| Paginação | `?page=0&size=20&sort=campo,asc` → `{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`. Padrão `size=20`; `size` acima de 500 é reduzido a 500; `page` começa em 0. Só as listagens de demandas e documentos (e as demandas de uma equipe) são paginadas; catálogos, setores, equipes e membros são listas curtas e retornam um array |
| Filtros | Query params opcionais e combináveis; datas `de`/`ate` inclusivas (`AAAA-MM-DD`); enums pelo **código** (`EM_ANDAMENTO`); valor inválido → `400` |
| Enums | Sempre o código estável em MAIÚSCULAS (`MEDIA`, `AGUARDANDO_DISTRIBUICAO`); o rótulo para exibição vem de `GET /api/catalogos/*` |
| Identificação | IDs numéricos; município pelo **código IBGE** (7 dígitos); demanda também tem `numero` (`DEM-AAAA/NNNN`) |
| Upload | `multipart/form-data` (`POST /api/documentos`): parte `arquivo` + campos de metadados; download binário em `GET /api/documentos/{id}/arquivo` |
| Erros | `application/problem+json` (RFC 9457): `type`, `title`, `status`, `detail`, `instance` e, em validação, `errors: [{ "campo": "titulo", "mensagem": "..." }]`. Nunca stacktrace. Todas as respostas 4xx/5xx documentadas no OpenAPI apontam para o schema `Problema` |
| Dados simulados | Respostas de indicadores, mapa e dashboard incluem `"demonstrativo": true` e `"aviso": "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS"` |

## Códigos HTTP

| Código | Uso |
|---|---|
| 200 / 201 / 204 | Sucesso (201 em criações — `POST /api/demandas`, `/atividades`, `/api/documentos`, vínculo de documento; 204 em exclusões e logout) |
| 400 | Validação de campos (`errors` por campo), enum/data/número inválido em query ou corpo, JSON malformado |
| 401 | Sem token, token inválido/expirado ou **revogado no logout** |
| 403 | Perfil sem permissão ou fora do escopo (ex.: fiscal em demanda de outra equipe) |
| 404 | Recurso inexistente |
| 409 | Conflito de estado (transição inválida, edição fora de PENDENTE/EM_TRIAGEM, vínculo duplicado) |
| 413 / 415 | Upload grande demais / tipo não permitido |
| 429 | Limite de requisições excedido (login, IA, upload — Issue #134). Cabeçalho `Retry-After` (segundos) e corpo `problem+json` com `type` `https://semanexus.demo/problemas/limite-requisicoes`, `categoria` (`LOGIN`/`IA`/`UPLOAD`) e `retryAfterSegundos` |
| 501 | Endpoint do contrato ainda não implementado (somente até a Issue correspondente). Corpo `problem+json` com `type` `https://semanexus.demo/problemas/nao-implementado` e a propriedade `issue` |

A ordem de avaliação é **autenticação (401) → perfil (403) → validação do corpo/parâmetros (400) → regra de negócio (404/409/…)**; os endpoints ainda em `501` respondem 501 apenas depois de passar por 401, 403 e 400.

## Endpoints (MVP)

**Status:** ✅ Implementado · 📄 Contrato publicado (responde `501` até a Issue indicada).

| Método | Caminho | Perfil | Issue | Status |
|---|---|---|:-:|:-:|
| POST | `/api/auth/login` · `/api/auth/logout` | público / autenticado | #4 | ✅ |
| GET | `/api/auth/me` · `/api/auth/config` | autenticado / público | #4 | ✅ |
| POST | `/api/auth/demo/{perfil}` | público, **só profile `demo`** (404 nos demais) | #83 | ✅ |
| GET | `/api/catalogos/{municipios,biomas,categorias,origens,prioridades,status}` | qualquer autenticado | #7 | ✅ |
| GET | `/api/setores` · `/api/equipes` · `/api/equipes/{id}/membros` | Admin, Gestor | #7 | ✅ |
| POST · PUT · GET · DELETE | `/api/demandas` · `/api/demandas/{id}` (PUT só em PENDENTE/EM_TRIAGEM, senão 409; DELETE só em PENDENTE, senão 409; número `DEM-AAAA/NNNN` automático) (cadastro: Admin, Gestor, Analista; exclusão lógica: Admin; consulta: todos, fiscal só da sua equipe) | ver ARCHITECTURE §6 | #11 | ✅ |
| GET | `/api/demandas/{id}/historico` | consulta | #11 | ✅ |
| POST | `/api/demandas/{id}/triagem/iniciar` · `/triagem` (transição inválida 409; fiscal fora da equipe 400) | Admin, Gestor, Analista | #12 | ✅ |
| POST | `/api/demandas/{id}/distribuicao` · GET `/api/equipes/{id}/demandas` | Admin, Gestor | #12 | ✅ |
| POST | `/api/demandas/{id}/inicio` · `/atividades` · `/fiscalizacao` | fiscal designado, Gestor, Admin | #13 | ✅ |
| POST | `/api/demandas/{id}/conclusao` · `/devolucao` | Gestor, Admin | #13 | ✅ |
| GET | `/api/demandas/checklists` (modelos de checklist por categoria) | consulta | #145 | ✅ |
| POST · GET · DELETE | `/api/demandas/{id}/fotos` (multipart) · `/fotos/{fotoId}/arquivo` · `/fotos/{fotoId}` | envio: fiscal designado, Gestor, Admin; leitura: escopo da demanda; exclusão: só quem enviou | #145 | ✅ |
| GET | `/api/auditoria` | Admin | #14 | Implementado |
| POST · GET | `/api/documentos` (multipart; Admin, Gestor, Fiscal, Analista) · `/api/documentos/{id}/arquivo` | ver ARCHITECTURE §6 | #15 | ✅ |
| POST | `/api/documentos/pre-analise` (multipart `arquivo`; Admin, Gestor, Fiscal, Analista; nada é salvo) | título, município, data, palavras-chave, vínculos `DEM-AAAA/NNNN` e avisos (ex.: OCR indisponível) | #140 | ✅ |
| GET | `/api/documentos` (biblioteca com filtros) · `/api/documentos/{id}` | qualquer autenticado | #16 | ✅ |
| GET · POST · DELETE | `/api/demandas/{id}/documentos[/{documentoId}]` | ver ARCHITECTURE §6 | #16 | ✅ |
| GET | `/api/intelligence/{resumo,serie-mensal,por-municipio,por-bioma,por-categoria,por-situacao,mapa}` | qualquer autenticado | #17 | ✅ |
| GET | `/api/intelligence/sentinela/briefing` | ADMIN, GESTOR (ANALISTA, FISCAL, CONSULTA → 403) | #138 | ✅ |
| POST | `/api/agentes/assistente-global` | Autenticado (todos os perfis; escopo do usuário nas ferramentas) | #139 | ✅ |
| GET | `/api/relatorios/demandas[.csv]` | ADMIN, GESTOR, ANALISTA, CONSULTA (FISCAL → 403) | #28 | ✅ |
| GET | `/api/dashboard` | qualquer autenticado | #17 | ✅ |
| GET | `/actuator/health` | público | #1 | ✅ |

Cada operação do contrato publicado traz no OpenAPI `x-implementado: false` e `x-issue: <n>`, além do aviso no início da `description`. Quando a Issue implementa o endpoint, remove-se `@ContratoPendente` do método e a operação deixa de declarar `501` (o contrato — caminhos, DTOs e erros — **não** muda; mudança incompatível exige avisar o Web e atualizar este arquivo). O teste `ContratoOpenApiTest` mantém a lista de pendentes: ao implementar um endpoint, retire-o de `PENDENTES`.

### P1 (somente se houver folga)

| Método | Caminho | Issue |
|---|---|:-:|
| GET · POST · PUT · PATCH | `/api/usuarios[/{id}[/ativacao]]` e cadastro/edição de `/api/setores` e `/api/equipes[/{id}/membros]` (a consulta já existe, Issue #7) | #24 |
| GET · PATCH | `/api/notificacoes[/{id}/lida]` | #25 |
| POST | `/api/documentos/comparacao` | #26 |
| POST | `/api/docia/comparacao/explicar` | #142 |
| POST | `/api/demandas/{id}/minuta-vistoria` | #137 |

## Catálogos e estrutura (Implementado — Issue #7)

Leitura, sem paginação. Catálogos: qualquer perfil autenticado. Setores, equipes e membros: **Admin e Gestor** (matriz de ARCHITECTURE §6); outros perfis recebem `403`.

| Endpoint | Resposta |
|---|---|
| `GET /api/catalogos/municipios[?bioma=CERRADO]` | `200` array de `{id, nome, regiao, bioma, latitude, longitude}` — os 142 municípios de MT em ordem alfabética (`id` = código IBGE); `bioma` inválido → `400` |
| `GET /api/catalogos/biomas` | `200` `[{codigo, rotulo, ordem}]` — `AMAZONIA`, `CERRADO`, `PANTANAL` |
| `GET /api/catalogos/categorias` | `200` as 8 categorias: `DESMATAMENTO_ILEGAL`, `QUEIMADA_INCENDIO`, `RECURSOS_HIDRICOS`, `FAUNA_SILVESTRE`, `PESCA_IRREGULAR`, `MINERACAO_IRREGULAR`, `RESIDUOS_POLUICAO`, `AREA_PRESERVACAO_PERMANENTE` |
| `GET /api/catalogos/origens` | `200` as 5 origens: `DENUNCIA`, `MONITORAMENTO`, `SOLICITACAO_INSTITUCIONAL`, `ACAO_PLANEJADA`, `ANALISE_AMBIENTAL` |
| `GET /api/catalogos/prioridades` | `200` `BAIXA`, `MEDIA`, `ALTA`, `URGENTE` (da menor para a maior) |
| `GET /api/catalogos/status` | `200` os 7 status na ordem do fluxo (`PENDENTE` … `FINALIZADA`). "Atrasada" **não** é status |
| `GET /api/setores[?ativo=true]` | `200` array de `{id, sigla, nome, ativo}` por sigla |
| `GET /api/equipes[?setorId=&ativo=]` | `200` array de `{id, nome, setorId, setorSigla, responsavel{id,nome}, totalMembros, ativo}` por nome |
| `GET /api/equipes/{id}/membros` | `200` array de `{id, nome, email, perfil, ativo}` por nome (nunca senha/hash) · `404` equipe inexistente |

## Contrato dos endpoints (Issue #7; demandas implementadas na #11)

Os controllers, DTOs de requisição/resposta, validações e erros abaixo já existem e aparecem em `/v3/api-docs`; o Web gera os mocks (MSW) a partir deles. Os detalhes campo a campo estão nos schemas do OpenAPI (`DemandaRequest`, `DemandaDetalhe`, `DocumentoDetalhe`, …).

**Demandas (Flux)** — cadastro, edição, listagem, detalhes, histórico e exclusão lógica estão **Implementados (Issue #11)**; as operações de fluxo da tabela abaixo de triagem e distribuição estão **Implementadas (Issue #12)**; as de execução, fiscalização, conclusão e devolução estão **Implementadas (Issue #13)**. `DemandaRequest` (cadastro e edição): `titulo` (5–200), `descricao`, `origem`, `categoria?`, `municipioId` (código IBGE de MT), `localidade?`, `latitude?`/`longitude?` (retângulo de MT), `dataRecebimento` (≤ hoje), `prioridade?`, `prazoSugerido?`, `observacoes?`. A resposta (`DemandaDetalhe`) traz `numero` (`DEM-AAAA/NNNN`), `status`, `atrasada` (derivada), `equipe`, `fiscal`, `prazo`, `fiscalizacao`, `totalDocumentos`, `areaHa?` e `areaGeometria?` (polígono GeoJSON da área afetada; na demonstração só a massa do seed tem área, **fictícia**) e metadados. Operações de fluxo (`triagem/iniciar`, `triagem`, `distribuicao`, `inicio`, `fiscalizacao`, `conclusao`, `devolucao`) devolvem a demanda já no novo status; `atividades` devolve o evento criado (`HistoricoItem`, `201`).

| Operação | Corpo | Transição |
|---|---|---|
| `POST /api/demandas/{id}/triagem` | `{decisao: CLASSIFICAR\|SOLICITAR_COMPLEMENTACAO, categoria?, prioridade?, justificativa}` — `CLASSIFICAR` exige categoria e prioridade | EM_TRIAGEM → AGUARDANDO_DISTRIBUICAO ou → PENDENTE |
| `POST /api/demandas/{id}/distribuicao` | `{equipeId, fiscalId, prazo (≥ hoje), observacoes?}` | AGUARDANDO_DISTRIBUICAO → DISTRIBUIDA |
| `POST /api/demandas/{id}/atividades` | `{descricao, data?}` | (sem mudança de status; demanda EM_ANDAMENTO) |
| `POST /api/demandas/{id}/fiscalizacao` | `{data, atividades, resultado, evidencias, recomendacoes?, checklist?}` | EM_ANDAMENTO → AGUARDANDO_CONCLUSAO |
| `POST /api/demandas/{id}/conclusao` | `{resultadoFinal, justificativa}` | AGUARDANDO_CONCLUSAO → FINALIZADA |
| `POST /api/demandas/{id}/devolucao` | `{justificativa}` | AGUARDANDO_CONCLUSAO → EM_ANDAMENTO |

Listagem `GET /api/demandas`: filtros `q` (número ou título), `status`, `municipioId`, `categoria`, `prioridade`, `equipeId`, `fiscalId`, `atrasada`, `de`, `ate` + `page`, `size` (máx. 500), `sort`. Cada item (`DemandaResumo`) traz `concluidaEm` (date-time; preenchido só em demandas finalizadas, `null` nas demais) e `atualizadoEm`. `GET /api/equipes/{id}/demandas` aceita `status` + paginação.

**Controle de prazos e produtividade (Implementado — Issue #84)** — todos autenticados; FISCAL só enxerga as demandas suas ou da sua equipe (escopo da listagem); tudo com `demonstrativo: true`. Só demandas com `prazo`; "hoje" no fuso de Mato Grosso.
- `GET /api/flux/prazos/resumo?diasAlerta=5` → `{vencidas, proximas, noPrazo, concluidasNoPrazo, concluidasComAtraso, maisAntigaVencida{numero, diasAtraso}|null, demonstrativo}`. VENCIDA = prazo < hoje e não finalizada; PROXIMA = prazo entre hoje e hoje+`diasAlerta` (0-365, padrão 5); NO_PRAZO = além disso; CONCLUIDA = finalizada (com atraso se `concluidaEm` > prazo).
- `GET /api/flux/prazos?estado=VENCIDA|PROXIMA|NO_PRAZO|CONCLUIDA&diasAlerta=5&page&size` → `PageResponse` de `{id, numero, titulo, municipio, equipe, fiscal, prazo, prioridade, status, estado, diasAtraso, diasRestantes, demonstrativo}` ordenado por prazo.
- `GET /api/flux/produtividade?de&ate&setorId` → `{kpis{demandasNoPeriodo, concluidas, percentualConclusao, tempoMedioConclusaoDias, percentualConcluidasNoPrazo, atrasadasAtivas}, porSetorSituacao[{setorId, setor, total, concluidas, ativas, atrasadas}], tempoMedioPorSetor[], historicoMensal[{mes AAAA-MM, recebidas, concluidas}], porResponsavel[{fiscalId, fiscal, total, concluidas, ativas, atrasadas, tempoMedioDias, percentualConclusao}], demonstrativo}`. Recorte por data de recebimento (`de`/`ate` inclusivos) e setor da equipe designada; setor da demanda = setor da equipe, responsável = fiscal; tempo = dias entre recebimento e conclusão; percentuais 0-100 com 1 casa.

**Documentos (DocIA)** — upload `multipart/form-data` com `arquivo` (pdf, txt, md, csv ou docx; até 10 MB) e `titulo`, `descricao?`, `categoria`, `tipo`, `dataDocumento`, `municipioId?`, `palavrasChave?`, `demandaId?` (o responsável é o usuário autenticado). **Implementado (#15):** `201` com `Location`; extensão fora da lista ou conteúdo incompatível com a extensão (verificado pelos bytes iniciais) → `415`; acima de 10 MB → `413`; CONSULTA → `403`; `municipioId` inexistente → `400`; `demandaId` já vincula o documento (404/403 conforme a demanda). O arquivo vai para `${STORAGE_DIR}/AAAA/MM/<uuid>` (o nome original fica só no banco, saneado); texto extraído: PDF (PDFBox), TXT, MD, CSV (DOCX sem extração). O download usa `Content-Disposition: attachment` e `nosniff`. O upload é auditado (`UPLOAD`/`DOCUMENTO`). Listagem `GET /api/documentos`: `q` (título, descrição, palavras-chave e conteúdo — busca **textual**, não semântica), `categoria`, `tipo`, `municipioId`, `demandaId`, `de`, `ate` + paginação. **Pesquisa (#16):** `q` ignora maiúsculas e acentos (`TRANSLATE` em SQL parametrizado nas colunas curtas e coluna normalizada `TEXTO_BUSCA` no texto extraído, sem limite de tamanho — #164; "fiscalizacao" encontra "Fiscalização"), procura a frase inteira como trecho em título, descrição, palavras-chave e texto extraído; `categoria` e `tipo` são igualdade sem diferenciar maiúsculas; `de` posterior a `ate` → `400`; `sort` aceita `titulo`, `dataDocumento`, `categoria`, `criadoEm` (padrão `criadoEm` decrescente). Detalhes (`DocumentoDetalhe`) trazem `textoExtraido` (**trecho** de até 2000 caracteres), `sha256`, as demandas vinculadas e `historico` (auditoria do documento: upload e vínculos, mais recente primeiro). `GET /api/demandas/{id}/documentos` lista os documentos da demanda (FISCAL só na sua equipe/atribuídas → `403`). Vincular e desvincular são registrados no histórico da demanda (tipo `DOCUMENTO`) e na auditoria (`VINCULAR`/`DESVINCULAR`); vínculo duplicado → `409`; desvincular vínculo inexistente → `404`. Vínculo: `POST /api/demandas/{id}/documentos` `{documentoId}`; `DELETE …/{documentoId}` remove só o vínculo.

**Assistente DocIA (#27)** — `POST /api/docia/perguntas` `{pergunta}` (até 500 caracteres; qualquer perfil autenticado) → `{resposta, modo, referencias[{documentoId, titulo, trecho, aderenciaTextual, similaridadeSemantica}], agente, modelo, passos[], recuperacao}`. `recuperacao` (#141): `LEXICAL` (só busca textual) ou `HIBRIDA` (textual + vetorial com embeddings locais, fundidas por RRF); nula quando nenhuma busca foi feita (pergunta recusada). `similaridadeSemantica` (cosseno do trecho mais próximo, nula na recuperação lexical) mede proximidade de sentido, **não** confiança. `aderenciaTextual` (alta/média/baixa, nula se o documento não veio da busca) é **relativa** ao documento com mais termos da pergunta — não é probabilidade de a resposta estar correta (ver `docs/AGENTES_IA.md`). Recuperação por busca textual (mesma da biblioteca, sem acento/caixa): termos da pergunta (sem palavras vazias) pontuam os documentos ativos; os 3 melhores contribuem com a frase mais aderente. O provider padrão (`DemoAssistenteProvider`) é **extrativo** e responde com `modo` = "Modo demonstrativo — sem IA generativa". Sem trecho aderente → `resposta` "Não há informação suficiente nos documentos…" e `referencias` vazias. Perguntas sobre legislação, multas, penalidades ou procedimentos oficiais são recusadas (resposta explicativa, sem referências). Provider externo: `DOCIA_PROVIDER=external` + `DOCIA_API_URL` (+ `DOCIA_API_KEY` como Bearer), só por variáveis de ambiente; recebe `{pergunta, contexto[]}` e devolve `{resposta}`; sem configuração completa ou em falha, usa o modo demonstrativo. Respostas do provider externo trazem `modo` = "Provider externo configurado".

**Intelligence e dashboard** — `GET /api/intelligence/*` aceitam os filtros `de`, `ate`, `municipioId`, `bioma`, `categoria`, `status`. `resumo` → `ResumoIndicadores`; `serie-mensal`, `por-municipio`, `por-bioma`, `por-categoria`, `por-situacao` → `{demonstrativo, aviso, itens[]}`; `mapa` → `{demonstrativo, aviso, pontos[]}` (cada ponto com `coordenadaAproximada` quando usa o centroide do município); `GET /api/dashboard` → `{demonstrativo, aviso, resumo, porSituacao, atividadesRecentes, porBioma, porCategoria}`. **Implementado (#17):** números calculados a cada consulta sobre as demandas (excluídas ficam de fora), então cadastrar ou finalizar uma demanda os altera na consulta seguinte. `resumo` inclui `fiscalizacoes`, `percentualConclusao` (%) e `tempoMedioAtendimentoDias` (média simulada recebimento→conclusão das finalizadas; nulo sem finalizadas); `totalDocumentos` = documentos ativos da biblioteca DocIA. `por-situacao`/`por-bioma`/`por-categoria` incluem itens com zero; `serie-mensal` preenche meses sem demandas; `mapa` usa o centroide do município quando a demanda não tem coordenada. `atividadesRecentes` traz os 10 últimos eventos do histórico das demandas (`DemandaHistorico`): desvio da Issue, que cita a AUDITORIA, justificado pelo DTO `AtividadeRecente` já publicado. **Visão institucional:** agregados, mapa e dashboard são abertos a todos os perfis autenticados, sem escopo de FISCAL (diferente de `GET /api/demandas`). `de`/`ate` aplicam-se à **data de recebimento** da demanda. `totalDocumentos` conta todos os documentos ativos (não é afetado pelos filtros). **Aviso ao Web:** campos novos e aditivos em `ResumoIndicadores` (`fiscalizacoes`, `percentualConclusao`, `tempoMedioAtendimentoDias`) e em `DashboardResponse` (`porBioma`, `porCategoria`). Medição: `mapa` respondeu em 10–19 ms com as 7 demandas do seed.

## Exemplo: login

```http
POST /api/auth/login
Content-Type: application/json

{ "email": "admin@admin.com", "senha": "<senha demonstrativa — ver docs/DEMO.md>" }
```
```json
{ "token": "<jwt>", "expiresIn": 28800,
  "usuario": { "id": 1, "nome": "Administrador (demonstração)", "email": "admin@admin.com", "perfil": "ADMIN" } }
```
> `admin@admin.com` é uma credencial **exclusivamente demonstrativa**, existente apenas nos profiles `demo`/`dev` (senha em [DEMO.md](DEMO.md)).

### Autenticação (Implementado — Issue #4; revogação e limite na #134)

| Endpoint | Resposta |
|---|---|
| `POST /api/auth/login` `{email, senha}` | `200` `{token, expiresIn, usuario}` · `400` campos inválidos · `401` credenciais inválidas (mensagem genérica, não revela se o e-mail existe) · `403` usuário inativo |
| `POST /api/auth/logout` | `204`; **revoga o token na hora** (o `jti` vai para `TOKEN_REVOGADO`; qualquer requisição seguinte com o mesmo token, inclusive novo logout, recebe `401`) e registra `LOGOUT` na auditoria. O cliente também descarta o token |
| `GET /api/auth/me` | `200` `{id, nome, email, perfil}` |
| `GET /api/auth/config` (público) | `200` `{demo: true\|false, perfisDemo?: [{perfil, nome, descricao}]}` — `demo=true` quando o profile `demo` está ativo (aviso **AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS** na tela de login); `perfisDemo` (cartões do Modo demonstração) só aparece com `demo=true` |
| `POST /api/auth/demo/{perfil}` (público; **somente profile `demo`**, 404 nos demais) | `200` `LoginResponse` do primeiro usuário ativo e demonstrativo (`DEMO=1`) do perfil (ADMIN, GESTOR, FISCAL, ANALISTA, CONSULTA) · `400` perfil inválido · `404` perfil sem usuário ativo. Sem senha; audita `LOGIN` com detalhe "modo demonstração" |

- **Token:** JWT HS256, validade de 8 h (`expiresIn` = 28800 s), enviado em `Authorization: Bearer <jwt>`. Carrega só `sub` (ID) e `perfil`; a cada requisição o usuário é relido do banco, então **desativar um usuário invalida seus tokens imediatamente**.
- **Segredo:** variável `JWT_SECRET` (mínimo 32 caracteres), **obrigatória fora dos profiles `dev` e `openapi`** (a API não sobe sem ela). Em `dev`/`openapi`, se ausente, usa-se um segredo aleatório efêmero.
- **401 / 403:** sem token, token inválido/expirado ou usuário inativo → `401`; perfil sem permissão (`@PreAuthorize`) → `403`; ambos em `application/problem+json`.
- **CORS:** habilitado somente no profile `dev`, para `http://localhost:5173`. No Docker o Web usa proxy (sem CORS).
- **Auditoria:** `LOGIN` e `LOGOUT` gravam em `AUDITORIA` (sem senha nem token).

### Limite de requisições (Implementado — Issue #134)

Janela fixa em memória (uma instância da API), configurável por variáveis de ambiente (`.env.example`):

| Categoria | Endpoints (só `POST`) | Conta por | Padrão |
|---|---|---|---|
| `LOGIN` | `/api/auth/login`, `/api/auth/demo/{perfil}` | IP do cliente | `LIMITE_LOGIN_POR_JANELA=20` |
| `IA` | `/api/demandas/{id}/triagem/sugestao` (e `/stream`), `/api/demandas/{id}/distribuicao/sugestao`, `/api/docia/perguntas` (e `/stream`), `/api/docia/comparacao/explicar`, `/api/demandas/{id}/minuta-vistoria` | usuário autenticado | `LIMITE_IA_POR_JANELA=10` |
| `UPLOAD` | `/api/documentos` e `/api/documentos/pre-analise` (multipart), `/api/demandas/{id}/fotos` (multipart, #145) | usuário autenticado | `LIMITE_UPLOAD_POR_JANELA=20` |

Janela: `LIMITE_JANELA_SEGUNDOS=60`; `LIMITE_REQUISICOES_ATIVO=false` desliga. Acima do limite: `429` com `Retry-After` (segundos até a próxima janela). O IP vem do último endereço de `X-Forwarded-For` **só** quando a conexão chega de um proxy configurado em `LIMITE_PROXY_CONFIAVEL` (IPs, CIDR ou nomes de host; no compose, `web` — o Nginx do Web, resolvido pela rede interna a cada 30 s) ou do loopback; vazio = só loopback. De qualquer outra origem o cabeçalho é ignorado e vale o IP da conexão. No compose a porta da API é publicada só em `127.0.0.1`. Memória limitada: no máximo `LIMITE_MAX_CHAVES` (10.000) contadores vivos — as janelas vencidas saem em custo amortizado e, acima do teto, a mais antiga é descartada. Os contadores zeram a cada reinício da API. O `429` aparece no OpenAPI dessas operações.

**Trade-offs conhecidos (revisão da #134):**
- *Custo da revogação por requisição:* cada requisição autenticada consulta `TOKEN_REVOGADO` pela PK (além da leitura do usuário que já existia), exceto tokens já conhecidos como revogados (cache local). Custo aceito para a demonstração; com muitas instâncias, um cache compartilhado seria o próximo passo.
- *Cache antes do commit (falha segura):* o `jti` entra no cache local **antes** da gravação no Oracle. Se a gravação falhar, esta instância continua recusando o token até ele expirar, mas outra instância (ou um reinício) o aceitaria — o lado seguro para uma única instância.
- *Logouts simultâneos do mesmo token:* a gravação roda em transação própria (`MERGE`, `REQUIRES_NEW`); uma colisão na PK conta como "já revogado". As duas respostas são `204` (ou `401` para a que chegar depois da revogação), e a auditoria registra **um** `LOGOUT`.

## Checklist por categoria e fotos de evidência com EXIF (Implementado — Issue #145)

Simulação demonstrativa (AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS): não gera auto de infração nem substitui roteiros ou sistemas oficiais.

- **Modelos de checklist:** `GET /api/demandas/checklists` (qualquer perfil) devolve `ChecklistModelo[]` — um por `CategoriaFiscalizacao`, com `versao` (hoje `1`, constante `ChecklistFiscalizacao.VERSAO` no código) e `itens[]` `{id, rotulo, tipo: BOOLEANO|TEXTO|NUMERO, obrigatorio}`.
- **Checklist no registro da fiscalização:** `FiscalizacaoRequest.checklist?` é um objeto `{"<id do item>": valor}`. Validado contra o modelo da **categoria da demanda**: item desconhecido (inclusive de outra categoria), obrigatório ausente/vazio, tipo errado, número negativo/acima de 10⁹ ou texto acima de 500 caracteres → `400` com `errors[].campo = "checklist.<id>"`, sem gravar nada. Opcional: registros sem checklist continuam aceitos. Gravado como JSON `{versao, categoria, itens}` (coluna `FISCALIZACAO.CHECKLIST`, `IS JSON`) e devolvido em `DemandaDetalhe.fiscalizacao.checklist` (`ChecklistPreenchido`).
- **Envio de foto:** `POST /api/demandas/{id}/fotos` (`multipart/form-data`, parte `arquivo`). Perfis FISCAL (só o **fiscal designado**; outro fiscal → `403`), GESTOR e ADMIN; ANALISTA/CONSULTA → `403`. Só com a demanda `EM_ANDAMENTO` (senão `409`); no máximo 20 fotos por demanda (`409`). Tipo reconhecido pelos **magic bytes** em `StorageService.validarImagem` — JPEG (`FF D8 FF`), PNG (`89 50 4E 47 0D 0A 1A 0A`) ou WebP (`RIFF....WEBP`); o EXIF só é lido de JPEG e WebP (PNG fica sem EXIF: o leitor de PNG da biblioteca descompacta chunks sem limite); qualquer outro conteúdo, mesmo com extensão `.jpg`, → `415`. Acima de 10 MB → `413`. O arquivo é gravado **intacto** em `${STORAGE_DIR}/AAAA/MM/<uuid>` (nada é removido do EXIF), com SHA-256; o nome original é saneado e só vai para o banco. Resposta `201` + `Location` com `FotoFiscalizacao`.
- **EXIF:** lido com `metadata-extractor` (Apache 2.0): `exifDataHora` (`DateTimeOriginal`, ou `DateTime`; horário local da câmera) e `exifLatitude`/`exifLongitude`. EXIF ausente/corrompido → campos nulos (nunca erro). O EXIF é declarado pelo próprio arquivo: **informativo, sem valor de prova e nunca usado em autorização**. `distanciaKm`: até a borda do polígono da demanda (`0` se dentro) quando há `areaGeometria`; senão Haversine até `latitude`/`longitude`; nula sem referência. `gpsForaMt` sinaliza GPS fora da caixa envolvente de MT (aceito, não bloqueado). `conferencia`: `CONFERE` (≤ 1 km), `DIVERGENTE`, `FORA_MT`, `SEM_GPS` ou `SEM_REFERENCIA`.
- **Leitura:** `GET /api/demandas/{id}/fotos` (metadados, mais antigas primeiro) e `GET /api/demandas/{id}/fotos/{fotoId}/arquivo` (tipo da imagem, `Content-Disposition: attachment`, `nosniff`) devolve o **arquivo original com todo o EXIF** (inclusive GPS) a quem tem acesso à demanda — qualquer perfil com acesso à demanda (FISCAL fora da equipe/atribuição → `403`; foto de outra demanda → `404`).
- **Exclusão:** `DELETE /api/demandas/{id}/fotos/{fotoId}` → `204`, só por **quem enviou** (`403` para os demais, inclusive GESTOR/ADMIN) e antes da conclusão (`FINALIZADA` → `409`); o arquivo sai do volume após o commit.
- **Rastreabilidade:** envio e exclusão entram no histórico da demanda (tipo `ATIVIDADE`, texto `[Foto] …` com o resultado da conferência) e na auditoria (`UPLOAD_FOTO_FISCALIZACAO` / `EXCLUIR_FOTO_FISCALIZACAO`, com prefixo do SHA-256). O envio conta na categoria `UPLOAD` do limite de requisições (`429` + `Retry-After`).

## Uso pelo frontend

O CI da API publica `openapi.json` em cada execução (job **Exportar contrato OpenAPI**). O Web baixa o artefato e mantém handlers MSW em `src/mocks/` (Issue Web#4), ativados por `VITE_USE_MOCKS=true`.

## Camada de referência de satélite — INPE (Implementado — Issue #34)

Dados **públicos reais** do INPE (focos de calor do Programa Queimadas e alertas DETER do TerraBrasilis) de Mato Grosso, exibidos como camada de referência **separada** dos dados fictícios do Flux; **não** entram em `/api/intelligence/resumo`, `/mapa` nem em nenhum indicador. Somente leitura, qualquer perfil autenticado. Toda resposta traz `"fonte": "INPE — dados públicos"`, `"oficialSema": false`, `atualizadoEm` (maior data de importação) e `aviso`.

| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/intelligence/referencia/metadados` | Importações (fonte, tipo, período, `importadoEm`, `registros`, `urlOrigem`, `licenca`, `arquivo`). |
| GET | `/api/intelligence/referencia/pontos?tipo=FOCO_CALOR\|DESMATAMENTO&de&ate&municipioId&bioma&limite` | Pontos (mais recentes primeiro). `de`/`ate` `AAAA-MM-DD` inclusivos (UTC); `limite` padrão 5000, máximo 20000 (valores maiores são limitados); `bioma` é sensível a caixa (`AMAZONIA`, `CERRADO`, `PANTANAL`; outro valor → 400); `truncado=true` se havia mais pontos. `de` > `ate` → 400. |
| GET | `/api/intelligence/referencia/por-municipio?tipo&de&ate` | Contagem por município (maior primeiro) e `total`, para camada coroplética e cards. |
| GET | `/api/intelligence/referencia/pontos/{id}` | Detalhe para o painel do mapa (`PontoDetalhe`): `ponto`, `areaInformada`, `areaCampoFonte` (`areamunkm`; `areatotalkm` só se faltar), `areaKm2Fonte` (valor exato da fonte), `areaObservacao`, `geometria` (polígono GeoJSON do DETER; `null` para focos), `atributos` (todos os campos originais da fonte) e `importacao` (fonte, período, URL, licença). Qualquer perfil autenticado; inexistente → 404. |

`DESMATAMENTO` = alertas DETER (campo `classe`, ex. `DESMATAMENTO_CR`, `CICATRIZ_DE_QUEIMADA`; `areaKm2` em km² e `areaHa` = km² × 100). Latitude/longitude do DETER são o centroide aproximado do polígono; o polígono vem no detalhe.

**Área afetada:** só existe onde a fonte a informa. O DETER traz polígono e área em km²; a API usa `areamunkm` (área do recorte por município, o mesmo polígono desenhado). No Cerrado, `areatotalkm` é a área do alerta inteiro, repetida em cada recorte, e fica só nos `atributos` (a `V8__area_deter_recorte.sql` corrige bancos que a gravaram). Snapshot sem nenhum registro válido não substitui o anterior. O foco de calor do BDQueimadas é um **ponto** (centro do pixel de detecção) e **não tem área queimada**: `areaKm2`/`areaHa` ficam `null` e `areaObservacao` explica isso. Nenhuma área é calculada ou estimada para dados do INPE.

## Pesquisa ranqueada híbrida (Implementado — Issue #141)

`GET /api/documentos/busca?q=...&limite=10&modo=AUTO` (qualquer perfil autenticado; só consulta). Mesma recuperação do Assistente DocIA: busca textual (termos/radicais, título vale mais) e, quando o modelo local de embeddings (`nomic-embed-text` via Ollama) está disponível e há trechos indexados, busca vetorial no Oracle 23ai (`VECTOR_DISTANCE` cosseno) fundidas por **RRF** (`Σ 1/(k + posição)`, `k = BUSCA_RRF_K`, padrão 60). Documento que só a parte vetorial encontrou entra apenas com similaridade ≥ `EMBEDDINGS_SIMILARIDADE_MINIMA` (abstenção preservada). Resposta `PesquisaDocumentos`: `consulta`, `recuperacao` (`LEXICAL` | `HIBRIDA`) e `resultados[]` (`documento` = `DocumentoResumo`, `trecho`, `aderenciaTextual` alta/média/baixa ou nula quando o documento não tem termo da consulta, `similaridadeSemantica` cosseno ou nula). Nenhuma das duas medidas é confiança. Parâmetros: `q` 2–200 caracteres, `limite` 1–20 (padrão 10), `modo` `AUTO` (padrão) ou `LEXICAL` (força só a textual, para comparação/benchmark); inválidos → `400` problem+json. Sem embeddings (desligados, modelo não baixado, Ollama fora do ar, nada indexado) a resposta é `LEXICAL`, nunca erro. A listagem paginada com filtros continua em `GET /api/documentos`.

## Comparação documental (Implementado — Issue #26)


`POST /api/docia/comparacao/explicar` com `{"aId": 1, "bId": 2}` (#142; mesma autorização da comparação). Reaproveita o diff do `ComparacaoService` e devolve `grandezas[]` (`categoria` AREA/VAZAO/VALOR/DATA/PRAZO/PERCENTUAL/CONCLUSAO, `situacao` ALTERADO/REMOVIDO/ADICIONADO, `valorA`/`valorB`, `variacaoAbsoluta`, `variacaoPercentual`, `unidade`), `grandezasIguais`, `similaridade` e `resumo` curto. A tabela vem sempre de extração determinística (regex), nunca do modelo. `modo`: LOCAL (Ollama) ou CLAUDE (resumo escrito pelo agente `comparacao`, somente leitura, com orçamento, auditoria `AGENTE_EXECUCAO` e erros de ferramenta pelo catálogo `ErroFerramenta`) ou DEMONSTRATIVO (resumo por regras, sem IA; auditoria `AGENTE_DEMONSTRATIVO`). Nada é gravado. 404/400 como na comparação; `aId` igual a `bId` → 400 ("Escolha dois documentos diferentes para comparar.").
`POST /api/documentos/comparacao` com `{"aId": 1, "bId": 2}` (qualquer perfil autenticado). Comparação **determinística** (LCS e Jaccard), **não é IA**: a resposta traz `geradoPorIa=false`, `aviso` e `metodo`. Campos: `a`/`b` (id, título), `metadados[]` (`campo`, `valorA`, `valorB`, `igual`), `similaridade` (0 a 100, 2*LCS/(nA+nB) por palavras), `similaridadeTermos` (Jaccard), `diffLinhas[]` e `diffPalavras[]` (`tipo` = `IGUAL`/`REMOVIDO` só em A/`ADICIONADO` só em B, `texto`), `trechosSemelhantes[]` (sentenças com similaridade >= 50%, até 20), `termosExclusivosA/B` (até 50) e `truncado` (textos limitados a 2000 palavras / 1500 linhas). Comparação ignora caixa, acentos e pontuação nas pontas. Documento inexistente/excluído → 404; documento sem texto extraído → 400 (`problem+json`, `erros[].campo` = `aId`/`bId`, mensagem com o título).

## Notificações (Implementado — Issue #25)
Migration `V6__notificacoes.sql` (`NOTIFICACAO`). Qualquer perfil autenticado; cada usuário só vê e altera as próprias (outra notificação → 404).
- `GET /api/notificacoes?lida=&page=&size=` — paginado (`PageResponse`), mais recentes primeiro.
- `GET /api/notificacoes/nao-lidas/contagem` — `{ "naoLidas": n }`.
- `PATCH /api/notificacoes/{id}/lida` — marca como lida (idempotente).

Geração: nova demanda (GESTOR e ADMIN, exceto o autor), distribuição (fiscal designado), documento vinculado a demanda (fiscal da demanda, se não for quem vinculou), prazo em até 3 dias e atraso (fiscal; atraso também aos gestores). A verificação de prazos roda por `@Scheduled` diariamente às 07:00 (America/Cuiaba; `sema.notificacoes.cron`); a chave `PRAZO:<demanda>:<data>` / `ATRASO:<demanda>:<data>` impede duplicar no mesmo dia.

## Gestão de usuários, setores e equipes (Implementado — Issue #24)

Consulta: **Admin e Gestor**. Cadastro, edição, ativação e membros: **somente Admin** (demais perfis `403`). Toda operação é registrada na auditoria (`USUARIO`, `SETOR`, `EQUIPE`; a senha nunca entra no detalhe). Respostas nunca trazem senha ou hash.

| Endpoint | Regras |
|---|---|
| `GET /api/usuarios[?q=&perfil=&ativo=&page=&size=]` | `200` paginado, ordenado por nome; `q` pesquisa trecho de nome ou e-mail (sem diferenciar maiúsculas) |
| `GET /api/usuarios/{id}` | `200` `{id, nome, email, perfil, setorId, setorSigla, ativo, ultimoAcesso, criadoEm}` · `404` |
| `POST /api/usuarios` | corpo `{nome, email, senha, perfil, setorId?}`; `201` + `Location` · senha com menos de 8 (ou mais de 72) caracteres → `400` (sem exceção para a credencial de teste) · e-mail duplicado → `409` · setor inexistente → `400` |
| `PUT /api/usuarios/{id}` | `{nome, email, senha?, perfil, setorId?}`; `senha` opcional (redefine; mesma regra) · rebaixar o último ADMIN ativo → `409` |
| `PATCH /api/usuarios/{id}/ativacao` | `{ativo}`; desativar o último ADMIN ativo → `409` |
| `POST /api/setores` · `PUT /api/setores/{id}` | `{sigla, nome, ativo?}`; sigla gravada em maiúsculas; duplicada → `409` |
| `POST /api/equipes` · `PUT /api/equipes/{id}` | `{nome, setorId, responsavelId?, ativo?}`; responsável deve ser usuário ativo e passa a ser membro; nome repetido no setor → `409`; `setorId`/`responsavelId` inválidos → `400` |
| `PUT /api/equipes/{id}/membros` | `{usuarioIds[], responsavelId?}` substitui os membros (só usuários ativos, senão `400`); sem `responsavelId` o atual é mantido se continuar membro, ou removido |

Um fiscal cadastrado e associado a uma equipe passa a constar em `GET /api/equipes/{id}/membros` e na distribuição de demandas.

## Relatórios de demandas (Implementado — Issue #28)
`GET /api/relatorios/demandas` (JSON paginado: `{demonstrativo, aviso, content[], page, size, totalElements, totalPages}`; `page` base 0, `size` padrão 20, máximo 100) e `GET /api/relatorios/demandas.csv` (sem paginação). Filtros combináveis iguais aos de `/api/intelligence/*` (que também aceitam `setorId`): `de`/`ate` (data de recebimento, inclusivos; `de` > `ate` → 400), `municipioId`, `bioma`, `categoria`, `status`, `setorId` (setor da equipe responsável; demandas sem equipe ficam fora; Issue #90). Ordenação: mais recentes primeiro. Perfis: ADMIN, GESTOR, ANALISTA e CONSULTA; FISCAL → 403. CSV: UTF-8 com BOM, separador `;`, fim de linha CRLF, linha 1 "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS", linha 2 cabeçalho, datas `dd/MM/AAAA`, `Content-Disposition: attachment; filename="relatorio-demandas-ficticio.csv"`. Células iniciadas por `=`, `+`, `-` ou `@` recebem `'` (proteção contra injeção de fórmula no Excel).

## Agentes de IA (Implementado — Issue #106)

`GET /api/agentes/status` (qualquer perfil autenticado) → `{modo, descricao, agentes[{id, nome, modulo, modelo, descricao}]}`. `modo` = `LOCAL` (modelo aberto no Ollama do próprio servidor, padrão, sem custo), `CLAUDE` (Anthropic, opcional, exige chave) ou `DEMONSTRATIVO` (regras locais, quando não há IA disponível; `modelo` = "Regras locais (sem IA generativa)"). Nunca expõe chaves. Funcionamento, configuração e segurança em `docs/AGENTES_IA.md`.

**Agente de Triagem (Implementado — Issue #107)** — `POST /api/demandas/{id}/triagem/sugestao` (ADMIN, GESTOR, ANALISTA; demanda em PENDENTE ou EM_TRIAGEM, senão 409; 404/403 conforme o escopo) → `{modo, modelo, decisao, categoria, prioridade, nucleo{sigla, nome}, justificativa, duplicatas[{id, numero, titulo, status, dataRecebimento}], evidencias[], avisos[]}`. Não grava nada: para aceitar, `POST /api/demandas/{id}/triagem`. `modo` LOCAL/CLAUDE (IA) ou DEMONSTRATIVO (regras locais, inclusive quando a IA falha — com aviso). Detalhes em `docs/AGENTES_IA.md`.

**Agente de Distribuição (Implementado — Issue #130)** — `POST /api/demandas/{id}/distribuicao/sugestao` (ADMIN, GESTOR; demanda em AGUARDANDO_DISTRIBUICAO, senão 409; 404 conforme o escopo) → `{modo, modelo, equipe{id, nome, setorSigla}, fiscal{id, nome, ativas, atrasadas, proximasDoVencimento}, prazo, justificativa, passos[], avisos[]}` (`equipe`/`fiscal` nulos se não houver equipe ativa com membros). Não grava nada: para aplicar, `POST /api/demandas/{id}/distribuicao`. O servidor valida a sugestão da IA (equipe ativa, fiscal membro ativo dela, prazo ≥ hoje); se inválida ou sem IA, `modo` DEMONSTRATIVO (regras locais, com aviso). Detalhes em `docs/AGENTES_IA.md`.

**Agente Copiloto de Minuta (Implementado — Issue #137)** — `POST /api/demandas/{id}/minuta-vistoria` (ADMIN, GESTOR, ANALISTA e o FISCAL designado para a demanda; outro fiscal ou CONSULTA → 403; 404 inexistente; limite da categoria IA → 429) → `{aviso, modo, modelo, demandaId, numero, nomeArquivoSugerido, markdown, evidencias[], avisos[]}`. `markdown` sempre começa com **MINUTA GERADA PARA REVISÃO HUMANA — AMBIENTE DEMONSTRATIVO** e tem as seções: 1. Identificação, 2. Histórico, 3. Constatações da fiscalização, 4. Documentos considerados, 5. Contexto INPE (dados públicos), 6. Conclusões e recomendações preliminares. Os fatos (identificação, eventos, registro de fiscalização, documentos, contagens do INPE) são montados pela API; o modelo escreve só sínteses e conclusões, e todo número dele precisa constar nos dados das ferramentas consultadas — senão, ou sem IA, a minuta vem do template determinístico (`modo` DEMONSTRATIVO, com aviso). **Nada é gravado**: a Web envia o texto revisado como documento pelo `POST /api/documentos` (vínculo à demanda). Detalhes em `docs/AGENTES_IA.md`.

**Sentinela Territorial e Briefing Executivo (Implementado — Issue #138)** — `GET /api/intelligence/sentinela/briefing` (ADMIN, GESTOR; ANALISTA, FISCAL e CONSULTA → 403; sem token → 401) → `{aviso, geradoEm, validoAte, origem (AGENDADA|SOB_DEMANDA), modo (LOCAL|CLAUDE|DEMONSTRATIVO), modelo, resumo, referenciaInpe{fonte, janelaDias, focosDe, focosAte, deterDe, deterAte, nota}, indicadores{demandasAbertas, porStatus, focosJanela, deterJanela}, totais{anomalias, alta, media, baixa}, anomalias[{id, tipo, severidade, titulo, descricao, numeros, acoes[{tipo, rotulo, parametros}], assunto (município, equipe ou status)}], parametros, evidencias[], avisos[]}`. Anomalias por regras determinísticas (ordem: ALTA, MEDIA, BAIXA):
- `INPE_SEM_DEMANDA` — município com focos + DETER ≥ `SENTINELA_MIN_REGISTROS_INPE` (10) nos últimos `SENTINELA_JANELA_INPE_DIAS` (30) dias **do pacote do INPE** e nenhuma demanda não finalizada. O pacote é estático: o "agora" de cada tipo é o `DATA_HORA` mais recente em `REFERENCIA_SATELITE` (janela `(ate − N dias, ate]`), não a data de hoje. Ações: `FILTRAR_MAPA` `{municipioId, camada: INPE, de, ate}` e `CRIAR_DEMANDA_MONITORAMENTO` `{origem: MONITORAMENTO, municipioId, municipio, categoria (DESMATAMENTO_ILEGAL se DETER > focos, senão QUEIMADA_INCENDIO), latitude, longitude (centro do agrupamento mais denso, célula de 0,1°, calculado sobre no máximo os 5.000 registros mais recentes do município na janela), titulo}`.
- `FILA_ACUMULADA` — por status (PENDENTE, EM_TRIAGEM, AGUARDANDO_DISTRIBUICAO, AGUARDANDO_CONCLUSAO): demandas paradas há ≥ `SENTINELA_FILA_DIAS` (7) dias (desde o último evento do histórico que levou ao status; sem ele, a criação) ≥ `SENTINELA_FILA_MINIMO` (3). Ações: `FILTRAR_DEMANDAS` e `FILTRAR_MAPA` `{status}`.
- `EQUIPE_SOBRECARREGADA` — equipe ativa com ativas/fiscal > `SENTINELA_ATIVAS_POR_FISCAL` (4) ou atrasadas/fiscal > `SENTINELA_ATRASADAS_POR_FISCAL` (1) (os dois: ALTA). Ações: `FILTRAR_DEMANDAS` `{equipeId, atrasada: true}` e `{equipeId}`.

Severidade de INPE e filas: ≥ 3× o mínimo ALTA, ≥ 2× MEDIA, senão BAIXA; até `SENTINELA_MAX_POR_TIPO` (5) por tipo. As ações são **só atalhos**: o cadastro pré-preenchido é concluído por uma pessoa em `POST /api/demandas`. `resumo`: agente `sentinela` (texto sem URLs/HTML/Markdown; todo número precisa existir nos dados calculados e, numa frase que cita o assunto de uma anomalia, números ≥ 10 precisam ser dessa anomalia ou dos indicadores gerais) ou template determinístico (`modo` DEMONSTRATIVO, com aviso quando a IA falhou ou foi descartada).

**Cache (stale-while-revalidate):** o `GET` devolve na hora o último briefing (o da rotina `SENTINELA_CRON`, padrão `0 0 7 * * *` America/Cuiaba, vale até ser substituído); passado `validoAte` (`SENTINELA_CACHE_MINUTOS`, 10), dispara **uma** atualização em segundo plano (com IA) — no máximo uma por janela. Sem briefing ainda (ex.: logo após subir a API), a primeira geração é síncrona e **sem IA** (template, uma só para requisições simultâneas) e o resumo da IA chega na atualização em segundo plano. Falha de geração: o último briefing bom continua servido com aviso ("Não foi possível atualizar…") e nova tentativa só após 60 s; sem briefing algum, `503 problem+json` com `Retry-After`. `SENTINELA_AGENDAMENTO_ATIVO=false` desliga a rotina (o endpoint continua).

**Sem limite de requisições (decisão da #138):** o modelo nunca roda na thread da requisição e roda no máximo uma vez por janela de cache, independentemente de quantos `GET` chegarem (geração única por flag atômica); por isso o endpoint não entra na categoria `IA` (que só conta `POST`) e consultas repetidas não geram custo de GPU. Detalhes em `docs/AGENTES_IA.md`.

**Assistente Global — Ctrl+K (Implementado — Issue #139)** — `POST /api/agentes/assistente-global` (todos os perfis autenticados; sem token → 401; `pergunta` vazia ou com mais de 500 caracteres → 400 `problem+json`; categoria `IA` do limite de requisições → 429) com corpo `{pergunta}` → `{aviso, modo (LOCAL|CLAUDE|DEMONSTRATIVO), modelo, resposta, links[{tipo (DEMANDA|DOCUMENTO|TELA), rotulo, url}], ferramentas[], evidencias[], avisos[]}`. Ferramentas **somente leitura**, com parâmetros explícitos, que delegam aos Services existentes com o usuário logado: `resumo_indicadores_ambientais(municipio?, de?, ate?)` → `IndicadorService.resumo` (agregados do `GET /api/intelligence/resumo`, aberto a todos os perfis; não lista demandas); `prazos_criticos(estado = VENCIDA|PROXIMA|TODOS, dias_alerta? 1–30)` → `PrazoService` (escopo do FISCAL); `buscar_demandas(texto?, status?, municipio?)` → `DemandaService.listar` (até 5; sem parâmetro de equipe/fiscal; escopo do FISCAL aplicado pelo Service); `buscar_documentos(consulta)` → `BuscaDocumentos.pesquisar` (biblioteca, lexical/híbrida); `resumo_alertas_inpe(municipio?, de?, ate?)` → `ReferenciaSateliteService.porMunicipio` (INPE, dados públicos). `municipio` aceita nome (sem acento/caixa) ou código IBGE. **Links** montados pela API a partir dos registros devolvidos pelas ferramentas (`/flux/demandas/{id}`, `/docia/documentos/{id}`, `/flux/prazos`, `/flux/demandas?q=&status=&municipioId=`, `/docia/documentos?q=`, `/intelligence?municipioId=&de=&ate=`; no máximo 8) — nunca ids ou URLs escritos pelo modelo. `resposta` da IA sem URLs/HTML/Markdown e com **todo número** presente nos dados das ferramentas (ou na data de hoje), sem contar os parâmetros que o modelo digitou (`consulta`, texto do filtro, período); resposta sem nenhuma chamada de ferramenta cai no modo demonstrativo; se não conferir, a resposta é o resumo determinístico das mesmas consultas (`modo` DEMONSTRATIVO, aviso). Sem IA ou em falha: modo demonstrativo identificado ("Modo demonstrativo (regras locais, sem IA generativa).") com roteamento por palavras-chave (prazos, INPE, documentos, indicadores, demandas/status/município; até 2 consultas). **Nada é gravado** (só a auditoria do agente). Detalhes em `docs/AGENTES_IA.md`.

**Assistente DocIA agêntico (Implementado — Issue #108)** — `POST /api/docia/perguntas` (mesmo contrato, campos novos compatíveis): com IA disponível, o Agente DocIA busca e lê os documentos e responde com citações conferidas; a resposta traz também `agente` (LOCAL, CLAUDE, DEMONSTRATIVO ou EXTERNO), `modelo` (nulo sem IA) e `passos` (o que o agente fez). Sem IA ou em falha, o comportamento extrativo anterior (#27). Detalhes em `docs/AGENTES_IA.md`.

**Assistente DocIA contextualizado e multi-turno (Implementado — Issue #133)** — `POST /api/docia/perguntas` aceita dois campos **opcionais** (sem eles, o contrato anterior): `demandaId` (restringe a busca e a leitura aos documentos vinculados à demanda; o usuário precisa ter acesso à demanda pela mesma regra do detalhe da demanda — FISCAL só da sua equipe/atribuídas: **403** fora do escopo, **404** inexistente, `problem+json`; demanda sem documentos vinculados responde "Não há informação suficiente…") e `historico` (`[{pergunta, resposta}]`, até 10 itens aceitos; a API mantém só os **4 mais recentes**, com no máximo 300 caracteres por pergunta, 500 por resposta e 2.000 no total, sem caracteres de controle nem marcadores de delimitação/papel). O histórico entra no prompt só como **dados** num bloco `<<<HISTORICO_DADOS … HISTORICO_DADOS>>>` e nunca como instrução; as citações continuam conferidas apenas nos documentos efetivamente lidos. O modo extrativo (sem IA) respeita `demandaId` e ignora `historico`.

**Streaming SSE dos agentes (Implementado — Issue #135)** — `POST /api/demandas/{id}/triagem/sugestao/stream` e `POST /api/docia/perguntas/stream`: mesmos corpos, perfis, escopo, auditoria e categoria `IA` do limite de requisições dos endpoints JSON (que seguem intactos). Erros de validação/escopo (400/401/403/404/409/429) saem como `problem+json` **antes** de abrir o stream; depois, `200 text/event-stream` com cabeçalhos `X-Accel-Buffering: no` e `Cache-Control: no-cache, no-transform`. JWT **só** no cabeçalho `Authorization` (nunca na query string — por isso o cliente usa `fetch` com `ReadableStream`, não `EventSource`). Eventos (`event:` + `data:` JSON):

| Evento | `data` | Quando |
|---|---|---|
| `inicio` | `{agente}` (`triagem` ou `docia`) | sempre, primeiro |
| `ferramenta_iniciada` | `{ferramenta, ordem}` | antes de cada ferramenta pedida pelo modelo |
| `ferramenta_concluida` | `{ferramenta, ordem, resumo, erro}` | depois de cada ferramenta; em erro, `resumo` = código do catálogo `ErroFerramenta` + mensagem pública + `ref. ERR-…` (detalhe só no log) |
| `fallback_demonstrativo` | `{motivo}` | IA indisponível ou sem resposta a tempo (orçamento, fila da GPU, erro do provedor); motivo público fixo, sem a mensagem da exceção |
| `delta_texto` | `{texto}` | texto final exibível (justificativa da triagem / resposta do DocIA) num **único** evento — os clientes Ollama/Anthropic ainda não transmitem tokens |
| `concluido` | mesmo JSON de `SugestaoTriagem` / `RespostaAssistente` do endpoint síncrono | fim normal |
| `erro` | `{mensagem, referencia}` | falha inesperada depois de aberto o stream (sem detalhe interno) |

Comentários `:keepalive` a cada 15 s (`AGENTES_SSE_BATIMENTO_SEGUNDOS`). Timeout do stream = orçamento + timeout por chamada + 30 s. Se o cliente desconectar, a execução para na próxima etapa (antes da próxima chamada ao modelo ou ferramenta) e desiste da espera pela fila da GPU. Até `AGENTES_SSE_MAX_SIMULTANEOS` (16) streams simultâneos; além disso, 503 `problem+json`.

## Painel do sistema (Implementado — Issue #114)

`GET /api/sistema/painel` (**somente ADMIN**; demais perfis 403, sem token 401) → `{geradoEm, sistema{versao, perfilAtivo, java, iniciadoEm, segundosNoAr, memoriaUsadaMb, memoriaMaximaMb}, verificacoes[{id, nome, estado, latenciaMs, resumo, detalhes}], agentes[{id, nome, modulo, modo, modelo, ferramentas[], metricas}]}`. Verificações ao vivo: `api`, `banco` (Oracle: versão e contagens), `ia` (provedor, modo, versão do Ollama, modelos instalados e carregados com % na GPU; Claude só "chave configurada" sim/não), `inpe` (pacotes de dados públicos importados: tipo, fonte, período, registros, licença, origem) e `armazenamento` (arquivos, espaço usado/livre). `estado`: OK, ATENCAO ou INDISPONIVEL; a falha de um componente não derruba o painel (resumo só com o tipo do erro). Métricas dos agentes em memória desde que a API subiu (`MetricasAgentes`): execuções, sucessos, falhas, usos do modo demonstrativo, tempo médio e último. Nunca expõe chaves ou senhas.

**Agente de Triagem — evidência espacial (Implementado — Issue #132)** — a resposta de `POST /api/demandas/{id}/triagem/sugestao` ganhou, sem alterar os campos existentes: `espacial {referencia: COORDENADA|AREA|MUNICIPIO, latitude?, longitude?, raioKm, inpe{focosNoRaio, focoMaisProximoKm?, alertasDeterNoRaio, dentroDePoligonoDeter, menorDistanciaDeterKm?, focosMunicipio, alertasDeterMunicipio}, demandasProximas[{id, numero, titulo, status, categoria?, municipio, distanciaKm?}], documentos[{id, titulo, motivo}]}` e `duplicatas[].distanciaKm?`; `inpe.periodoDias` (janela de dias anteriores ao recebimento) e `inpe.truncado` (limite de leitura atingido). Sem coordenada nem área, `referencia=MUNICIPIO` (contagens do município, `distanciaKm` nulo). Nada é gravado.

**Action Cards — `acoesSugeridas[]` (Implementado — Issue #136)** — `RespostaAssistente` (`POST /api/docia/perguntas`, e o evento `concluido` do stream) e `SugestaoTriagem` (`/triagem/sugestao`, e o `concluido` do stream) ganharam `acoesSugeridas: [{tipo, rotulo, parametros}]` (sempre presente; vazio quando não há). `tipo` (enum `TipoAcaoSugerida`): `ABRIR_COMPARACAO {aId, bId}`, `SUGERIR_VINCULO_DEMANDA {documentoId, demandaId}` (texto do documento cita uma demanda acessível e não vinculada), `VER_NO_MAPA {lat, lon, zoom, demandaId}`, `ABRIR_DEMANDA {demandaId}`. As ações são derivadas no servidor do que o agente citou/encontrou (nunca de ids escritos pelo modelo) e cada parâmetro é revalidado (existe e o usuário acessa; FISCAL só vê demandas do seu escopo; CONSULTA não recebe `SUGERIR_VINCULO_DEMANDA`). Nada é gravado: a pessoa confirma na tela e o cliente usa os endpoints normais (`POST /api/documentos/comparacao`, `POST /api/demandas/{id}/documentos`).

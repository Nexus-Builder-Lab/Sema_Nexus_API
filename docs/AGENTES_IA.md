# SEMA ALERTA — Agentes de IA no sistema

> **Status:** infraestrutura (Issue #106), Agente de Triagem (Issue #107) e Assistente DocIA agêntico (Issue #108) implementados.
> Aprovado pelo proprietário em 13/11/2026. **AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.**

Diferente dos agentes de **desenvolvimento** (`.claude/agents/`, usados pelo Claude Code para escrever o código — ver `docs/AGENTES.md`), estes agentes **rodam junto com o sistema**, dentro da API, e ajudam os usuários nos módulos.

## Como funciona

```
Navegador (React)              API Spring Boot (pacote agentes)                     Provedor do modelo
Card "Sugestão de IA"  ──►  endpoint do módulo (ex.: /triagem/sugestao)
[Aplicar] [Descartar]          └─ ExecutorAgente: laço de ferramentas        ◄──►  Ollama local (padrão, sem custo)
                                    ├─ ferramenta → Service existente              ou Claude (opcional, com chave)
                                    │   (mesmas permissões do usuário)
                                    ├─ resposta final em JSON com esquema
                                    └─ auditoria: provedor, modelo, ferramentas, tokens
```

1. O usuário pede uma sugestão numa tela.
2. O `ExecutorAgente` envia a tarefa ao modelo com as instruções do agente e as ferramentas declaradas.
3. O modelo pede ferramentas (ex.: "buscar demandas semelhantes"); a API executa o Service correspondente **na thread da requisição**, portanto com o perfil e o escopo do usuário logado, e devolve o resultado.
4. O laço repete até a resposta final em JSON, até `AGENTES_MAX_ITERACOES` (padrão 6) ou até estourar o **orçamento total** da execução (`AGENTES_ORCAMENTO_SEGUNDOS`, padrão 150 s, e `AGENTES_MAX_CHAMADAS_FERRAMENTA`, padrão 8). Estourado o limite, a execução vira `AGENTE_FALHA` e a tela usa o modo demonstrativo identificado.
   Cada chamada ao modelo passa pela **fila da GPU** (Issue #134, `FilaGpu`): `Semaphore` justo (ordem de chegada) com `AGENTES_GPU_CONCORRENCIA` vagas (padrão 1). Quem espera mais que `AGENTES_GPU_ESPERA_SEGUNDOS` (padrão 20 s, nunca além do orçamento restante) desiste com `AGENTE_FALHA` ("GPU ocupada") e a tela usa o modo demonstrativo identificado — os pedidos não se empilham na GPU. Os endpoints de IA também têm limite por usuário (`LIMITE_IA_POR_JANELA`, `429` com `Retry-After`; ver `docs/API.md`).
5. A tela mostra a sugestão, o modelo e as ferramentas usadas. **Nada é gravado pelo agente**: aplicar a sugestão usa o endpoint normal do módulo, com confirmação humana.

## Provedores

| Provedor (`AGENTES_PROVEDOR`) | Modelo | Custo | Dados saem da máquina? |
|---|---|---|---|
| `ollama` (**padrão**) | `qwen3:4b-instruct` (Qwen3-4B-Instruct-2507, aberto, licença Apache 2.0, ~2,5 GB, sem modo de raciocínio) no serviço `ollama` do compose, com GPU NVIDIA | **zero** | **não** (`OLLAMA_NO_CLOUD=1`, sem porta no host) |
| `anthropic` (opcional, futuro) | Claude Haiku 5.5 (triagem) e Sonnet 5.5 (DocIA), SDK oficial `anthropic-java` | pago por uso (chave com créditos) | sim (API da Anthropic) |
| `demo` | nenhum: regras locais | zero | não |

Em qualquer falha (Ollama parado, modelo não baixado, erro, resposta inválida) os agentes caem no **modo demonstrativo** (regras locais), sempre identificado. Escolha do proprietário em 13/11/2026: modelo local gratuito, para não ter custo real (a chave da Anthropic exige cartão).

## Agentes

| Agente | Módulo | Ferramentas | Issue |
|---|---|---|---|
| Agente de Triagem | SEMA Flux | `obter_demanda`, `buscar_demandas_semelhantes`, `alertas_inpe_municipio`, `alertas_inpe_raio_coordenada`, `demandas_proximas_coordenada`, `documentos_mesma_localidade` | #107, #132 (implementados) |
| Assistente DocIA | SEMA DocIA | `buscar_documentos`, `ler_documento` | #108 (implementado) |
| Agente de Distribuição | SEMA Flux | `carga_trabalho_fiscais`, `demandas_ativas_na_regiao`, `tempo_medio_categoria` | #130 (implementado) |
| Agente Copiloto de Minuta | SEMA Flux | `obter_dossie_demanda`, `ler_registro_fiscalizacao`, `resumo_documentos_vinculados`, `alertas_inpe_area` | #137 (implementado) |
| Sentinela Territorial | SEMA Intelligence | `anomalias_sentinela`, `indicadores_operacionais` | #138 (implementado) |

## Preparar a IA local (uma vez)

> Rede com inspeção TLS (proxy/antivírus): se `ollama pull` falhar dentro do container com `x509: certificate signed by unknown authority`, baixe o modelo **no host** (onde a cadeia do proxy é confiável e a verificação TLS continua ligada) pelo registro `https://registry.ollama.ai/v2/library/<modelo>/manifests/latest` e `/blobs/<digest>`, confira o SHA-256 de cada blob com o digest do manifesto e copie manifesto e blobs para `/root/.ollama/models/{manifests/registry.ollama.ai/library/<modelo>/latest,blobs/sha256-<hash>}` com `docker cp`. Foi assim que o `nomic-embed-text` foi instalado em 24/11/2026. Nunca desligue a verificação TLS. Os scripts `ia-preparar` não fazem isso automaticamente.

1. No `.env`: `COMPOSE_PROFILES=ia` (GPU NVIDIA; sem GPU use `ia-cpu`, mais lento) e `AGENTES_PROVEDOR=ollama`.
2. `.\scripts\ia-preparar.ps1` (ou `./scripts/ia-preparar.sh`): sobe o serviço `ollama` e baixa `OLLAMA_MODELO` para o volume `ollama` do projeto.
3. `GET /api/agentes/status` deve indicar `LOCAL`. A API **aquece o modelo ao iniciar** (em segundo plano, ~1 min: carrega na GPU e faz uma geração curta — Issue #124) e ele fica carregado por **4 h** após o último uso (`OLLAMA_MANTER_CARREGADO`), então a primeira pergunta não espera o carregamento. Estado do aquecimento no Painel do sistema.
4. Depois de baixado, **funciona sem internet** (bom para a apresentação).

**Rede com inspeção HTTPS (ex.: firewall da SEMA):** o download do modelo pode falhar com `x509: certificate signed by unknown authority`. Não desligue a verificação TLS. Opções: baixar em outra rede, ou (feito em 13/11/2026, com autorização do proprietário) adicionar temporariamente o certificado **público** da CA do firewall — já confiado pelo Windows — só no container do Ollama, baixar o modelo e recriar o container (`docker compose up -d --force-recreate ollama`) para remover a confiança.

## Agente de Triagem (Issue #107)

`POST /api/demandas/{id}/triagem/sugestao` (ADMIN, GESTOR, ANALISTA; demanda Pendente ou Em triagem, senão 409). Não grava nada.

1. Uma rodada de ferramentas (o modelo costuma chamar as três juntas): dados da demanda; até 8 demandas do mesmo município (escopo do usuário); focos de calor e alertas DETER do INPE no município.
2. Resposta final já no esquema JSON (`maxRodadasFerramentas = 1`): decisão, categoria, prioridade, justificativa, duplicatas.
3. A API valida: enums inválidos ou ausentes são completados pelas regras locais (com aviso); duplicatas só se vieram da ferramenta e não estão finalizadas; justificativa limitada a 3 frases completas; o **núcleo regional** vem da região do município (determinístico).
4. Falha da IA → regras locais (`RegrasTriagem`: palavras-chave → categoria; gravidade e INPE → prioridade), com aviso.

Medido em 13/11/2026 (GTX 1660 SUPER, `qwen3:4b-instruct`): **7–11 s** por sugestão (10 de 10 demandas pendentes com IA, sem cair nas regras). Em 16/11/2026 (Issue #120), com justificativa de até 220 caracteres e limite de 400 tokens: **média 7,2 s** (antes 9,4 s), mesmas categorias, prioridades e núcleos nas 5 demandas medidas.

Lições com modelo pequeno: ferramentas **sem parâmetros** confundem o modelo (ele fica raciocinando sobre como passar o número) — toda ferramenta tem um parâmetro, e o servidor usa sempre a demanda em triagem; o `qwen3:4b` (com raciocínio) gastava ~1.000 tokens por etapa (15–20 s), por isso o padrão é a versão *instruct*; sem limite de tamanho no esquema o modelo pode se repetir até o limite de tokens.

## Agente de Distribuição (Issue #130)

`POST /api/demandas/{id}/distribuicao/sugestao` (ADMIN, GESTOR — os mesmos da distribuição; demanda em Aguardando distribuição, senão 409 — o fluxo não tem redistribuição). Não grava nada: a pessoa aplica pelo `POST /api/demandas/{id}/distribuicao`.

1. Ferramentas somente leitura, todas com parâmetro explícito: `carga_trabalho_fiscais(nucleo)` — equipes ativas do núcleo regional (setor `NR-*` da região do município; `TODOS` ou núcleo sem equipe = todas as ativas) com ids dos fiscais e demandas ativas, atrasadas e com prazo nos próximos 7 dias por equipe e por fiscal; `demandas_ativas_na_regiao(abrangencia = MUNICIPIO|REGIAO)` — vistorias Distribuída/Em andamento no município ou na região, com a equipe (logística); `tempo_medio_categoria(categoria)` — média histórica (recebimento → conclusão) das finalizadas e um `prazoReferencia`. Parâmetro fora do enum → `PARAMETRO_INVALIDO` do catálogo `ErroFerramenta`.
2. A tarefa enviada ao modelo leva só campos estruturados (número, categoria, prioridade, município, núcleo, data de hoje) — título e relato não entram (menos superfície de *prompt injection*).
3. Resposta no esquema `{equipeId, fiscalId, prazo, justificativa}`. **O servidor valida** contra as mesmas candidatas das regras: equipe ativa, com membro ativo e do núcleo da demanda (ou de qualquer núcleo quando o da demanda não tem equipe utilizável), fiscal elegível dela (perfil FISCAL quando a equipe tem algum), prazo é data válida entre hoje e hoje + 180 dias. Quando o núcleo não tem equipe utilizável e a busca é ampliada, a ferramenta, os passos e a justificativa dizem isso explicitamente. O GESTOR não é restrito ao próprio setor (o endpoint de distribuição também não restringe). Consultas em JPQL com projeções/agregações (sem carregar entidades nem N+1). Qualquer falha — inclusive erro inesperado ao processar a resposta — → a sugestão inteira vem das **regras locais** (`RegrasDistribuicao`), com `modo` DEMONSTRATIVO, aviso com o motivo e auditoria `AGENTE_DEMONSTRATIVO` (a execução da IA fica em `AGENTE_EXECUCAO`).
4. Regras locais (também sem IA): fiscal com menor pontuação `ativas + 2×próximas do vencimento + 3×atrasadas`, com 1 ponto de desconto se a equipe já tem vistoria em campo no mesmo município; empate por carga da equipe e nome. Prefere membros com perfil FISCAL (senão, qualquer membro ativo — mesma regra do endpoint de distribuição). Prazo = hoje + tempo médio da categoria arredondado para cima (15 dias sem histórico), no máximo 7 dias se URGENTE e 15 se ALTA, entre 3 e 90 dias.
5. Mesmas proteções dos demais agentes: `ExecutorAgente` (fila da GPU, orçamento, catálogo de erros), categoria `IA` do limite de requisições, métricas no Painel do sistema e em `GET /api/agentes/status`. Usa o modelo configurado para a triagem (`AGENTE_TRIAGEM_MODELO` no Claude; `OLLAMA_MODELO` no local).

## Assistente DocIA agêntico (Issue #108)

Mesmo endpoint `POST /api/docia/perguntas`. Com IA disponível, o agente decide o que buscar (`buscar_documentos`) e se precisa ler outro documento (`ler_documento`, até 4.000 caracteres), em até 3 rodadas de ferramentas, e responde **em texto livre** (ou no esquema curto `{resposta, suficiente}`); sem base nos documentos responde `NAO_ENCONTRADO`.

**Contexto da demanda e histórico (Implementado — Issue #133).** `demandaId` (opcional) é autorizado antes de qualquer busca (`DemandaService.detalhar`: 404/403) e limita `buscar_documentos` e `ler_documento` aos documentos de `DOCUMENTO_DEMANDA`; ler um id fora do conjunto devolve erro de ferramenta. `historico` (opcional) é sanitizado por `HistoricoConversa` (4 turnos mais recentes; 300/500 caracteres por pergunta/resposta; 2.000 no total; remove controles, bidirecionais, `<<<`/`>>>`, `HISTORICO_DADOS`, tags `<system>` e prefixos `SYSTEM:`) e enviado no bloco delimitado `<<<HISTORICO_DADOS … HISTORICO_DADOS>>>`, antes de "Pergunta:". As instruções do agente mandam tratá-lo como contexto, nunca obedecer ordens ali e confirmar fatos nos documentos lidos. Defesa em profundidade: mesmo que o modelo seja induzido a citar outro documento, as referências só aceitam documentos devolvidos pelas ferramentas.

`buscar_documentos` (desde a Issue #120): busca textual com **radical** das palavras longas ("desmatada" encontra "desmatamento") e +2 pontos quando o termo está no título; documentos com menos de 1/3 da pontuação do melhor ficam de fora; devolve **o texto do mais relevante** (e de um 2º igualmente relevante, ex.: outra versão) e a lista dos demais com relevância alta/média/baixa. As linhas do selo "AMBIENTE DEMONSTRATIVO" saem do texto entregue ao modelo.

A API **monta as citações**: documentos lidos (até 2), título vindo do banco e o trecho do documento mais aderente à pergunta e à resposta (frases quebradas entre linhas são reunidas; a linha do título não vale como trecho). Se o modelo citar, só valem documentos que as ferramentas devolveram e trechos que existem no texto. Frases de aviso sem dado ("Este documento é fictício…") saem da resposta — a tela já mostra o selo. `suficiente=false` ou `NAO_ENCONTRADO` → "não há informação suficiente". A recusa de perguntas sobre leis, multas e procedimentos oficiais continua antes do agente. Falha → modo extrativo (identificado).

Resposta ganhou `agente` (LOCAL/CLAUDE/DEMONSTRATIVO/EXTERNO), `modelo` e `passos` (compatível).

Medido em 13/11/2026 (`qwen3:4b-instruct`, GTX 1660 SUPER): 3–12 s por pergunta.

## Busca híbrida Oracle 23ai VECTOR (Issue #141)

A recuperação de `buscar_documentos`, do modo extrativo e de `GET /api/documentos/busca` passou a ser **híbrida** quando há embeddings: o ranking textual (radicais, título vale mais, corte de 1/3) e o ranking vetorial (cosseno entre a pergunta e o trecho mais próximo de cada documento, `VECTOR_DISTANCE` no Oracle 23ai, tabela `DOCUMENTO_TRECHO`) são fundidos por **RRF** (`Σ 1/(k + posição)`, `BUSCA_RRF_K` = 60). Usa só as posições, porque as pontuações têm escalas diferentes. Documento encontrado **só** pela parte vetorial precisa de similaridade ≥ `EMBEDDINGS_SIMILARIDADE_MINIMA` (0,65, calibrado com o modelo real), senão toda pergunta "acharia" o vizinho mais próximo e a abstenção se perderia.

- **Modelo:** `nomic-embed-text` (Apache 2.0, ~0,3 GB, 768 dimensões) no mesmo Ollama dos agentes, baixado por `scripts/ia-preparar.*`; prefixos `search_query:`/`search_document:`. Não usa a fila da GPU (chamadas de milissegundos). `EMBEDDINGS_MODELO` aceita outro modelo de 768 dimensões; outra dimensão desliga a parte vetorial com aviso no log.
- **Indexação:** no upload (após o commit, thread própria) e preenchimento idempotente ao iniciar (15 s) e a cada `EMBEDDINGS_REINDEXAR_SEGUNDOS`; falha = tenta no próximo ciclo. Nunca bloqueia a subida nem o upload.
- **Degradação:** `EMBEDDINGS_ATIVO=false`, modelo não baixado, Ollama fora do ar (pausa de 30 s após falha, timeout de 4 s na consulta), nada indexado ou erro de SQL → busca só lexical, **sem erro**.
- **Transparência:** `recuperacao` (`LEXICAL` | `HIBRIDA`) na resposta do Assistente e da pesquisa; `similaridadeSemantica` em cada referência/resultado (cosseno, proximidade de sentido — **não** é confiança). `aderenciaTextual` mantém o significado (relativa ao melhor documento da busca textual) e fica nula quando o documento não tem nenhum termo da consulta.
- **Instruções e ferramentas do agente não mudaram** (mesmo texto para o modelo); muda só quais documentos a busca devolve.

### Benchmark textual × híbrida

Conjunto fixo: `src/test/resources/benchmark/perguntas-busca.json` — 21 perguntas sobre o acervo fictício do seed (14 respondíveis, incluindo 4 paráfrases sem as palavras do texto, e 7 fora do acervo — 3 delas ambientais, como "Qual a área desmatada em Cáceres?" (o caso Cáceres × Colniza), em que o certo é se abster). Métricas: precisão@3 (fração do que voltou no top-3 que é relevante), acerto@1, recall@3, abstenção correta (fora do acervo → nada) e abstenção indevida (respondível → nada).

**Offline (teste `BenchmarkBuscaTest`, `mvn test`, 24/11/2026)** — código de produção da busca com o SQL emulado em memória e embeddings **falsos determinísticos** (trigramas + uma tabela pequena de sinônimos de teste, limiar 0,30). Prova a mecânica (fusão, limiar, abstenção, degradação), **não** a qualidade do nomic-embed-text:

| Recuperação | Precisão@3 | Acerto@1 | Recall@3 | Abstenção correta | Abstenção indevida |
|---|---|---|---|---|---|
| LEXICAL | 0,80 | 0,93 | 0,96 | 0,71 | 0,00 |
| HIBRIDA (embeddings falsos) | 0,71 | 1,00 | 0,92 | 0,71 | 0,00 |

Leitura honesta: a híbrida pôs o documento certo em 1º na paráfrase "Derrubada da floresta detectada por satélite" (a textual punha o Parecer de Cáceres, por causa de "satélite"), mas trouxe mais documentos ao top-3 (ex.: a Instrução Normativa em "Incêndio no pantanal durante a seca"), o que baixou a precisão@3. A abstenção não mudou: "Resultado das eleições municipais" continua com falso positivo **textual** ("resultado" e "município" existem nos documentos) — a parte vetorial não cria nem remove esse erro. O teste também garante que, sem embeddings, a busca "AUTO" devolve exatamente o resultado lexical.

**Calibração com o modelo real (24/11/2026, nomic-embed-text no Ollama do compose, acervo do seed):** os cossenos do nomic ficam comprimidos (0,55–0,80). Nas 7 perguntas fora do acervo, o documento mais próximo **sem** nenhum termo da pergunta chegou a 0,633 ("Campanha de vacinação contra febre aftosa"); nas respondíveis, os documentos só-vetoriais acima de 0,63 eram quase todos **irrelevantes** (ex.: 0,671 em "Quantos hectares a queimada atingiu em Cáceres?"), e o 1º vetorial coincidiu com o relevante em 12 de 14 perguntas, sempre já achado pela textual. Por isso o limiar padrão é **0,65**: nenhuma pergunta fora do acervo ganha documento pela parte vetorial (abstenção preservada) e a contribuição vetorial fica na reordenação por RRF. Os falsos positivos de "Qual a área desmatada em Cáceres?" e "Resultado das eleições municipais" vêm da busca **textual** (termos em comum) e não mudam com o limiar.

**Benchmark com o modelo real (24/11/2026, `scripts/benchmark-busca.ps1`, limiar 0,65, 21 perguntas):**

| Recuperação | Precisão@3 | Acerto@1 | Recall@3 | Abstenção correta | Abstenção indevida |
|---|---|---|---|---|---|
| LEXICAL | 0,80 | 0,93 | 0,96 | 0,71 | 0,00 |
| HIBRIDA (nomic-embed-text) | 0,70 | 0,86 | 0,96 | 0,71 | 0,00 |

**Decisão:** no acervo demonstrativo (12 documentos curtos, perguntas com termos do texto) a híbrida **não supera** a textual — mesma abstenção e recall, menos precisão e acerto@1 (a fusão RRF promove vizinhos semânticos pouco relevantes). Por isso `EMBEDDINGS_ATIVO=false` é o padrão: a demonstração usa a busca textual e a resposta informa `recuperacao=LEXICAL`. Tudo continua implementado e testado; para ligar, `EMBEDDINGS_ATIVO=true` no `.env` (o modelo precisa estar baixado). Reavaliar com acervo maior ou outro modelo de embeddings.

**Como repetir:** o benchmark com o nomic-embed-text exige Docker (Ollama e API) e não foi rodado nesta Issue. Para medir: `scripts/ia-preparar.ps1` (baixa o modelo), aguardar ~2 min (indexação; `GET /api/documentos/busca?q=queimada` deve responder `recuperacao=HIBRIDA`) e rodar `scripts/benchmark-busca.ps1 -Saida benchmark.md` (ou `scripts/benchmark-busca.sh benchmark.md`, requer `jq`). O script só lê (login demonstrativo CONSULTA ou `SEMA_EMAIL`/`SEMA_SENHA`) e compara `modo=LEXICAL` × `modo=AUTO` com as mesmas perguntas. Calibrar `EMBEDDINGS_SIMILARIDADE_MINIMA` pelo resultado (abstenção correta sem perder acerto@1) e registrar a tabela aqui.

## Resumo das diferenças da comparação (Issue #142)

`POST /api/docia/comparacao/explicar`: agente `comparacao` sem ferramentas; a tabela de grandezas (extração determinística, `GrandezasExtrator`) vai na mensagem como dado e o modelo escreve só o `resumo` (até 3 frases). Sem IA ou após falha, resumo por regras com `modo` DEMONSTRATIVO e auditoria `AGENTE_DEMONSTRATIVO`. Os números da tabela nunca vêm do modelo; nada é gravado.

## Agente Copiloto de Minuta de vistoria (Issue #137)

`POST /api/demandas/{id}/minuta-vistoria` (ADMIN, GESTOR, ANALISTA e FISCAL designado; CONSULTA e fiscal não designado → 403). `AgenteMinutaService` lê antes da IA, com o perfil e o escopo do usuário (`DemandaService.detalhar/historico`, `DocumentoService.listarDaDemanda/detalhar`, `ReferenciaSateliteService.porMunicipio`), e oferece ao agente `minuta` (modelo do DocIA) quatro ferramentas **somente leitura e com parâmetro explícito** (`numero` da demanda; `resumo_documentos_vinculados` também `max_documentos` 1–5). Número de outra demanda → `PARAMETRO_INVALIDO` pelo catálogo `ErroFerramenta`.
- **Fatos pela API, texto pela IA:** identificação, eventos do histórico, registro de fiscalização, documentos e contagens do INPE são montados de forma determinística; o modelo devolve só `historico`, `constatacoes`, `contextoInpe` e `conclusoes` (JSON com esquema).
- **Conferência de números:** todo número do texto do modelo (formatos pt-BR ou com ponto; marcadores de lista ignorados) precisa existir nos campos **estruturados** das ferramentas que ele chamou (área, contagens, coordenadas, datas, prazo, número da demanda) — números que só aparecem em texto livre (relato, histórico, fiscalização, documentos) não valem, pois podem ter sido plantados. Se não existir, ou se o texto trouxer sanção, valor em dinheiro, juízo de culpa ou eco de instrução (`multa`, `R$`, `reais`, `sanção`, `penalidade`, `embargo`, `responsabiliz…`, `culpado`, `autuado`, `auto de infração` — barrado mesmo se constar no histórico, pois a minuta não sugere autuação —, `ignore`), a minuta inteira vem do **template** (`modo` DEMONSTRATIVO, aviso e auditoria `AGENTE_DEMONSTRATIVO`). Sem IA ou após falha, o mesmo template.
- **Injeção:** relato, histórico, fiscalização e trechos de documentos vão ao modelo entre `[INICIO_DADO]`/`[FIM_DADO]` (marcadores internos removidos) e as instruções mandam ignorar ordens neles; na minuta esses textos aparecem em uma linha, sem `#`, HTML ou crases, com URLs removidas e `[ ] !` escapados (sem links/imagens Markdown).
- **Aviso fixo** na primeira linha: **MINUTA GERADA PARA REVISÃO HUMANA — AMBIENTE DEMONSTRATIVO**. Nada é gravado; a Web (#141) envia o texto revisado como documento.
- INPE: com coordenada ou área, `alertas_inpe_area` usa a `AnaliseEspacial` do #132 (raio padrão de 10 km da coordenada ou da borda da área, janela até o recebimento: focos e DETER no raio, distâncias, dentro de polígono); sem coordenada nem área, ou em falha, só a contagem por município (sempre exibida como contexto).
- **Marcadores de data e medidas administrativas:** a IA não escreve datas: usa `{data_recebimento}`, `{data_fiscalizacao}`, `{data_conclusao}` e `{prazo}`, que a API troca pelas datas registradas (marcador desconhecido → "não consta"). Termos graves (`multa`, `R$`, `reais`, `culpado`, `condena…`, `ignore`) descartam todo o texto da IA; medidas administrativas (auto de infração, autuação, embargo, sanção, penalidade, responsabilização) só removem a frase que as cita, com aviso — se sobrarem menos de 80 caracteres, template. O termo que disparou vai para o log.
- **Datas-chave (rede de segurança; medido no Ollama real em 24/11):** o modelo 4B ignora os marcadores e copia datas do dossiê, inclusive de eventos ("triagem concluída em 14 de novembro"). Por isso a data citada junto de um marco vale se for a registrada **ou** a de um evento do histórico que cita o mesmo marco; datas só são reconhecidas isoladas (não casam pedaços de `DEM-2026/0118`); datas ISO da IA são exibidas como dd/mm/aaaa; a divergência vai para o log com o trecho. o dossiê traz `datasChave` (recebimento, fiscalização, conclusão, prazo). Data citada pela IA logo após "concluída/finalizada", "fiscalização/vistoria", "recebida" ou "prazo/vence" precisa ser a registrada (marco sem data registrada reprova); senão, template.

## Sentinela Territorial e Briefing Executivo (Issue #138)

`GET /api/intelligence/sentinela/briefing` (ADMIN, GESTOR). `SentinelaTerritorialService` calcula primeiro as anomalias por **regras determinísticas** (`RegrasSentinela`, consultas agregadas em `ConsultasSentinela` e a carga de `ConsultasDistribuicao` do #130): municípios com focos + DETER na janela recente do INPE e sem demanda aberta (com o centro do agrupamento mais denso para pré-preencher uma demanda de origem `MONITORAMENTO`), filas paradas por status e equipes com ativas/atrasadas por fiscal acima dos limites. Como o pacote do INPE é estático, a janela termina no `DATA_HORA` mais recente de cada tipo, não na data de hoje.
- **Agente `sentinela`** (modelo da triagem): ferramentas **somente leitura e com parâmetro** — `anomalias_sentinela(tipo = TODAS|INPE_SEM_DEMANDA|FILA_ACUMULADA|EQUIPE_SOBRECARREGADA)` e `indicadores_operacionais(escopo = ESTADO)` — que só devolvem os dados já calculados; uma rodada de ferramentas e resposta em texto livre (`resumo`, 3–5 frases). Passa pelo `ExecutorAgente` (fila da GPU, orçamento, auditoria `AGENTE_EXECUCAO`/`AGENTE_FALHA`, métricas no Painel do sistema e em `GET /api/agentes/status`).
- **Conferência de números:** todo número do texto precisa existir nos dados calculados (indicadores, totais, números e títulos das anomalias, janelas do INPE, data de hoje); texto vazio, curto (< 40), longo (> 1.200) ou com número ausente → **template determinístico** (`modo` DEMONSTRATIVO, aviso e auditoria `AGENTE_DEMONSTRATIVO`). Sem IA ou após falha, o mesmo template.
- **Nada é gravado** (além da auditoria): as ações são atalhos (`FILTRAR_MAPA`, `FILTRAR_DEMANDAS`, `CRIAR_DEMANDA_MONITORAMENTO`) e a demanda só é criada por uma pessoa no cadastro normal.
- **Cache e rotina:** o briefing fica em memória por `SENTINELA_CACHE_MINUTOS` (10) com uma única geração por vez; a rotina `SENTINELA_CRON` (padrão 07:00 de Cuiabá) o pré-calcula e `SENTINELA_AGENDAMENTO_ATIVO=false` a desliga. Por isso o modelo é chamado no máximo uma vez por janela de cache, e o `GET` não entra na categoria `IA` do limite de requisições. Na rotina agendada não há usuário logado: a auditoria fica sem usuário, e as consultas não dependem de escopo (ADMIN e GESTOR veem todas as demandas).

## Desempenho (Issue #120, 16/11/2026)

Mesmo modelo e mesma placa (GTX 1660 SUPER, 100% na GPU, Flash Attention ligado). Pedido do proprietário: mais rápido mantendo a qualidade.

| Medida | Antes | Depois |
|---|---|---|
| DocIA, 5 perguntas de referência (média) | 11,3 s (2 de 5 caíam no modo demonstrativo após ~18 s) | **~3 s**, 5 de 5 com IA |
| DocIA, 9 perguntas (inclui 1 sem resposta), 2 rodadas | — | **2,9–3,1 s**; 8 corretas citando o documento certo + "não há informação" na sem resposta; resultados iguais nas 2 rodadas |
| Triagem, 5 demandas (média) | 9,4 s | **7,2 s**, mesmas classificações |
| Primeira pergunta após subir ou após 30 min parado | +30–50 s (carga do modelo) | sem espera (aquecimento ao iniciar; fica 4 h) |

Causas encontradas e correções:
1. **Falha na etapa final do DocIA** (reproduzida direto no Ollama): as instruções permitiam buscar + ler 2 documentos (3 rodadas), mas o limite era 2; sem ferramentas, o modelo "chamava ferramenta" em texto, gerava 900 tokens (~15 s) e devolvia resposta vazia → 3 rodadas e, na etapa final, pedido explícito "não chame mais ferramentas".
2. **Resposta escrita duas vezes** (texto e depois JSON, +4–6 s) → agentes podem aceitar texto livre (`DefinicaoAgente.campoTexto`).
3. **Trechos longos copiados pelo modelo** para citações que a API reconferia → a API monta as citações.
4. **Documento errado** ("área desmatada em Colniza" lia o Parecer de Cáceres): radical, peso do título, corte dos pouco relevantes, trecho sem a linha do título e texto do mais relevante já entregue pela busca.
5. **Modelo descarregado após 30 min** → aquecimento ao iniciar (`AquecimentoModelo`, mesma janela de contexto de 8.192 tokens das chamadas, senão o Ollama recarregaria) e permanência de 4 h.

**Primeira resposta após ligar (Issue #124, 17/11/2026):** só carregar o modelo não bastava — a 1ª leitura de um texto grande paga um custo único de inicialização na GPU (1.203 tokens: 21,3 s na 1ª chamada, 5,0 s na 2ª), e a 1ª triagem após subir levava 28–36 s. O aquecimento passou a fazer também uma geração curta com ~1.000 tokens de entrada: 1ª triagem **12,2 s**, 1º DocIA **4,9 s**. Flash Attention foi testado desligado e **não** é a causa (1ª leitura 15,7 s; respostas normais mais lentas, 9,4 s contra 7,9 s) — continua no padrão do Ollama.

A GPU chega a ~99% **durante** a geração (alguns segundos) — é o esperado: o modelo usa a placa inteira para responder mais rápido e depois ela volta ao repouso. A memória de vídeo fica ocupada (~3,9 GB) enquanto o modelo está carregado.

## Streaming SSE dos passos (Issue #135, Implementado)

- `ExecutorAgente.executar(agente, tarefa, entidadeId, ObservadorAgente)`: callback de eventos (`EventoAgente`). A sobrecarga antiga usa `ObservadorAgente.NENHUM` (no-op) — agentes e endpoints JSON seguem iguais. O executor emite `ferramenta_iniciada`/`ferramenta_concluida` (nome seguro da ferramenta, resumo já exibido nos passos; erros só pelo catálogo `ErroFerramenta`). Uma falha do observador nunca interrompe o agente.
- Os serviços (`AgenteTriagemService`, `AssistenteService`) emitem `fallback_demonstrativo` com motivo público fixo quando caem nas regras locais/modo extrativo; `TransmissaoAgente` emite `inicio`, `delta_texto`, `concluido` (mesmo payload do JSON) ou `erro` (mensagem pública + referência).
- **`delta_texto` não é token a token:** os clientes `OllamaClienteModelo` e `AnthropicClienteModelo` usam respostas não transmitidas (o laço de ferramentas precisa da resposta completa para ler as chamadas e validar o JSON). O texto final vai num único `delta_texto`; transmitir tokens fica como evolução futura (exige `stream: true` nos dois clientes e leitura incremental do JSON).
- **Cancelamento:** `ObservadorAgente.cancelado()` é conferido antes de cada chamada ao modelo e de cada ferramenta; a espera na `FilaGpu` é fatiada (200 ms) e desiste quando o cliente sai — a vaga não é ocupada por quem desconectou. Uma chamada ao modelo já em andamento termina (pior caso: 1 timeout) e a vaga é liberada em seguida. Cancelado, a auditoria registra `AGENTE_FALHA` ("execução cancelada") e não conta falha nas métricas.
- Mesmo contexto de segurança na thread do stream (`DelegatingSecurityContextRunnable`): escopo das ferramentas e auditoria em nome do usuário, como no endpoint síncrono. O despacho `ASYNC` interno do contêiner (fim do stream) é liberado na cadeia de segurança — a requisição original já foi autenticada e autorizada.
- Nginx do Web: o `location /api/` atual funciona (`proxy_http_version 1.1`, sem gzip; `X-Accel-Buffering: no` desliga o buffer por resposta; `:keepalive` a cada 15 s fica abaixo do `proxy_read_timeout 120s`).

## Configuração (`.env`)

| Variável | Padrão | Descrição |
|---|---|---|
| `AGENTES_PROVEDOR` | `ollama` | `ollama`, `anthropic` ou `demo`. |
| `COMPOSE_PROFILES` | vazio | `ia` ou `ia-cpu` para subir o serviço Ollama. |
| `OLLAMA_URL` | `http://ollama:11434` | Endereço interno do Ollama. |
| `OLLAMA_MODELO` | `qwen3:4b-instruct` | Modelo local (precisa suportar ferramentas). |
| `OLLAMA_PENSAR` | `false` | `true` só para modelos com modo de raciocínio (ex.: `qwen3:4b`); os demais recusam o campo. |
| `OLLAMA_MANTER_CARREGADO` | `4h` | Tempo que o modelo fica na memória de vídeo após o último uso (`30m`, `4h`, `-1` = sempre). |
| `AGENTES_AQUECER` | `true` | Carregar o modelo em segundo plano ao iniciar a API. |
| `ANTHROPIC_API_KEY` | vazia | Só com `AGENTES_PROVEDOR=anthropic`; criada pelo proprietário, nunca versionada. |
| `AGENTE_TRIAGEM_MODELO` / `AGENTE_DOCIA_MODELO` | `claude-haiku-5-5` / `claude-sonnet-5-5` | Modelos do Claude, se usado. |
| `AGENTES_TIMEOUT_SEGUNDOS` | `90` | Tempo máximo por chamada (5–300). |
| `AGENTES_MAX_ITERACOES` | `6` | Máximo de idas e voltas com ferramentas (1–10). |
| `AGENTES_ORCAMENTO_SEGUNDOS` | `150` | Orçamento total de tempo de uma execução (1–600). Conferido antes de cada chamada ao modelo; pior caso = orçamento + 1 timeout. |
| `AGENTES_MAX_CHAMADAS_FERRAMENTA` | `8` | Máximo de chamadas de ferramenta somadas na execução (1–30). |
| `AGENTES_GPU_CONCORRENCIA` | `1` | Chamadas simultâneas ao modelo na fila da GPU (1–16; Issue #134). |
| `AGENTES_GPU_ESPERA_SEGUNDOS` | `20` | Espera máxima por vaga na fila (1–120 s); esgotada, modo demonstrativo identificado. |
| `EMBEDDINGS_ATIVO` | `false` | Busca híbrida do DocIA (#141); desligada por padrão após o benchmark real; `true` liga. |
| `EMBEDDINGS_MODELO` | `nomic-embed-text` | Modelo de embeddings no Ollama (768 dimensões). |
| `EMBEDDINGS_SIMILARIDADE_MINIMA` | `0.65` | Cosseno mínimo (0–1) para um documento entrar só pela parte vetorial. |
| `EMBEDDINGS_REINDEXAR_SEGUNDOS` | `120` | Intervalo da indexação de documentos sem trechos (10–86400). |
| `BUSCA_RRF_K` | `60` | Constante k da fusão RRF (1–1000). |
| `AGENTES_SSE_BATIMENTO_SEGUNDOS` | `15` | Intervalo do comentário `:keepalive` dos streams SSE (1–60 s; Issue #135). Também no compose e no `.env.example`. |
| `AGENTES_SSE_MAX_SIMULTANEOS` | `16` | Streams SSE de agentes ao mesmo tempo (1–64); além disso, 503 `problem+json`. |

`GET /api/agentes/status` informa o modo efetivo (`LOCAL`, `CLAUDE` ou `DEMONSTRATIVO`) e o modelo de cada agente — nunca chaves.

## Segurança

- Ferramentas só consultam e rodam com as permissões do usuário; gravar continua exigindo ação humana.
- Conteúdo de documentos pode tentar instruir o modelo (*prompt injection*): por isso o agente não tem ferramentas de escrita e suas respostas são sempre sugestões revisadas.
- Ollama sem porta publicada no host (só a API o acessa) e com modelos em nuvem desligados.
- Chave da Anthropic (se usada) só no `.env`; `AgentesConfiguracao.toString()` não a inclui; erros registram só o tipo da exceção.
- Auditoria: `AGENTE_EXECUCAO`, `AGENTE_FALHA` e `AGENTE_DEMONSTRATIVO` (entidade `AGENTE`) com provedor, modelo, ferramentas, **quantidade de chamadas de ferramenta, latência (ms)** e tokens.
- **Catálogo de erros públicos das ferramentas** (`ErroFerramenta`, relatório técnico P2): quando uma ferramenta falha, o modelo, os passos exibidos na tela e a auditoria recebem só `CÓDIGO: mensagem pública (ref. ERR-xxxxxxxx)`. O detalhe técnico (`e.getMessage()`, que numa ferramenta futura poderia trazer caminho, SQL ou credencial) vai **só para o log da API**, com a mesma referência. Classificação pelo **tipo** da exceção:

  | Código | Exceção | Mensagem pública |
  |---|---|---|
  | `ACESSO_NEGADO` | `AccessDeniedException` (403 do Service) | O perfil do usuário não tem acesso a este registro. |
  | `NAO_ENCONTRADO` | `RecursoNaoEncontradoException` | Registro não encontrado ou inativo. |
  | `PARAMETRO_INVALIDO` | `IllegalArgumentException`, `DadosInvalidosException` | Parâmetro inválido para a ferramenta. |
  | `FERRAMENTA_INEXISTENTE` | o modelo pediu uma ferramenta que o agente não tem | A ferramenta pedida não existe para este agente. |
  | `ERRO_INTERNO` | qualquer outra | Falha interna ao executar a ferramenta. |

- **Avaliação adversarial** (`AvaliacaoAgentesTest`, modelo simulado, sem rede): erro sintético com caminho e credencial marcados (não aparece no modelo, nos passos nem na auditoria), *prompt injection* dentro de documento pedindo ferramentas de gravação (não existem; nada é executado; as ferramentas oferecidas nunca mudam), modelo indisponível (nenhuma chamada; `AGENTE_FALHA`; modo demonstrativo), orçamento de chamadas e orçamento de tempo (interrompem a execução).

### Aderência textual (DocIA) não é confiança factual

A aderência textual vem da busca **textual** (a parte vetorial da busca híbrida, Issue #141, tem medida própria, `similaridadeSemantica`, que também não é confiança). Cada documento recebe pontos pelos termos da pergunta que contém (título vale mais) e é classificado em **aderência textual** alta (70%+ dos pontos do melhor documento da mesma busca), média (40–70%) ou baixa. A escala é **relativa**: o melhor documento de uma busca fraca também sai "alta". Ela **não é probabilidade de a resposta estar correta**. A API a devolve em cada citação (`referencias[].aderenciaTextual`) e a tela do Assistente explica o rótulo. Quando os documentos não têm a resposta, o agente responde `NAO_ENCONTRADO` e a tela mostra "não encontrei" (abstenção); citações só apontam documentos que as ferramentas devolveram ou leram, com o trecho conferido no texto do documento. A busca híbrida foi implementada na Issue #141 (seção acima), com benchmark textual × híbrida.

## Limitações e futuro

- Modelo pequeno (4B): respostas mais simples que modelos grandes; por isso sugestões são validadas pela API e revisadas por uma pessoa. Tabelas (ex.: boletim mês × bioma) são lidas com dificuldade: perguntas vagas sobre elas podem ter resposta imprecisa.
- Sem placa NVIDIA o modelo roda só no processador (perfil `ia-cpu`): estimativa de 30–90 s por triagem e 1–3 min por pergunta, com o processador a 100% — para uso real, a IA deve ficar num servidor com GPU (os usuários só usam o navegador).
- Uso real por um órgão público: o modelo local é uma boa base (dados não saem da rede); com provedor externo, exige contrato, análise de LGPD e governança de IA.
- Futuro (P2): Agente de Prazos (agendado), Analista Ambiental (Intelligence) e Assistente SEMA ALERTA (multiagente).

### Ferramentas geoespaciais do Agente de Triagem (Issue #132, Implementado)
Somente leitura, com o perfil/escopo do usuário, parâmetros explícitos (`numero`, `raio_km`; raio padrão 10 km, máximo 50):
- `alertas_inpe_raio_coordenada`: focos de calor num raio (pré-filtro por caixa no SQL + Haversine em Java), alertas DETER cujo polígono toca o raio (distância ponto→polígono em Java; `dentroDePoligonoDeter`, `menorDistanciaDeterKm`) a partir da coordenada ou do centroide de `areaGeometria`; sem ambos, devolve os totais do município (fallback).
- `demandas_proximas_coordenada`: demandas não finalizadas no raio, da mais próxima à mais distante, **sem restringir por município** (duplicatas na divisa), respeitando o escopo do FISCAL; sem coordenada lista as do município.
- `documentos_mesma_localidade`: documentos ativos do DocIA que citam a localidade (no município), vinculados a demandas próximas ou do mesmo município (documentos não têm coordenada própria).
A resposta da sugestão traz `espacial` (referência COORDENADA/AREA/MUNICIPIO, raio, contagens INPE, demandas próximas com `distanciaKm`, documentos relacionados) calculada pela API com raio padrão, independentemente do que o modelo chamar. No modo demonstrativo, com coordenadas, as duplicatas e a prioridade usam a proximidade (limiar de 10 focos+alertas no raio, nos 90 dias anteriores ao recebimento; janela configurável por `sema.triagem.espacial.janela-dias`) e a evidência "Proximidade espacial" identifica isso.

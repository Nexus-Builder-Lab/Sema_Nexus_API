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
4. O laço repete até a resposta final em JSON (ou até `AGENTES_MAX_ITERACOES`, padrão 6).
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
| Agente de Triagem | SEMA Flux | `obter_demanda`, `buscar_demandas_semelhantes`, `alertas_inpe_municipio` | #107 (implementado) |
| Assistente DocIA | SEMA DocIA | `buscar_documentos`, `ler_documento` | #108 (implementado) |

## Preparar a IA local (uma vez)

1. No `.env`: `COMPOSE_PROFILES=ia` (GPU NVIDIA; sem GPU use `ia-cpu`, mais lento) e `AGENTES_PROVEDOR=ollama`.
2. `.\scripts\ia-preparar.ps1` (ou `./scripts/ia-preparar.sh`): sobe o serviço `ollama` e baixa `OLLAMA_MODELO` para o volume `ollama` do projeto.
3. `GET /api/agentes/status` deve indicar `LOCAL`. A primeira resposta demora mais (carrega o modelo na GPU, ~30–50 s); depois, ~3–5 s por etapa. O modelo fica carregado por 30 min (`OLLAMA_KEEP_ALIVE`).
4. Depois de baixado, **funciona sem internet** (bom para a apresentação).

**Rede com inspeção HTTPS (ex.: firewall da SEMA):** o download do modelo pode falhar com `x509: certificate signed by unknown authority`. Não desligue a verificação TLS. Opções: baixar em outra rede, ou (feito em 13/11/2026, com autorização do proprietário) adicionar temporariamente o certificado **público** da CA do firewall — já confiado pelo Windows — só no container do Ollama, baixar o modelo e recriar o container (`docker compose up -d --force-recreate ollama`) para remover a confiança.

## Agente de Triagem (Issue #107)

`POST /api/demandas/{id}/triagem/sugestao` (ADMIN, GESTOR, ANALISTA; demanda Pendente ou Em triagem, senão 409). Não grava nada.

1. Uma rodada de ferramentas (o modelo costuma chamar as três juntas): dados da demanda; até 8 demandas do mesmo município (escopo do usuário); focos de calor e alertas DETER do INPE no município.
2. Resposta final já no esquema JSON (`maxRodadasFerramentas = 1`): decisão, categoria, prioridade, justificativa, duplicatas.
3. A API valida: enums inválidos ou ausentes são completados pelas regras locais (com aviso); duplicatas só se vieram da ferramenta e não estão finalizadas; justificativa limitada a 3 frases completas; o **núcleo regional** vem da região do município (determinístico).
4. Falha da IA → regras locais (`RegrasTriagem`: palavras-chave → categoria; gravidade e INPE → prioridade), com aviso.

Medido em 13/11/2026 (GTX 1660 SUPER, `qwen3:4b-instruct`): **7–11 s** por sugestão (10 de 10 demandas pendentes com IA, sem cair nas regras); a primeira após subir o Ollama leva ~30–50 s (carga na GPU).

Lições com modelo pequeno: ferramentas **sem parâmetros** confundem o modelo (ele fica raciocinando sobre como passar o número) — toda ferramenta tem um parâmetro, e o servidor usa sempre a demanda em triagem; o `qwen3:4b` (com raciocínio) gastava ~1.000 tokens por etapa (15–20 s), por isso o padrão é a versão *instruct*; sem limite de tamanho no esquema o modelo pode se repetir até o limite de tokens.

## Assistente DocIA agêntico (Issue #108)

Mesmo endpoint `POST /api/docia/perguntas`. Com IA disponível, o agente decide o que buscar (`buscar_documentos`, a mesma busca textual da biblioteca) e o que ler (`ler_documento`, até 4.000 caracteres do texto extraído), em até 2 rodadas de ferramentas, e responde no esquema `{resposta, suficiente, referencias[{documentoId, trecho}]}`.

A API valida: referências só de documentos que as ferramentas devolveram; título vindo do banco; **trecho conferido no texto** do documento (trecho inventado é trocado pelo trecho real mais aderente); sem citação, cita o primeiro documento consultado; `suficiente=false` → "não há informação suficiente". A recusa de perguntas sobre leis, multas e procedimentos oficiais continua antes do agente. Falha → modo extrativo (identificado).

Resposta ganhou `agente` (LOCAL/CLAUDE/DEMONSTRATIVO/EXTERNO), `modelo` e `passos` (compatível).

Medido em 13/11/2026 (`qwen3:4b-instruct`, GTX 1660 SUPER): 3–12 s por pergunta; "O que o parecer 014/2026 concluiu?" → resposta correta citando a versão 1 do parecer; pergunta sem base nos documentos → "não há informação suficiente".

## Configuração (`.env`)

| Variável | Padrão | Descrição |
|---|---|---|
| `AGENTES_PROVEDOR` | `ollama` | `ollama`, `anthropic` ou `demo`. |
| `COMPOSE_PROFILES` | vazio | `ia` ou `ia-cpu` para subir o serviço Ollama. |
| `OLLAMA_URL` | `http://ollama:11434` | Endereço interno do Ollama. |
| `OLLAMA_MODELO` | `qwen3:4b-instruct` | Modelo local (precisa suportar ferramentas). |
| `OLLAMA_PENSAR` | `false` | `true` só para modelos com modo de raciocínio (ex.: `qwen3:4b`); os demais recusam o campo. |
| `ANTHROPIC_API_KEY` | vazia | Só com `AGENTES_PROVEDOR=anthropic`; criada pelo proprietário, nunca versionada. |
| `AGENTE_TRIAGEM_MODELO` / `AGENTE_DOCIA_MODELO` | `claude-haiku-5-5` / `claude-sonnet-5-5` | Modelos do Claude, se usado. |
| `AGENTES_TIMEOUT_SEGUNDOS` | `90` | Tempo máximo por chamada (5–300). |
| `AGENTES_MAX_ITERACOES` | `6` | Máximo de idas e voltas com ferramentas (1–10). |

`GET /api/agentes/status` informa o modo efetivo (`LOCAL`, `CLAUDE` ou `DEMONSTRATIVO`) e o modelo de cada agente — nunca chaves.

## Segurança

- Ferramentas só consultam e rodam com as permissões do usuário; gravar continua exigindo ação humana.
- Conteúdo de documentos pode tentar instruir o modelo (*prompt injection*): por isso o agente não tem ferramentas de escrita e suas respostas são sempre sugestões revisadas.
- Ollama sem porta publicada no host (só a API o acessa) e com modelos em nuvem desligados.
- Chave da Anthropic (se usada) só no `.env`; `AgentesConfiguracao.toString()` não a inclui; erros registram só o tipo da exceção.
- Auditoria: `AGENTE_EXECUCAO`, `AGENTE_FALHA` e `AGENTE_DEMONSTRATIVO` (entidade `AGENTE`) com provedor, modelo, ferramentas e tokens.

## Limitações e futuro

- Modelo pequeno (4B): respostas mais simples que modelos grandes; por isso sugestões são validadas pela API e revisadas por uma pessoa.
- Uso real por um órgão público: o modelo local é uma boa base (dados não saem da rede); com provedor externo, exige contrato, análise de LGPD e governança de IA.
- Futuro (P2): Agente de Prazos (agendado), Analista Ambiental (Intelligence) e Assistente SEMA ALERTA (multiagente).

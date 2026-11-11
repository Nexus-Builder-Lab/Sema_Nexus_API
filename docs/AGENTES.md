# SEMA NEXUS — Agentes, subagentes e fluxo multiagente

> **Status:** Implementado em 11/11/2026 (estrutura). Será usado na próxima retomada do autopilot, que continua **pausado** até ordem do proprietário.

## Visão geral

Em vez de uma única sessão fazer tudo, cada Issue passa por **subagentes especializados** coordenados por um **orquestrador**:

```
                       ┌──────────────────────────┐
                       │ ORQUESTRADOR (/next-issue)│  decide · branch · PR · merge · labels · Status
                       └────────────┬─────────────┘
          ┌─────────────────────────┼─────────────────────────┐
          ▼                         ▼                         ▼
   planejador-issue  ──▶  implementador-(backend|frontend)  ──▶  em paralelo:
                                                                 ├─ validador-qa
                                                                 ├─ revisor-codigo
                                                                 ├─ revisor-seguranca         (quando aplicável)
                                                                 └─ revisor-ui-acessibilidade (Web, telas)
                                      ▲                               │
                                      └──── até 3 ciclos de correção ─┘
                                             (diagnosticador · Opus após o 2º)
                                                                 │
                                                                 ▼
                                                           documentador ──▶ orquestrador abre PR, espera CI, faz merge
```

Há **dois níveis de multiagente**:
1. **Entre repositórios:** duas sessões do autopilot em paralelo (API e Web), coordenadas pelas dependências "Blocked by" e pela label `aguardando-dependencia`; a trava `docker.lock` evita subidas simultâneas do compose.
2. **Dentro de cada Issue:** o orquestrador e os subagentes acima; validação e revisões rodam em paralelo.

## Agentes

| Agente | Repositórios | Papel | Ferramentas | Modelo · esforço |
|---|---|---|---|---|
| `planejador-issue` | API, Web | Lê a Issue, dependências e código; devolve plano, testes previstos, mapa dos critérios e riscos | leitura + Bash/PowerShell | Sonnet 5.5 · alto |
| `implementador-backend` | API | Implementa e corrige o escopo (código, migrations, testes, docs técnicos), roda `mvn verify`, commita | leitura + edição + Bash/PowerShell | Sonnet 5.5 · alto |
| `implementador-frontend` | Web | Implementa e corrige telas/componentes, mocks e testes; roda lint/test/build; commita | leitura + edição + Bash/PowerShell | Sonnet 5.5 · alto |
| `validador-qa` | API, Web | Build, testes, Docker (com trava) e evidência de cada critério de aceitação | leitura + Bash/PowerShell (não edita) | Sonnet 5.5 · alto |
| `revisor-codigo` | API, Web | Revisão independente do diff: defeitos, regras do projeto, testes, docs | somente leitura | Sonnet 5.5 · alto |
| `revisor-seguranca` | API, Web | Autorização, JWT, credencial demonstrativa, uploads, segredos, logs, CORS, XSS | somente leitura | Sonnet 5.5 · alto |
| `revisor-ui-acessibilidade` | Web | Identidade visual, responsividade, estados, acessibilidade, faixa demonstrativa | somente leitura | Sonnet 5.5 · alto |
| `documentador` | API, Web | Entrada no diário, status nos docs, rascunho do corpo do PR | leitura + edição (só docs) | Sonnet 5.5 · alto |
| `diagnosticador` | API, Web | Causa raiz de falha persistente ou de integração; separa defeito de bloqueio de ambiente | somente leitura | **Opus 5.5 · médio** (esporádico) |

Arquivos: `.claude/agents/<nome>.md` em cada repositório (frontmatter `name`, `description`, `tools`, `model`, `effort`).

## Regras de segurança da estrutura

- **Somente o orquestrador** cria branch, faz push, abre PR, faz merge, altera labels/Status no Project e escreve no `RELATORIO_ERROS.md`.
- Agentes de revisão, validação e diagnóstico não recebem ferramentas de edição.
- As regras `deny` do `.claude/settings.json` valem para todos os agentes (sem force push, sem apagar volumes, sem mexer em permissões/visibilidade).
- Um veredito sem evidência não conta como aprovação. O merge exige: CI verde, `validador-qa` aprovado, critérios comprovados, Docker quando aplicável, dependências fechadas e nenhum achado bloqueante/crítico/alto dos revisores.

## Custo de tokens

Mais agentes = mais leituras do mesmo código. Para conter o custo:
- todos em **Sonnet 5.5**; o **Opus 5.5** só no `diagnosticador`, após 2 ciclos sem sucesso;
- `revisor-seguranca` e `revisor-ui-acessibilidade` só quando a Issue toca nesses temas;
- na correção, só os agentes que reprovaram rodam de novo (além do `validador-qa`);
- os relatórios dos subagentes são curtos, para o orquestrador não carregar arquivos grandes.

## Como usar fora do autopilot

Numa sessão interativa do Claude Code em um dos repositórios, peça, por exemplo: "use o revisor-codigo no branch atual" ou "use o planejador-issue na Issue 12". O comando `/agents` lista e edita os agentes.

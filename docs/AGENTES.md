# SEMA NEXUS — Agentes e fluxo multiagente

> **Status:** Implementado. Versão econômica desde 11/11/2026 (Issue #55): mesmos modelos e esforços, muito menos tokens por Issue.

## Fluxo de uma Issue

```
autopilot.ps1 ──(proxima.mjs, 0 tokens)──▶ há trabalho?  não → dorme e verifica de novo
                                              │ sim
                                              ▼
                    ORQUESTRADOR (/next-issue <n>)   decide · branch · PR · merge · labels · Status
                                              │
                    [planejador-issue — só em Issues grandes]
                                              ▼
                          implementador-(backend|frontend)   planeja, implementa, testes afetados, diário
                                              ▼
                      em paralelo:  validador-qa  ∥  revisor
                                              │  (até 3 ciclos de correção; diagnosticador Opus após o 2º)
                                              ▼
                              orquestrador: PR → CI → merge condicionado
```

Dois níveis de multiagente: **entre repositórios** (sessões API e Web em paralelo, coordenadas por "Blocked by", `aguardando-dependencia` e a trava `docker.lock`) e **dentro da Issue** (orquestrador + agentes).

## Agentes

| Agente | Repo | Papel | Edita? | Modelo · esforço |
|---|---|---|:-:|---|
| `implementador-backend` | API | Planeja e implementa o escopo, testes afetados, docs técnicos e diário | sim | Sonnet 5.5 · médio |
| `implementador-frontend` | Web | Idem para o frontend | sim | Sonnet 5.5 · médio |
| `validador-qa` | ambos | Suíte completa (uma vez), Docker (uma vez, quando aplicável), evidência por critério | não | Sonnet 5.5 · médio |
| `revisor` | ambos | Revisão única: defeitos, regras, testes, segurança e (Web) interface/acessibilidade | não | Sonnet 5.5 · médio |
| `planejador-issue` | ambos | Plano curto, **só** para Issues grandes ou arriscadas | não | Sonnet 5.5 · médio |
| `diagnosticador` | ambos | Causa raiz após 2 ciclos sem sucesso; separa defeito de bloqueio de ambiente | não | Opus 5.5 · médio |

A **seleção** da próxima Issue é feita pelo script `scripts/autopilot/proxima.mjs` (Node + `gh`), sem modelo.

## Medidas de economia (11/11/2026)

A análise das 24 primeiras iterações mostrou que o custo vem do **contexto relido a cada turno** (1,5–6 milhões de tokens de cache por Issue), não do texto gerado. Por isso:

| Medida | Efeito |
|---|---|
| Saídas de comando para arquivo; só resumo/erro no contexto (Maven `-q`, `--reporter=dot`, `docker compose ... > log`, `gh pr checks --watch > $null`) | Contexto menor em todos os turnos seguintes |
| Seleção por `proxima.mjs`; espera sem chamar o Claude | Fim das iterações `SEM_TRABALHO` (antes ~US$ 0,95 em 7 iterações) |
| Não desenvolver contra dependências abertas de outro repo | Fim do trabalho que ficava parado em `AGUARDANDO_DEPENDENCIA` (~US$ 3,18 em 3 iterações) |
| Revisores fundidos em um `revisor`; sem `documentador` e `selecionador`; planejador só em Issues grandes | Menos agentes relendo o mesmo código |
| Implementador roda só testes afetados; suíte completa e Docker uma vez, no validador; na correção só reroda quem reprovou | Menos execuções e menos logs |
| `CLAUDE.md` e `/next-issue` enxutos (detalhes em `docs/`) | Contexto fixo menor em todo agente e turno |
| Sem retentativa Opus para falha de ambiente (`FALHOU_AMBIENTE`) | Sem gasto de Opus quando repetir não resolve |

## Regras

- **Só o orquestrador** faz push, abre PR, faz merge, altera labels/Status e escreve no `RELATORIO_ERROS.md`.
- `validador-qa`, `revisor`, `planejador-issue` e `diagnosticador` não editam código.
- Merge exige: CI verde, validador aprovado, critérios comprovados, Docker quando aplicável, dependências fechadas, revisor sem bloqueante.
- As regras `deny` do `.claude/settings.json` valem para todos.

# SEMA NEXUS — Guia de desenvolvimento

## Fluxo de trabalho (trunk-based)

Somente `main` + branches de trabalho. Não existe `develop`.

1. Escolha uma Issue da **sprint atual** no [Project](https://github.com/orgs/Nexus-Builder-Lab/projects/1) (view "Sprint atual"), priorizando P0. Atribua-se a ela.
2. Confira o "Blocked by" da Issue. Não comece se houver bloqueio aberto.
3. Crie o branch a partir da `main` atualizada: **`feature/GH-<número da Issue>`** (número da Issue **do repositório onde está o branch**). Com Claude Code: `/start-issue <n>`.
4. Mova o item para **Em andamento** no Project.
5. Commits: `GH-<n>: descrição curta`.
6. Abra o PR: título `GH-<n> — <título da Issue>`, corpo pelo template, com `Closes #<n>`. Mova para **PR solicitado**.
7. CI verde + revisão → merge **somente com autorização do proprietário**. A Issue fecha e vai para **Finalizado**.

Regras: um branch por Issue; sem prefixos `fix/`, `chore/`, `docs/`; sem sufixos; não crie branches antecipadamente; Issue mesclada que precisa de mais trabalho → nova Issue.
Mudança que exige API e Web → uma Issue em cada repositório, ligadas por "Blocked by", um PR em cada repositório.

## Status do Project

| Status | Significado | Quem move |
|---|---|---|
| Pendente | No backlog, ainda não planejada para começar | inicial (S2, S3, P1, P2) |
| A fazer | Planejada para a sprint e pronta para começar | inicial (S1); você, no início de cada sprint |
| Em andamento | Branch criado, trabalho em curso | você |
| PR solicitado | PR aberto aguardando CI/revisão | você |
| Finalizado | Issue fechada após merge e critérios atendidos | automação "Item closed" |

### Automações do Project (configuração manual — a API do GitHub não permite configurá-las)

Em **Project → ⋯ → Workflows**:

| Workflow nativo | Configuração |
|---|---|
| Item added to project | Ativar → Status = **Pendente** |
| Item closed | Ativar → Status = **Finalizado** |
| Item reopened | Ativar → Status = **A fazer** |
| Pull request merged | Ativar → Status = **Finalizado** |
| Pull request linked to issue | Ativar → Status = **PR solicitado** (se disponível no seu plano) |
| Auto-add to project | Ativar com filtro `is:issue,pr is:open`. No plano Free há **1** workflow de auto-add por Project; ele cobre um repositório. Para o outro repositório, adicione itens manualmente ou com `gh project item-add 1 --owner Nexus-Builder-Lab --url <url>` |

Mudança manual de Status no Project é imediata em todas as views.

## Ambiente local

| Ferramenta | Versão mínima |
|---|---|
| Git | 2.40 |
| Java (Temurin) | 21 |
| Maven | 3.9 |
| Node.js | 22 LTS (24 funciona) |
| Docker Desktop / Engine + Compose v2 | 24 / 2.20 |
| RAM | 8 GB (16 GB recomendado) |

Clone os dois repositórios lado a lado:
```
projetos/
├── Sema_Nexus_API/   # API + docker-compose.yml + scripts/ + docs/
└── Sema_Nexus_Web/
```

Execução via Docker: [DOCKER.md](DOCKER.md). Execução da API fora do Docker (após a Issue #1): Oracle em container + `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.

## CI (GitHub Actions)

| Repositório | Workflow | Gatilhos | Jobs |
|---|---|---|---|
| API | `ci.yml` | push `main` e `feature/GH-*`, PR para `main`, manual | Nome do branch · Compilar e testar (Maven) · Exportar contrato OpenAPI · Build da imagem Docker |
| API | `project-health.yml` | diário 12:00 UTC + manual | Resumo de saúde do projeto ([PROJECT_HEALTH.md](PROJECT_HEALTH.md)) |
| API | `compose-smoke.yml` | manual + noturno | Planejado (Issue #20) |
| API | `oracle-it.yml` | manual + noturno | Planejado, P1 (Issue #23) |
| Web | `ci.yml` | push `main` e `feature/GH-*`, PR para `main`, manual | Nome do branch · Lint, testes e build · Build da imagem Docker |

Enquanto `pom.xml` / `package.json` / `Dockerfile` não existirem, os jobs correspondentes terminam com **aviso** (não com sucesso falso nem falha). Todos usam `concurrency` com cancelamento de execuções antigas.

Cota: o plano Free tem 2.000 minutos/mês de Actions para repositórios privados. Os jobs com Oracle e o smoke do compose são manuais/noturnos por isso.

## Proteção da `main`

**Indisponível no plano atual.** Rulesets e branch protection exigem repositório público ou plano Team/Enterprise para repositórios privados. A disciplina é garantida por: CLAUDE.md, job de nome de branch no CI, `.claude/settings.json` (bloqueia force push) e revisão do proprietário antes de cada merge.
Se o plano mudar, aplique um ruleset na `main`: exigir PR (0 aprovações), exigir os checks `Nome do branch`, `Compilar e testar` e `Build da imagem Docker`, bloquear force push e exclusão, exigir resolução de conversas.

## Guardrails do Claude Code

`.claude/settings.json` nega force push, exclusão/edição de repositório, chamadas `gh api` a colaboradores/convites/permissões/visibilidade, `rm -rf` fora do projeto e comandos Docker que apagam volumes; pede confirmação para merge, tags, releases, push direto na `main` e `demo-reset`.

## Labels

`P0` `P1` `P2` · `module:core` `module:intelligence` `module:flux` `module:docia` · `type:feature` `type:bug` `type:task` · `blocked` — idênticas nos dois repositórios.

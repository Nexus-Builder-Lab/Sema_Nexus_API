# CLAUDE.md — SEMA NEXUS · Sema_Nexus_API

Regras permanentes. Detalhes: `docs/ARCHITECTURE.md`, `docs/DATABASE.md`, `docs/API.md`, `docs/DEVELOPMENT.md`, `docs/AUTOPILOT.md`, `docs/AGENTES.md`, `docs/DEFINITION_OF_DONE.md` — leia só o que a tarefa exigir.

## Projeto
- Plataforma **demonstrativa** (inteligência ambiental, demandas de fiscalização, gestão documental) de **Mato Grosso**. **Só dados fictícios** (municípios/biomas do IBGE são reais; o resto é simulado). Exceção aprovada: dados públicos do INPE na camada de satélite, sempre identificados.
- **Nenhuma integração institucional** (SEMA-MT, SIGA, SISREV…). Dados simulados exibem "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS".
- Implementação até 25/11/2026 · revisão/tag 26/11 · apresentação 27/11.
- Repos: este = backend + infra/docs transversais (compose, `scripts/`, `docs/`); `Sema_Nexus_Web` = frontend; `FabricioJorge0/prototipo_SEMA_NEXUS` = referência, nunca alterar. Nunca misturar código dos dois repos: uma Issue e um PR por repositório.

## Stack e arquitetura
Java 21 · Spring Boot 3.5 · Oracle 23ai Free · Flyway · JWT/BCrypt · springdoc · JUnit 5/Mockito/Testcontainers · Maven · Docker Compose. Não trocar tecnologia sem aprovação.
- `Controller → Service → Repository → Entity` + `dto/`; regra de negócio no Service; erros `problem+json` pelo `GlobalExceptionHandler`; `PageResponse`; prefixo `/api`.
- Autorização **no backend** (`@PreAuthorize` + escopo). Perfis: ADMIN, GESTOR, FISCAL, ANALISTA, CONSULTA.
- **Oracle obrigatório** (nunca H2/Postgres/MySQL/SQLite). Schema só por Flyway; **nunca editar migration já mesclada**. SQL parametrizado.
- Arquivos no volume (`STORAGE_DIR`), nunca no banco. Mudou endpoint → anotações OpenAPI + `docs/API.md`.
- Testes unitários sem Oracle; com Oracle só com a tag `oracle`.

## Segurança (inegociável)
- Sem credenciais no código, imagens ou compose; configuração por variáveis de ambiente (`.env` fora do Git; `.env.example` atualizado).
- Credencial `admin@admin.com` / `12345678` **só demonstrativa**: profiles `demo`/`dev`, só hash BCrypt, trava `TestUserProfileGuard`. Texto puro só em README, `docs/DEMO.md`, `docs/DOCKER.md` e testes. Não relaxar validações (senha ≥ 8).
- Nunca `docker compose down -v`, `docker volume rm`, `docker system prune`, nem parar/remover containers de outros projetos. Nunca encerrar processos que você não iniciou (a porta 8080 do host é de outro serviço: use 18080).
- GitHub: nunca se adicionar como colaborador, convidar contas, mudar permissões/visibilidade, apagar histórico, force push ou push na `main`. Tag/release só com autorização.

## Fluxo
- Sempre a partir de uma Issue. Títulos `[SEMA_NEXUS_API][MÓDULO] ...` (`[CORE]` `[INTELLIGENCE]` `[FLUX]` `[DOCIA]` `[DATABASE]` `[INFRA]` `[TEST]` `[DOCS]`).
- Branch `feature/GH-<n>` (n = Issue deste repo; nenhum outro padrão). Commits `GH-<n>: ...` (mensagem com aspas → `git commit -F arquivo`). PR `GH-<n> — <título>` com `Closes #<n>`.
- Escopo novo = proposta ao proprietário, nunca implementar direto. Não inventar resultado de teste/CI. Atualizar docs (Implementado / Planejado / Futuro).
- **Definition of Done:** escopo e critérios atendidos com evidência, testes executados e aprovados, CI verde, funciona via Docker quando aplicável, docs atualizados, sem erro crítico.

## Autopilot (autorizado pelo proprietário)
- `/next-issue` orquestra agentes (`.claude/agents/`). **Merge automático só se:** CI verde · validador aprovou · critérios comprovados · Docker validado quando aplicável · dependências fechadas · revisor sem achado bloqueante. Só falta dependência → label `aguardando-dependencia`. Após 3 ciclos sem sucesso → label `autopilot-falhou` + entrada em `C:\SEMANEXUS\autopilot\RELATORIO_ERROS.md`.
- Só o orquestrador faz push, PR, merge, labels e Status. Diário em `docs/DIARIO_DESENVOLVIMENTO.md` no mesmo PR.
- Modelos: Sonnet 5.5 médio (padrão); tarefas mecânicas por script (0 tokens) ou Haiku 5.5; Opus 5.5 médio só no `diagnosticador` e na retentativa após falha de código.
- Ambiente: `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'` antes de `mvn` (o do sistema é JDK 8).

## Economia de tokens (obrigatório)
- **Saídas de comando mínimas:** mande o log para arquivo e mostre só o resumo/erro. Ex.: `mvn -B -ntp -q verify > $env:TEMP\mvn.log 2>&1; if ($LASTEXITCODE) { Select-String -Path $env:TEMP\mvn.log -Pattern 'ERROR|FAIL|Tests run:.*Fail' | Select-Object -First 40 }`. Docker: `docker compose up -d --build --quiet-pull > $env:TEMP\compose.log 2>&1`. CI: `gh pr checks <pr> --watch --interval 30 > $null; gh pr checks <pr>`.
- Leia só o necessário: `Grep`/`Glob` antes de `Read`; `Read` com offset/limit em arquivos grandes; não releia o que já leu; não liste diretórios inteiros sem necessidade.
- Testes: durante o desenvolvimento rode só os afetados (`mvn -q -Dtest=Classe test`); a suíte completa roda uma vez, no validador.
- Relatórios de agentes curtos e objetivos (sem colar logs).

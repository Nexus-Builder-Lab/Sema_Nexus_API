# SEMA ALERTA — API

Backend do **SEMA ALERTA**, plataforma **demonstrativa** de inteligência ambiental, gestão de demandas de fiscalização e gestão documental no contexto de **Mato Grosso**.

> ⚠️ **AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.** Sem integração com sistemas da SEMA-MT ou outros sistemas institucionais. Nada aqui tem valor oficial.

Este repositório também concentra a **infraestrutura e a documentação transversais** do projeto (Docker Compose da demonstração, scripts, documentação, monitoramento diário). O frontend está em [Sema_Nexus_Web](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web).

## Situação

Matriz conferida contra o código e **validada em execução** no par API `caa0da6` + Web `03494762` (mais o diff local
das melhorias do relatório técnico, ainda sem commit) em 20/11/2026 — evidências em
[docs/VALIDACAO_INTEGRADA.md](docs/VALIDACAO_INTEGRADA.md). **Implementado** = no código e exercitado contra a API real;
**Parcial** = no código, sem validação completa; **Planejado** = ainda não existe.

| Capacidade | Status |
|---|---|
| Infra: Dockerfile não-root, `docker-compose.yml` (Oracle 23ai Free + API + Web + Ollama no profile `ia`), scripts `demo-up`/`demo-down`/`demo-reset`, CI (`ci`, `compose-smoke`, `oracle-it`) | **Implementado** |
| Core: JWT/BCrypt, 5 perfis com autorização por método na API, cartões de demonstração (`/api/auth/demo/{perfil}`, só com `demo=true`), usuários, setores, equipes, auditoria, notificações | **Implementado** — 401/403 por perfil validados em execução |
| Oracle + Flyway (schema só por migration), seed demonstrativo só em `demo`/`dev`, trava do usuário de teste | **Implementado** |
| Flux: cadastro, listagem, detalhes e histórico; triagem, distribuição, execução, atividades, fiscalização, conclusão e devolução; prazos, produtividade e relatórios (CSV) | **Implementado** — fluxo login → demanda → triagem validado em execução |
| Intelligence: resumo, série mensal, por município/bioma/categoria/situação, mapa; camada de referência com dados públicos do INPE (identificados) | **Implementado** |
| DocIA: biblioteca com busca **textual**, upload (pdf, txt, md, csv, docx), vínculo à demanda, download, comparação de documentos (regras, sem IA) | **Implementado** — upload + vínculo validados em execução |
| Agente de Triagem (sugestão; aplicação só por uma pessoa) e Assistente DocIA (citações conferidas pela API, abstenção, aderência textual) com IA local (Ollama) e modo demonstrativo identificado | **Implementado** — validados em execução, inclusive com o modelo parado |
| Agentes: catálogo de erros públicos das ferramentas, orçamento total (tempo e chamadas), auditoria com latência e chamadas, avaliação adversarial (modelo simulado) | **Implementado** (20/11/2026) |
| Painel do sistema (`/api/sistema/painel`, só ADMIN): saúde, agentes, perfis e integrações | **Implementado** |
| Provedor Anthropic (Claude) para os agentes | **Parcial** — no código e em testes unitários; não validado com chave real (decisão de não ter custo) |
| Validação integrada automatizada (skill `validar-integracao-sema`) | **Parcial** — roteiro manual/local; não roda no CI |
| Busca semântica ou híbrida na DocIA | **Planejado** — só depois de comparar a qualidade com a busca textual atual |
| Hooks do Claude Code (proteger volumes, `.env` e migrations) e servidor MCP próprio (consultas autenticadas) | **Planejado** — hoje há permissões allow/deny/ask, não hooks; sem MCP próprio |

Acompanhamento: [GitHub Project — SEMA ALERTA — Desenvolvimento](https://github.com/orgs/Nexus-Builder-Lab/projects/1) · [docs/SPRINTS.md](docs/SPRINTS.md)

## Tecnologias

Java 21 · Spring Boot 3.5 · Spring Security (JWT + BCrypt) · Spring Data JPA · **Oracle Database 23ai Free** · Flyway · springdoc-openapi · JUnit 5 · Mockito · Testcontainers · Maven · Docker Compose · GitHub Actions

## Executar via Docker (Implementado — Issues #2, #8 e Web#2)

```bash
git clone https://github.com/Nexus-Builder-Lab/Sema_Nexus_API.git
git clone https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web.git
cd Sema_Nexus_API
cp .env.example .env              # Windows: Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

- Edite o `.env` (senhas e `JWT_SECRET` de 32+ caracteres) antes do `up`; se 8080, 3000 ou 1521 estiverem em uso, troque `API_PORT`, `WEB_PORT` ou `ORACLE_PORT`.
- Aplicação: http://127.0.0.1:3000 · API: http://127.0.0.1:8080 · Swagger: http://127.0.0.1:8080/swagger-ui.html (portas `WEB_PORT`/`API_PORT` do `.env`)
- Atalho no Windows: `.\scripts\demo-up.ps1` sobe Oracle + API + Web e espera os healthchecks.

> **Use `127.0.0.1`, não `localhost`.** Em alguns Windows, `localhost` resolve para IPv6 (`::1`) e cai em outro serviço que usa as mesmas portas. As portas abaixo são as padrão do `.env.example`; se você trocou `API_PORT`/`WEB_PORT` no `.env` (ex.: `API_PORT=18081`), use as suas.

- Login de teste (**somente demonstração**): `admin@admin.com` / `12345678`
- Parar sem perder dados: `docker compose down` (nunca `-v`, que apaga o banco e os uploads).
- Requisitos: Docker com Compose v2, RAM de 8 GB+ no host (~2 GB para o Oracle), ~12 GB de disco; 1ª subida de 1 a 3 min (mais o download das imagens).

Passo a passo completo, requisitos, Windows/WSL2, Apple Silicon, reset e solução de problemas: [docs/DOCKER.md](docs/DOCKER.md).

## Executar localmente sem Docker

Requisitos: **JDK 21** e Maven 3.9. O Maven usa o JDK do `JAVA_HOME` (não o do `PATH`); se ele apontar para JDK 8, o build para com a mensagem "SEMA ALERTA exige JDK 21".

```powershell
# Windows (só na sessão atual)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
mvn -B verify                                          # build + testes
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # http://127.0.0.1:8080
```

A API precisa do **Oracle** (Issue #3): na primeira subida o Flyway cria o schema e carrega os 142 municípios; nas seguintes, nada muda. Se preferir, suba só o Oracle do compose (`docker compose up -d oracle`) ou avulso e informe a conexão por variáveis de ambiente (escolha a senha você mesmo; nunca a versione):

```powershell
docker run -d --name sema-oracle -p 1521:1521 -e ORACLE_PASSWORD=<senha> -e APP_USER=SEMANEXUS -e APP_USER_PASSWORD=<senha> gvenzl/oracle-free:23-slim-faststart
$env:SPRING_DATASOURCE_URL = 'jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1'   # padrão do profile dev
$env:SPRING_DATASOURCE_USERNAME = 'SEMANEXUS'
$env:SPRING_DATASOURCE_PASSWORD = '<senha>'
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Com o compose: `docker compose up -d oracle` e as variáveis do `.env` exportadas no shell (a porta do host é `ORACLE_PORT`, padrão 1521).

Testes: `mvn -B verify` roda só os testes **sem** Oracle. Os testes de integração com Oracle real (Testcontainers, tag `oracle`, exigem Docker) rodam com `mvn -B verify -Poracle`.

| URL | O que é |
|---|---|
| http://127.0.0.1:8080/actuator/health | Healthcheck (público) |
| http://127.0.0.1:8080/swagger-ui.html | Swagger UI (público; desligado em `prod`) |
| http://127.0.0.1:8080/v3/api-docs | Contrato OpenAPI (público) |

Profiles: `dev` (padrão), `demo` (Docker/apresentação), `prod` (sem seed e sem Swagger), `openapi` (só para o CI exportar o contrato, sem banco).

## Usuário de teste

O seed demonstrativo cria `admin@admin.com` / `12345678` (Administrador) **somente** nos profiles `demo` e `dev`, com senha gravada apenas como hash BCrypt. Se a API subir em outro profile (por exemplo `SPRING_PROFILES_ACTIVE=prod`) e esse usuário existir no banco, ela **se recusa a iniciar**: o `TestUserProfileGuard` registra um log `ERROR` (sem senha) e lança `IllegalStateException`. Para resolver, remova o usuário do banco ou inicie com o profile `demo`/`dev`. A trava não atua no profile `openapi` (usado só pelo CI, sem banco). Demais usuários demonstrativos: [docs/DEMO.md](docs/DEMO.md).

## Documentação

| Documento | Conteúdo |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Arquitetura, stack, pastas, fluxo de status, perfis, decisões |
| [REQUIREMENTS.md](docs/REQUIREMENTS.md) | Requisitos por módulo com P0/P1/P2 e status |
| [DATABASE.md](docs/DATABASE.md) | Modelo Oracle, convenções, seed e reset |
| [API.md](docs/API.md) | Padrões REST e endpoints |
| [DEVELOPMENT.md](docs/DEVELOPMENT.md) | Branches, PRs, status do Project, CI, guardrails |
| [SPRINTS.md](docs/SPRINTS.md) | Sprints, backlog, capacidade, caminho crítico, cortes |
| [DOCKER.md](docs/DOCKER.md) | Subida, parada, reset, requisitos |
| [DEMO.md](docs/DEMO.md) | Credenciais demonstrativas e roteiro de 10 minutos |
| [DEFINITION_OF_DONE.md](docs/DEFINITION_OF_DONE.md) | Critérios de conclusão |
| [PROJECT_HEALTH.md](docs/PROJECT_HEALTH.md) | Monitoramento diário |
| [AGENTES_IA.md](docs/AGENTES_IA.md) | Agentes de IA no sistema (Triagem, DocIA): provedores, orçamento, catálogo de erros, aderência textual |
| [VALIDACAO_INTEGRADA.md](docs/VALIDACAO_INTEGRADA.md) | Validação integrada API + Web no mesmo par de commits (relatório técnico): fluxo, perfis, falha, avaliação |
| [AGENTES.md](docs/AGENTES.md) | Subagentes especializados e fluxo multiagente |
| [AUTOPILOT.md](docs/AUTOPILOT.md) | Desenvolvimento autônomo: como iniciar, acompanhar e parar |
| [DIARIO_DESENVOLVIMENTO.md](docs/DIARIO_DESENVOLVIMENTO.md) | Histórico de tudo o que foi feito, decidido e testado |
| [CLAUDE.md](CLAUDE.md) | Regras permanentes para sessões com Claude Code |

## Contribuição

Toda mudança parte de uma Issue. Branch `feature/GH-<n>`, commits `GH-<n>: ...`, PR com `Closes #<n>`. Ver [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

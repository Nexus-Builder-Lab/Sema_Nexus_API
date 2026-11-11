# SEMA NEXUS — API

Backend do **SEMA NEXUS**, plataforma **demonstrativa** de inteligência ambiental, gestão de demandas de fiscalização e gestão documental no contexto de **Mato Grosso**.

> ⚠️ **AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.** Sem integração com sistemas da SEMA-MT ou outros sistemas institucionais. Nada aqui tem valor oficial.

Este repositório também concentra a **infraestrutura e a documentação transversais** do projeto (Docker Compose da demonstração, scripts, documentação, monitoramento diário). O frontend está em [Sema_Nexus_Web](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web).

## Situação

| Item | Status |
|---|---|
| Planejamento, backlog (3 sprints), Project, CI, templates, documentação | **Implementado** (10/11/2026) |
| Esqueleto Spring Boot: profiles, erros RFC 9457, paginação, OpenAPI/Swagger, health (Issue #1) | **Implementado** |
| Imagem Docker da API: Dockerfile multi-stage, usuário não-root, healthcheck (Issue #2) | **Implementado** |
| Conexão Oracle, Flyway e schema do Core com os 142 municípios de MT (Issue #3) | **Implementado** |
| Seed demonstrativo base: usuário de teste, usuários, setores e equipes fictícios, só em `demo`/`dev` (Issue #5) | **Implementado** |
| Trava de inicialização do usuário de teste fora de `demo`/`dev` (Issue #6) | **Implementado** |
| `docker-compose.yml` (Oracle Free + API + Web), `.env.example`, healthchecks e volumes (Issue #8) | **Implementado** |
| `docs/DOCKER.md` completo (requisitos, Windows/WSL2, Apple Silicon, problemas comuns) e seção de execução do README (Issue #10) | **Implementado** |
| Scripts `demo-up`/`demo-down`/`demo-reset` (`.sh` e `.ps1`) e `Makefile` (Issue #9) | **Implementado** |
| Demandas do Flux: cadastro, edição, listagem com filtros, detalhes, histórico e exclusão lógica (Issue #11) | **Implementado** |
| Triagem, distribuição e fiscalização do Flux (#12, #13), Intelligence, DocIA | **Planejado** — Sprints 2 e 3 |

Acompanhamento: [GitHub Project — SEMA NEXUS — Desenvolvimento](https://github.com/orgs/Nexus-Builder-Lab/projects/1) · [docs/SPRINTS.md](docs/SPRINTS.md)

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
- Aplicação: http://localhost:3000 · API: http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui.html
- Login de teste (**somente demonstração**): `admin@admin.com` / `12345678`
- Parar sem perder dados: `docker compose down` (nunca `-v`, que apaga o banco e os uploads).
- Requisitos: Docker com Compose v2, RAM de 8 GB+ no host (~2 GB para o Oracle), ~12 GB de disco; 1ª subida de 1 a 3 min (mais o download das imagens).

Passo a passo completo, requisitos, Windows/WSL2, Apple Silicon, reset e solução de problemas: [docs/DOCKER.md](docs/DOCKER.md).

## Executar localmente sem Docker

Requisitos: **JDK 21** e Maven 3.9. O Maven usa o JDK do `JAVA_HOME` (não o do `PATH`); se ele apontar para JDK 8, o build para com a mensagem "SEMA NEXUS exige JDK 21".

```powershell
# Windows (só na sessão atual)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
mvn -B verify                                          # build + testes
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # http://localhost:8080
```

A API precisa do **Oracle** (Issue #3): na primeira subida o Flyway cria o schema e carrega os 142 municípios; nas seguintes, nada muda. Se preferir, suba só o Oracle do compose (`docker compose up -d oracle`) ou avulso e informe a conexão por variáveis de ambiente (escolha a senha você mesmo; nunca a versione):

```powershell
docker run -d --name sema-oracle -p 1521:1521 -e ORACLE_PASSWORD=<senha> -e APP_USER=SEMANEXUS -e APP_USER_PASSWORD=<senha> gvenzl/oracle-free:23-slim-faststart
$env:SPRING_DATASOURCE_URL = 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'   # padrão do profile dev
$env:SPRING_DATASOURCE_USERNAME = 'SEMANEXUS'
$env:SPRING_DATASOURCE_PASSWORD = '<senha>'
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Com o compose: `docker compose up -d oracle` e as variáveis do `.env` exportadas no shell (a porta do host é `ORACLE_PORT`, padrão 1521).

Testes: `mvn -B verify` roda só os testes **sem** Oracle. Os testes de integração com Oracle real (Testcontainers, tag `oracle`, exigem Docker) rodam com `mvn -B verify -Poracle`.

| URL | O que é |
|---|---|
| http://localhost:8080/actuator/health | Healthcheck (público) |
| http://localhost:8080/swagger-ui.html | Swagger UI (público; desligado em `prod`) |
| http://localhost:8080/v3/api-docs | Contrato OpenAPI (público) |

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
| [AGENTES.md](docs/AGENTES.md) | Subagentes especializados e fluxo multiagente |
| [AUTOPILOT.md](docs/AUTOPILOT.md) | Desenvolvimento autônomo: como iniciar, acompanhar e parar |
| [DIARIO_DESENVOLVIMENTO.md](docs/DIARIO_DESENVOLVIMENTO.md) | Histórico de tudo o que foi feito, decidido e testado |
| [CLAUDE.md](CLAUDE.md) | Regras permanentes para sessões com Claude Code |

## Contribuição

Toda mudança parte de uma Issue. Branch `feature/GH-<n>`, commits `GH-<n>: ...`, PR com `Closes #<n>`. Ver [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

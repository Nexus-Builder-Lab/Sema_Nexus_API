# SEMA NEXUS — API

Backend do **SEMA NEXUS**, plataforma **demonstrativa** de inteligência ambiental, gestão de demandas de fiscalização e gestão documental no contexto de **Mato Grosso**.

> ⚠️ **AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.** Sem integração com sistemas da SEMA-MT ou outros sistemas institucionais. Nada aqui tem valor oficial.

Este repositório também concentra a **infraestrutura e a documentação transversais** do projeto (Docker Compose da demonstração, scripts, documentação, monitoramento diário). O frontend está em [Sema_Nexus_Web](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web).

## Situação

| Item | Status |
|---|---|
| Planejamento, backlog (3 sprints), Project, CI, templates, documentação | **Implementado** (10/11/2026) |
| Esqueleto Spring Boot: profiles, erros RFC 9457, paginação, OpenAPI/Swagger, health (Issue #1) | **Implementado** |
| Conexão Oracle, autenticação, seed, Dockerfile, docker-compose | **Planejado** — Sprint 1 (Issues #2–#10) |
| Módulos Flux, Intelligence, DocIA | **Planejado** — Sprints 2 e 3 |

Acompanhamento: [GitHub Project — SEMA NEXUS — Desenvolvimento](https://github.com/orgs/Nexus-Builder-Lab/projects/1) · [docs/SPRINTS.md](docs/SPRINTS.md)

## Tecnologias

Java 21 · Spring Boot 3.5 · Spring Security (JWT + BCrypt) · Spring Data JPA · **Oracle Database 23ai Free** · Flyway · springdoc-openapi · JUnit 5 · Mockito · Testcontainers · Maven · Docker Compose · GitHub Actions

## Executar via Docker (após as Issues #2, #8 e Web#2)

```bash
git clone https://github.com/Nexus-Builder-Lab/Sema_Nexus_API.git
git clone https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web.git
cd Sema_Nexus_API
cp .env.example .env              # Windows: Copy-Item .env.example .env
docker compose up -d --build
docker compose ps
```

- Aplicação: http://localhost:3000 · API: http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui.html
- Login de teste (**somente demonstração**): `admin@admin.com` / `12345678`

Detalhes, requisitos e reset: [docs/DOCKER.md](docs/DOCKER.md).

## Executar localmente sem Docker

Requisitos: **JDK 21** e Maven 3.9. O Maven usa o JDK do `JAVA_HOME` (não o do `PATH`); se ele apontar para JDK 8, o build para com a mensagem "SEMA NEXUS exige JDK 21".

```powershell
# Windows (só na sessão atual)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
mvn -B verify                                          # build + testes
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # http://localhost:8080
```

Até a Issue #3, a API sobe **sem banco** (health, Swagger e o padrão de erros já funcionam; as rotas `/api/**` respondem 401). A partir da Issue #3 será preciso o Oracle: `docker compose up -d oracle` e as variáveis do `.env` exportadas no shell.

| URL | O que é |
|---|---|
| http://localhost:8080/actuator/health | Healthcheck (público) |
| http://localhost:8080/swagger-ui.html | Swagger UI (público; desligado em `prod`) |
| http://localhost:8080/v3/api-docs | Contrato OpenAPI (público) |

Profiles: `dev` (padrão), `demo` (Docker/apresentação), `prod` (sem seed e sem Swagger), `openapi` (só para o CI exportar o contrato, sem banco).

## Usuário de teste

O seed demonstrativo cria `admin@admin.com` / `12345678` (Administrador) **somente** nos profiles `demo` e `dev`, com senha gravada apenas como hash BCrypt. Se a API subir em outro profile e esse usuário existir, ela se recusa a iniciar. Demais usuários demonstrativos: [docs/DEMO.md](docs/DEMO.md).

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
| [CLAUDE.md](CLAUDE.md) | Regras permanentes para sessões com Claude Code |

## Contribuição

Toda mudança parte de uma Issue. Branch `feature/GH-<n>`, commits `GH-<n>: ...`, PR com `Closes #<n>`. Ver [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

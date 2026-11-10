# SEMA NEXUS API — Diário de desenvolvimento

> Registro cronológico de tudo o que foi feito, decidido e testado neste repositório. Uma entrada por Issue concluída (incluída no próprio PR). Resultados de teste são sempre os **reais** da execução.
> Erros que o autopilot não conseguiu corrigir ficam em `C:\SEMANEXUS\autopilot\RELATORIO_ERROS.md` e nas Issues com a label `autopilot-falhou`.

## Modelo de entrada

```
## AAAA-MM-DD HH:MM — GH-<n> · <título curto>
- **PR:** #<pr> · **Status:** mesclado | aguardando dependência | falhou
- **Executado por:** autopilot (sessão API) | <pessoa>
- **O que foi feito:** ...
- **Decisões técnicas:** ...
- **Arquivos principais:** ...
- **Testes:** comando → resultado real (ex.: `mvn -B verify` → 14 testes, 0 falhas)
- **Docker:** validado | não se aplica (motivo)
- **Pendências / observações para as próximas Issues:** ...
```

---

## 2026-11-10 — Planejamento e organização do repositório
- **Executado por:** Claude Code, com aprovação do proprietário.
- **O que foi feito:** diagnóstico (hub `Sema_Nexus` inexistente — renomeado para `Sema_Nexus_Web`; protótipo em `FabricioJorge0/prototipo_SEMA_NEXUS`); decisão de não ter hub (infra e docs transversais ficam aqui); backlog de 49 Issues (30 API, 19 Web) em 3 sprints sem fins de semana, implementação até 25/11 e revisão em 26/11; GitHub Project com campos, Iteration de sprints e views por sprint; 85 dependências nativas; labels; CLAUDE.md, templates, CI, project-health e documentação.
- **Decisões técnicas:** Flyway (não Liquibase); Spring Boot 3.5; títulos `[SEMA_NEXUS_API][MÓDULO]`; status do protótipo substituídos pelos 7 da especificação.
- **Limitações registradas:** plano Free + repositórios privados → sem rulesets/branch protection e sem secret scanning; automações do Project precisam de configuração manual.

## 2026-11-10 — GH-1 · Esqueleto Spring Boot com padrões REST, erros e OpenAPI
- **PR:** #31 · **Status:** mesclado (squash `c4dc1c2`)
- **Executado por:** Claude Code, merge autorizado pelo proprietário.
- **O que foi feito:** projeto Spring Boot 3.5.16 / Java 21 / Maven; pacotes por módulo; profiles `dev` (padrão), `demo`, `prod`, `openapi`; `GlobalExceptionHandler` em `problem+json` (RFC 9457) com `errors[]` e sem stacktrace; `PageResponse`; `OpenApiConfig` com bearer JWT; segurança mínima (health e docs públicos, resto 401); Actuator só com health.
- **Decisões técnicas:** springdoc 2.10.0 (compilado contra Boot 3.5.16); `maven-enforcer` exigindo JDK 21; exclusão temporária de DataSource/JPA/Flyway até a #3; o handler repassa `AccessDeniedException`/`AuthenticationException` ao Spring Security (evita 500 no lugar de 401/403).
- **Arquivos principais:** `pom.xml`, `common/exception/GlobalExceptionHandler.java`, `common/dto/PageResponse.java`, `config/OpenApiConfig.java`, `security/SecurityConfig.java`, `application*.yml`.
- **Testes:** `mvn -B -ntp verify` → 14 testes, 0 falhas. Jar com profile `dev`: health UP, `/v3/api-docs` 200, Swagger 200, `/api/demandas` 401, sem senha gerada no log. Export do OpenAPI pelo profile `openapi` sem banco. CI do PR: 4 jobs verdes, artefatos `openapi` e `surefire-reports`.
- **Docker:** não se aplica (Dockerfile é a #2).
- **Pendências:** #3 remove as exclusões TEMPORÁRIAS em `application.yml`; #4 substitui a `SecurityConfig` mínima. Ambiente local: `JAVA_HOME` do sistema aponta para JDK 8; porta 8080 do host ocupada por outro serviço (usar 18080).

## 2026-11-10 12:40 — GH-2 · Dockerfile multi-stage, .dockerignore e healthcheck da API
- **PR:** (este PR) · **Status:** mesclado se o CI ficar verde
- **Executado por:** autopilot (sessão API)
- **O que foi feito:** `Dockerfile` multi-stage (build `maven:3.9-eclipse-temurin-21` com `dependency:go-offline` antes de copiar `src/`; runtime `eclipse-temurin:21-jre-alpine`), usuário não-root `semanexus` (UID/GID 10001), `/data/uploads` com dono do usuário, `EXPOSE 8080`, `JAVA_OPTS` por variável, `HEALTHCHECK` com `wget` em `/actuator/health`; `.dockerignore`.
- **Decisões técnicas:** `ENTRYPOINT` com `exec` para a JVM ser PID 1 (recebe SIGTERM do `docker compose down`); `STORAGE_DIR=/data/uploads` e `SERVER_PORT=8080` como padrões não sensíveis; `/data/uploads` declarado como `VOLUME`; `.dockerignore` também exclui `docs/`, `scripts/`, `.github/`, `.claude/` e chaves (contexto só com `pom.xml` e `src/`).
- **Arquivos principais:** `Dockerfile`, `.dockerignore`, `docs/DOCKER.md` (seção "Imagem da API"), `README.md`.
- **Testes:** `mvn -B -ntp verify` → 14 testes, 0 falhas, BUILD SUCCESS. `docker build -t sema-nexus-api:gh2 .` → sucesso. `docker run --rm --entrypoint id` → `uid=10001(semanexus) gid=10001(semanexus)`. Container ficou `healthy`; `/actuator/health` → `{"status":"UP",...}`; processo Java como `semanexus`. `docker history --no-trunc` e `Config.Env` sem senhas/tokens (única ocorrência de "key" é o `gpg --recv-keys` público da imagem base). Tamanho: soma das camadas ≈ 283 MB descompactada (jar 70,7 MB + JRE 165 MB + base), 138 MB compactada; o `docker image ls` do containerd mostra 421 MB porque soma o conteúdo compactado e o descompactado.
- **Docker:** imagem validada isoladamente (o `docker-compose.yml` ainda não existe — Issue #8).
- **Pendências / observações:** #8 deve montar o volume `uploads` em `/data/uploads` e passar segredos só pelo `.env`; com o Oracle habilitado (#3) o healthcheck passa a refletir o estado do banco.

## 2026-11-10 13:10 — GH-3 · Conexão Oracle, Flyway e schema do Core
- **PR:** (este PR) · **Status:** mesclado se o CI ficar verde
- **Executado por:** autopilot (sessão API)
- **O que foi feito:** removidas as exclusões temporárias de DataSource/JPA/Flyway; datasource só por `SPRING_DATASOURCE_*` (Hikari, `ddl-auto=validate`); `V1__core.sql` (`MUNICIPIO`, `SETOR`, `USUARIO`, `EQUIPE`, `EQUIPE_MEMBRO`, `AUDITORIA` com PK identity, FKs, CHECKs, UKs e índices); `V2__municipios_mt.sql` com os 142 municípios de MT gerados a partir de `data/geo-mt.js` do protótipo; entidades e repositories em `core/{municipio,setor,usuario,equipe,auditoria}`; Testcontainers (tag `oracle`, profile Maven `-Poracle`).
- **Decisões técnicas:** booleanos com o tipo nativo `BOOLEAN` do Oracle 23ai (o Hibernate valida sem conversor); `MUNICIPIO.ID` = código IBGE com CHECK de faixa da UF 51 e CHECK do retângulo de MT nas coordenadas; e-mail sempre minúsculo (CHECK no banco + normalização na entidade); `AUDITORIA.ENTIDADE_ID` como `VARCHAR2(64)` e `USUARIO_ID` sem associação JPA (log append-only, `@Immutable`); `SemaNexusApplicationTests` soma o profile `openapi` para continuar sem banco (unitários não dependem de Oracle); a URL `localhost:1521/FREEPDB1` é padrão só no profile `dev` (não sensível).
- **Arquivos principais:** `src/main/resources/db/migration/V1__core.sql`, `V2__municipios_mt.sql`, `application.yml`, `application-dev.yml`, `core/**`, `pom.xml`, `CoreSchemaOracleTest.java`, `docs/DATABASE.md`, `README.md`.
- **Testes:** `mvn -B -ntp verify` → 16 testes, 0 falhas, BUILD SUCCESS. `mvn -B -ntp verify -Poracle` (Oracle 23ai Free via Testcontainers) → 4 testes, 0 falhas: V1 e V2 aplicadas e `migrate()` seguinte executa 0; `COUNT(*) MUNICIPIO` = 142 e 0 fora da faixa 51xxxxx; contexto sobe com `validate`; persistência de setor/usuário/equipe/membro/auditoria; CHECKs de perfil, bioma e UF e UK de e-mail rejeitam dados inválidos. Jar contra Oracle avulso (porta 15210) com variáveis de ambiente: 1ª subida "Successfully applied 2 migrations", 2ª subida "Schema is up to date. No migration necessary", health `UP` nas duas.
- **Docker:** não se aplica ao compose (o `docker-compose.yml` é a Issue #8); Oracle validado em container avulso e via Testcontainers.
- **Pendências / observações:** #8 deve passar `SPRING_DATASOURCE_*` do `.env` e o healthcheck da API agora depende do banco; job agendado dos testes `oracle` é a #23 (P1). Timestamps usam `LocalDateTime` (fuso da JVM) — no compose convém `TZ=America/Cuiaba`.

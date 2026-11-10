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

# SEMA NEXUS API â€” DiÃ¡rio de desenvolvimento

> Registro cronolÃ³gico de tudo o que foi feito, decidido e testado neste repositÃ³rio. Uma entrada por Issue concluÃ­da (incluÃ­da no prÃ³prio PR). Resultados de teste sÃ£o sempre os **reais** da execuÃ§Ã£o.
> Erros que o autopilot nÃ£o conseguiu corrigir ficam em `C:\SEMANEXUS\autopilot\RELATORIO_ERROS.md` e nas Issues com a label `autopilot-falhou`.

## Modelo de entrada

```
## AAAA-MM-DD HH:MM â€” GH-<n> Â· <tÃ­tulo curto>
- **PR:** #<pr> Â· **Status:** mesclado | aguardando dependÃªncia | falhou
- **Executado por:** autopilot (sessÃ£o API) | <pessoa>
- **O que foi feito:** ...
- **DecisÃµes tÃ©cnicas:** ...
- **Arquivos principais:** ...
- **Testes:** comando â†’ resultado real (ex.: `mvn -B verify` â†’ 14 testes, 0 falhas)
- **Docker:** validado | nÃ£o se aplica (motivo)
- **PendÃªncias / observaÃ§Ãµes para as prÃ³ximas Issues:** ...
```

---

## 2026-11-10 â€” Planejamento e organizaÃ§Ã£o do repositÃ³rio
- **Executado por:** Claude Code, com aprovaÃ§Ã£o do proprietÃ¡rio.
- **O que foi feito:** diagnÃ³stico (hub `Sema_Nexus` inexistente â€” renomeado para `Sema_Nexus_Web`; protÃ³tipo em `FabricioJorge0/prototipo_SEMA_NEXUS`); decisÃ£o de nÃ£o ter hub (infra e docs transversais ficam aqui); backlog de 49 Issues (30 API, 19 Web) em 3 sprints sem fins de semana, implementaÃ§Ã£o atÃ© 25/11 e revisÃ£o em 26/11; GitHub Project com campos, Iteration de sprints e views por sprint; 85 dependÃªncias nativas; labels; CLAUDE.md, templates, CI, project-health e documentaÃ§Ã£o.
- **DecisÃµes tÃ©cnicas:** Flyway (nÃ£o Liquibase); Spring Boot 3.5; tÃ­tulos `[SEMA_NEXUS_API][MÃ“DULO]`; status do protÃ³tipo substituÃ­dos pelos 7 da especificaÃ§Ã£o.
- **LimitaÃ§Ãµes registradas:** plano Free + repositÃ³rios privados â†’ sem rulesets/branch protection e sem secret scanning; automaÃ§Ãµes do Project precisam de configuraÃ§Ã£o manual.

## 2026-11-10 â€” GH-1 Â· Esqueleto Spring Boot com padrÃµes REST, erros e OpenAPI
- **PR:** #31 Â· **Status:** mesclado (squash `c4dc1c2`)
- **Executado por:** Claude Code, merge autorizado pelo proprietÃ¡rio.
- **O que foi feito:** projeto Spring Boot 3.5.16 / Java 21 / Maven; pacotes por mÃ³dulo; profiles `dev` (padrÃ£o), `demo`, `prod`, `openapi`; `GlobalExceptionHandler` em `problem+json` (RFC 9457) com `errors[]` e sem stacktrace; `PageResponse`; `OpenApiConfig` com bearer JWT; seguranÃ§a mÃ­nima (health e docs pÃºblicos, resto 401); Actuator sÃ³ com health.
- **DecisÃµes tÃ©cnicas:** springdoc 2.10.0 (compilado contra Boot 3.5.16); `maven-enforcer` exigindo JDK 21; exclusÃ£o temporÃ¡ria de DataSource/JPA/Flyway atÃ© a #3; o handler repassa `AccessDeniedException`/`AuthenticationException` ao Spring Security (evita 500 no lugar de 401/403).
- **Arquivos principais:** `pom.xml`, `common/exception/GlobalExceptionHandler.java`, `common/dto/PageResponse.java`, `config/OpenApiConfig.java`, `security/SecurityConfig.java`, `application*.yml`.
- **Testes:** `mvn -B -ntp verify` â†’ 14 testes, 0 falhas. Jar com profile `dev`: health UP, `/v3/api-docs` 200, Swagger 200, `/api/demandas` 401, sem senha gerada no log. Export do OpenAPI pelo profile `openapi` sem banco. CI do PR: 4 jobs verdes, artefatos `openapi` e `surefire-reports`.
- **Docker:** nÃ£o se aplica (Dockerfile Ã© a #2).
- **PendÃªncias:** #3 remove as exclusÃµes TEMPORÃRIAS em `application.yml`; #4 substitui a `SecurityConfig` mÃ­nima. Ambiente local: `JAVA_HOME` do sistema aponta para JDK 8; porta 8080 do host ocupada por outro serviÃ§o (usar 18080).

## 2026-11-10 12:40 â€” GH-2 Â· Dockerfile multi-stage, .dockerignore e healthcheck da API
- **PR:** (este PR) Â· **Status:** mesclado se o CI ficar verde
- **Executado por:** autopilot (sessÃ£o API)
- **O que foi feito:** `Dockerfile` multi-stage (build `maven:3.9-eclipse-temurin-21` com `dependency:go-offline` antes de copiar `src/`; runtime `eclipse-temurin:21-jre-alpine`), usuÃ¡rio nÃ£o-root `semanexus` (UID/GID 10001), `/data/uploads` com dono do usuÃ¡rio, `EXPOSE 8080`, `JAVA_OPTS` por variÃ¡vel, `HEALTHCHECK` com `wget` em `/actuator/health`; `.dockerignore`.
- **DecisÃµes tÃ©cnicas:** `ENTRYPOINT` com `exec` para a JVM ser PID 1 (recebe SIGTERM do `docker compose down`); `STORAGE_DIR=/data/uploads` e `SERVER_PORT=8080` como padrÃµes nÃ£o sensÃ­veis; `/data/uploads` declarado como `VOLUME`; `.dockerignore` tambÃ©m exclui `docs/`, `scripts/`, `.github/`, `.claude/` e chaves (contexto sÃ³ com `pom.xml` e `src/`).
- **Arquivos principais:** `Dockerfile`, `.dockerignore`, `docs/DOCKER.md` (seÃ§Ã£o "Imagem da API"), `README.md`.
- **Testes:** `mvn -B -ntp verify` â†’ 14 testes, 0 falhas, BUILD SUCCESS. `docker build -t sema-nexus-api:gh2 .` â†’ sucesso. `docker run --rm --entrypoint id` â†’ `uid=10001(semanexus) gid=10001(semanexus)`. Container ficou `healthy`; `/actuator/health` â†’ `{"status":"UP",...}`; processo Java como `semanexus`. `docker history --no-trunc` e `Config.Env` sem senhas/tokens (Ãºnica ocorrÃªncia de "key" Ã© o `gpg --recv-keys` pÃºblico da imagem base). Tamanho: soma das camadas â‰ˆ 283 MB descompactada (jar 70,7 MB + JRE 165 MB + base), 138 MB compactada; o `docker image ls` do containerd mostra 421 MB porque soma o conteÃºdo compactado e o descompactado.
- **Docker:** imagem validada isoladamente (o `docker-compose.yml` ainda nÃ£o existe â€” Issue #8).
- **PendÃªncias / observaÃ§Ãµes:** #8 deve montar o volume `uploads` em `/data/uploads` e passar segredos sÃ³ pelo `.env`; com o Oracle habilitado (#3) o healthcheck passa a refletir o estado do banco.

## 2026-11-10 13:10 â€” GH-3 Â· ConexÃ£o Oracle, Flyway e schema do Core
- **PR:** (este PR) Â· **Status:** mesclado se o CI ficar verde
- **Executado por:** autopilot (sessÃ£o API)
- **O que foi feito:** removidas as exclusÃµes temporÃ¡rias de DataSource/JPA/Flyway; datasource sÃ³ por `SPRING_DATASOURCE_*` (Hikari, `ddl-auto=validate`); `V1__core.sql` (`MUNICIPIO`, `SETOR`, `USUARIO`, `EQUIPE`, `EQUIPE_MEMBRO`, `AUDITORIA` com PK identity, FKs, CHECKs, UKs e Ã­ndices); `V2__municipios_mt.sql` com os 142 municÃ­pios de MT gerados a partir de `data/geo-mt.js` do protÃ³tipo; entidades e repositories em `core/{municipio,setor,usuario,equipe,auditoria}`; Testcontainers (tag `oracle`, profile Maven `-Poracle`).
- **DecisÃµes tÃ©cnicas:** booleanos com o tipo nativo `BOOLEAN` do Oracle 23ai (o Hibernate valida sem conversor); `MUNICIPIO.ID` = cÃ³digo IBGE com CHECK de faixa da UF 51 e CHECK do retÃ¢ngulo de MT nas coordenadas; e-mail sempre minÃºsculo (CHECK no banco + normalizaÃ§Ã£o na entidade); `AUDITORIA.ENTIDADE_ID` como `VARCHAR2(64)` e `USUARIO_ID` sem associaÃ§Ã£o JPA (log append-only, `@Immutable`); `SemaNexusApplicationTests` soma o profile `openapi` para continuar sem banco (unitÃ¡rios nÃ£o dependem de Oracle); a URL `localhost:1521/FREEPDB1` Ã© padrÃ£o sÃ³ no profile `dev` (nÃ£o sensÃ­vel).
- **Arquivos principais:** `src/main/resources/db/migration/V1__core.sql`, `V2__municipios_mt.sql`, `application.yml`, `application-dev.yml`, `core/**`, `pom.xml`, `CoreSchemaOracleTest.java`, `docs/DATABASE.md`, `README.md`.
- **Testes:** `mvn -B -ntp verify` â†’ 16 testes, 0 falhas, BUILD SUCCESS. `mvn -B -ntp verify -Poracle` (Oracle 23ai Free via Testcontainers) â†’ 4 testes, 0 falhas: V1 e V2 aplicadas e `migrate()` seguinte executa 0; `COUNT(*) MUNICIPIO` = 142 e 0 fora da faixa 51xxxxx; contexto sobe com `validate`; persistÃªncia de setor/usuÃ¡rio/equipe/membro/auditoria; CHECKs de perfil, bioma e UF e UK de e-mail rejeitam dados invÃ¡lidos. Jar contra Oracle avulso (porta 15210) com variÃ¡veis de ambiente: 1Âª subida "Successfully applied 2 migrations", 2Âª subida "Schema is up to date. No migration necessary", health `UP` nas duas.
- **Docker:** nÃ£o se aplica ao compose (o `docker-compose.yml` Ã© a Issue #8); Oracle validado em container avulso e via Testcontainers.
- **PendÃªncias / observaÃ§Ãµes:** #8 deve passar `SPRING_DATASOURCE_*` do `.env` e o healthcheck da API agora depende do banco; job agendado dos testes `oracle` Ã© a #23 (P1). Timestamps usam `LocalDateTime` (fuso da JVM) â€” no compose convÃ©m `TZ=America/Cuiaba`.

## 2026-11-10 13:45 â€” GH-4 Â· AutenticaÃ§Ã£o JWT com BCrypt, autorizaÃ§Ã£o por perfil e CORS
- **PR:** (este PR) Â· **Status:** mesclado se o CI ficar verde
- **Executado por:** autopilot (sessÃ£o API)
- **O que foi feito:** `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` e `GET /api/auth/config` (pÃºblico, `demo` conforme profile); `JwtService` HS256 (Nimbus JOSE+JWT, expiraÃ§Ã£o 8 h) e `JwtConfig` (`JWT_SECRET` â‰¥ 32 bytes, obrigatÃ³rio fora de `dev`/`openapi`); `JwtAuthFilter` (relÃª o usuÃ¡rio no banco a cada requisiÃ§Ã£o); `SecurityConfig` stateless com `@EnableMethodSecurity`, BCrypt e CORS sÃ³ no profile `dev` (`http://localhost:5173`); 401/403 em `problem+json`; `AuditService` mÃ­nimo (LOGIN/LOGOUT) e `ULTIMO_ACESSO` atualizado.
- **DecisÃµes tÃ©cnicas:** biblioteca `spring-security-oauth2-jose` (versÃ£o gerenciada pelo Boot; traz o Nimbus) em vez de adicionar jjwt; token sÃ³ com `sub`/`perfil`, e perfil/ativo vÃªm do banco (desativar usuÃ¡rio invalida tokens na hora); login confere a senha **antes** de revelar usuÃ¡rio inativo (403) e executa BCrypt contra hash descartÃ¡vel quando o e-mail nÃ£o existe (mensagem 401 genÃ©rica e custo equivalente); DTOs de login mascaram senha/token no `toString()`; `JwtAuthFilter` nÃ£o Ã© `@Component` (evita registro duplicado como filtro de servlet); o profile `openapi` agora mantÃ©m JPA/repositÃ³rios (a autenticaÃ§Ã£o depende deles) mas sem abrir conexÃ£o (Flyway off, `allow_jdbc_metadata_access=false`, Hikari sem conexÃ£o inicial, dialeto explÃ­cito) â€” o CI continua exportando o contrato sem banco.
- **Arquivos principais:** `security/{SecurityConfig,JwtService,JwtConfig,JwtProperties,JwtAuthFilter,ProblemDetailSecurityHandlers,UsuarioAutenticado}.java`, `core/auth/**`, `core/auditoria/AuditService.java`, `common/exception/{CredenciaisInvalidas,UsuarioInativo}Exception.java` + `GlobalExceptionHandler`, `application.yml`, `application-openapi.yml`, `pom.xml`, `docs/API.md`, `docs/ARCHITECTURE.md`.
- **Testes:** `mvn -B -ntp verify` â†’ BUILD SUCCESS (testes unitÃ¡rios `JwtServiceTest`, `JwtConfigTest`, `AuthServiceTest`, `AuditServiceTest`, `@WebMvcTest` `AuthControllerWebMvcTest`, `CorsProfileTest` e os jÃ¡ existentes). `mvn -B -ntp verify -Poracle` (Oracle 23ai Free via Testcontainers) â†’ 8 testes, 0 falhas, incluindo `AuthOracleTest` (login/me/logout ponta a ponta, `AUDITORIA` com LOGIN e LOGOUT, `ULTIMO_ACESSO`, 401 genÃ©rico, 403 inativo, token de usuÃ¡rio desativado â†’ 401, nenhuma senha/token na auditoria). Jar com profile `openapi` (sem banco) na porta 18080: health UP, `/v3/api-docs` lista os 4 endpoints `/api/auth/*`, `/api/auth/me` sem token â†’ 401.
- **Docker:** `docker-compose.yml` ainda nÃ£o existe (Issue #8); Oracle validado via Testcontainers.
- **PendÃªncias / observaÃ§Ãµes:** o usuÃ¡rio `admin@admin.com` e a trava do profile vÃªm das Issues #5 e #6; revogaÃ§Ã£o de token no logout Ã© P2 (o token Ã© descartado no cliente).

## 2026-11-10 13:50 â€” GH-5 Â· Seed demonstrativo base com usuÃ¡rio de teste (somente demo/dev)
- **PR:** (este PR) Â· **Status:** mesclado se o CI ficar verde
- **Executado por:** autopilot (sessÃ£o API)
- **O que foi feito:** `DemoSeedRunner` (`ApplicationRunner`) + `DemoSeedService` (`@Transactional`) no pacote `demo`, ativos sÃ³ em `demo`/`dev`; lÃª `src/main/resources/demo/usuarios.json` (3 setores, 6 usuÃ¡rios, 4 equipes fictÃ­cios) e cria apenas o que falta, por chave natural (sigla do setor, e-mail, setor + nome da equipe), sem alterar registros existentes. UsuÃ¡rio de teste `admin@admin.com` (ADMIN, ativo, `DEMO=1`); demais usuÃ¡rios no domÃ­nio `@demo.semanexus.local`. Log "seed demonstrativo aplicado / jÃ¡ existente" sem senhas.
- **DecisÃµes tÃ©cnicas:** expressÃ£o de profile `(demo | dev) & !openapi` (em vez de sÃ³ `demo`/`dev`) para que testes e o export do contrato com `dev` + `openapi` â€” que rodam sem banco â€” nÃ£o executem o seed; hashes BCrypt (custo 10) prÃ©-calculados no JSON, com senha de demonstraÃ§Ã£o distinta (`Demo@Sema2026`) para os cinco usuÃ¡rios que nÃ£o sÃ£o o de teste; `toString()` do registro de usuÃ¡rio do seed nÃ£o expÃµe o hash; `EquipeRepository.findBySetorIdAndNome`; `CoreSchemaOracleTest` passou a contar `EQUIPE_MEMBRO` sÃ³ da equipe do prÃ³prio teste (o contexto `dev` agora semeia).
- **Arquivos principais:** `demo/{DemoSeedRunner,DemoSeedService,DemoSeedDados,ResultadoSeed}.java`, `resources/demo/usuarios.json`, `core/equipe/EquipeRepository.java`, testes `demo/DemoSeed{Service,Profile,Recurso,Oracle}Test`, `docs/DEMO.md`, `docs/DATABASE.md`, `docs/ARCHITECTURE.md`, `README.md`.
- **Testes:** `mvn -B -ntp verify` â†’ 71 testes, 0 falhas, BUILD SUCCESS (com `-Dspring-boot.repackage.skip=true`: o `target/sema-nexus-api.jar` estava travado por um processo Java de outra sessÃ£o â€” a API com profile `openapi` na porta 18080, nÃ£o iniciado por esta execuÃ§Ã£o e por isso nÃ£o encerrado; o CI faz o build completo). `mvn -B -ntp verify -Poracle` (Oracle 23ai Free via Testcontainers) â†’ 11 testes, 0 falhas, incluindo `DemoSeedOracleTest`: seed aplicado na subida (3 setores, 6 usuÃ¡rios, 4 equipes, 6 membros), 2Âª execuÃ§Ã£o cria 0 e as contagens permanecem iguais, hash do admin comeÃ§a com `$2a$` e nÃ£o contÃ©m a senha, todos os usuÃ¡rios com `DEMO=1`, login `admin@admin.com` (ADMIN) e `fiscal1@demo.semanexus.local` (FISCAL) â†’ 200. Teste de profile: runner/serviÃ§o nÃ£o existem em `prod`, nem sem profile, nem com `dev`+`openapi`. Teste varre `src/main` e confirma que nenhuma senha em texto puro aparece.
- **Docker:** `docker-compose.yml` ainda nÃ£o existe (Issue #8); validado via Testcontainers.
- **PendÃªncias / observaÃ§Ãµes:** trava de inicializaÃ§Ã£o do usuÃ¡rio de teste fora de demo/dev Ã© a Issue #6; demandas e documentos demonstrativos sÃ£o a #18; a senha `admin@admin.com` sÃ³ aparece em README, docs e testes.

## 2026-11-10 13:55 â€” GH-6 Â· Trava de inicializaÃ§Ã£o do usuÃ¡rio de teste fora dos profiles demo/dev
- **PR:** (este PR) Â· **Status:** mesclado se o CI ficar verde
- **Executado por:** autopilot (sessÃ£o API)
- **O que foi feito:** `security/TestUserProfileGuard` (`ApplicationRunner`, `@Order(HIGHEST_PRECEDENCE)`): se nenhum profile ativo Ã© `demo`/`dev` e `USUARIO.EMAIL = admin@admin.com` existe, registra log `ERROR` (motivo e orientaÃ§Ã£o, sem senha) e lanÃ§a `IllegalStateException`, abortando a inicializaÃ§Ã£o. Somente leitura (`UsuarioRepository.existsByEmail`).
- **DecisÃµes tÃ©cnicas:** usa `Environment.acceptsProfiles(Profiles.of("demo","dev"))`, que tambÃ©m considera o profile padrÃ£o (`dev`) quando nenhum estÃ¡ ativo; carregada em todos os profiles **exceto `openapi`** (o CI exporta o contrato sem banco, nÃ£o hÃ¡ o que consultar â€” mesmo critÃ©rio do seed da #5); o `ApplicationRunner` roda depois de o servidor web jÃ¡ estar de pÃ©, entÃ£o hÃ¡ uma janela de milissegundos antes do encerramento â€” aceita porque a Issue especifica `ApplicationRunner` e a aplicaÃ§Ã£o Ã© encerrada em seguida (nÃ£o hÃ¡ como a trava deixar a API no ar).
- **Arquivos principais:** `security/TestUserProfileGuard.java`, testes `security/TestUserProfileGuardTest.java` e `TestUserProfileGuardOracleTest.java`, `README.md`, `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/REQUIREMENTS.md`.
- **Testes:** `mvn -B -ntp verify -Dspring-boot.repackage.skip=true` â†’ 79 testes, 0 falhas, BUILD SUCCESS (o `target/sema-nexus-api.jar` estÃ¡ travado por um processo Java de outra sessÃ£o, nÃ£o iniciado por esta execuÃ§Ã£o e por isso nÃ£o encerrado; o CI faz o build completo). `mvn -B -ntp verify -Poracle` (Oracle 23ai Free via Testcontainers) â†’ 15 testes, 0 falhas, BUILD SUCCESS. UnitÃ¡rios: prod+usuÃ¡rio â†’ falha; prod sem usuÃ¡rio â†’ sobe; demo/dev com usuÃ¡rio â†’ sobe (sem nem consultar o banco); profile padrÃ£o `dev` â†’ sobe; profile desconhecido com usuÃ¡rio â†’ falha; mensagem sem senha; bean carregado em todos os profiles menos `openapi`. Oracle (Testcontainers, `-Poracle`): aplicaÃ§Ã£o real (`SpringApplication`) sobe em `prod` sem usuÃ¡rio, `demo` cria o usuÃ¡rio, `prod` com usuÃ¡rio **recusa iniciar** (mensagem com e-mail e orientaÃ§Ã£o), `dev` com usuÃ¡rio sobe.
- **Docker:** `docker-compose.yml` ainda nÃ£o existe (Issue #8); nÃ£o se aplica ao compose â€” validado com Oracle real via Testcontainers.
- **PendÃªncias / observaÃ§Ãµes:** na #8, o compose da demonstraÃ§Ã£o deve usar `SPRING_PROFILES_ACTIVE=demo`; trocar para `prod` num volume com o seed farÃ¡ a API recusar a subida (comportamento esperado â€” documentar em `docs/DOCKER.md`).

## 2026-11-10 18:10 â€” GH-7 Â· Contrato OpenAPI dos endpoints P0 e endpoints de catÃ¡logos e estrutura
- **PR:** (este PR) Â· **Status:** mesclado se o CI ficar verde
- **Executado por:** autopilot (sessÃ£o API, Sonnet 5.5)
- **O que foi feito:** (1) **Implementado:** `GET /api/catalogos/{municipios,biomas,categorias,origens,prioridades,status}`, `GET /api/setores`, `GET /api/equipes`, `GET /api/equipes/{id}/membros` (Oracle real; 142 municÃ­pios de MT). (2) **Contrato publicado (501):** controllers, DTOs de request/response com Bean Validation e erros de todos os endpoints P0 de demandas (#11/#12/#13), documentos (#15/#16), intelligence, mapa e dashboard (#17). (3) Enums `Origem`, `CategoriaFiscalizacao`, `Prioridade`, `StatusDemanda` (+ rÃ³tulo em `Bioma`) em `core/catalogo`. (4) `docs/API.md` com padrÃµes de resposta, paginaÃ§Ã£o, filtros, erros, cÃ³digos HTTP e o contrato resumido; `docs/ARCHITECTURE.md`, `DEVELOPMENT.md` e `REQUIREMENTS.md` atualizados. O artefato `openapi.json` jÃ¡ Ã© publicado pelo job `openapi` do CI.
- **DecisÃµes tÃ©cnicas:** `@ContratoPendente(issue)` + `ContratoPendenteException` â†’ `501 problem+json` (com `issue`), executado **depois** de autenticaÃ§Ã£o, `@PreAuthorize` e validaÃ§Ã£o (401 â†’ 403 â†’ 400 â†’ 501); `ErrosPadraoCustomizer` (OperationCustomizer do springdoc) documenta 400/401/403/404/409/413/415/501 com schema `Problema` a partir do que o mÃ©todo declara, em vez de `@ApiResponse` repetido; operaÃ§Ãµes pendentes levam `x-implementado: false` e `x-issue`; upload multipart modelado num Ãºnico record com `MultipartFile arquivo` (o springdoc nÃ£o incluÃ­a a parte do arquivo com `@RequestPart` + `@ModelAttribute`); setores/equipes/membros restritos a ADMIN/GESTOR conforme ARCHITECTURE Â§6 (a tabela antiga de API.md dizia "autenticado"); categoria/prioridade opcionais no cadastro (confirmadas na triagem); `DELETE /api/demandas/{id}` e `GET /api/equipes/{id}/demandas` incluÃ­dos por jÃ¡ constarem em API.md/Issue #11/#12; municÃ­pios ordenados no service (sem acentos, palavra a palavra) porque a ordem do `ORDER BY` do Oracle diferiu da esperada (achado do teste Oracle); paginaÃ§Ã£o padrÃ£o 20 / mÃ¡x. 100 em `application.yml`; categoria/tipo de documento como texto livre (a Issue #15 pode introduzir catÃ¡logo sem quebrar o contrato).
- **Arquivos principais:** `core/catalogo/**`, `core/{setor,equipe}/**`, `flux/demanda/**`, `docia/documento/**`, `intelligence/{indicador,mapa}/**`, `core/dashboard/**`, `config/{ErrosPadraoCustomizer,OpenApiConfig}.java`, `common/{dto,openapi,exception}/**`, `security/Autorizacao.java`, `docs/API.md`.
- **Testes:** `mvn -B -ntp verify -Dspring-boot.repackage.skip=true` â†’ 132 testes, 0 falhas, BUILD SUCCESS (novos: `ContratoOpenApiTest` 14 â€” todos os endpoints P0 no `/v3/api-docs` com schemas, erros `problem+json`, multipart/binÃ¡rio e enums dos catÃ¡logos; `EndpointsContratoWebMvcTest` 21 â€” 401/403/400/501 por perfil, binding de filtros, paginaÃ§Ã£o e multipart; `CatalogoServiceTest` 10, `EquipeServiceTest` 5, `SetorServiceTest` 2). `mvn -B -ntp verify -Poracle` (Oracle 23ai Free via Testcontainers) â†’ 20 testes, 0 falhas, BUILD SUCCESS, incluindo `CatalogosEstruturaOracleTest` (5): login real, 142 municÃ­pios de MT ordenados e particionados por bioma, setores/equipes/membros do seed com `totalMembros` coerente, 403 para CONSULTA/FISCAL, 404 de equipe inexistente, 501 do dashboard
- **Docker:** `docker-compose.yml` ainda nÃ£o existe (Issue #8); nÃ£o se aplica ao compose â€” catÃ¡logos e estrutura validados contra Oracle 23ai real via Testcontainers.
- **PendÃªncias / observaÃ§Ãµes:** o jar `target/sema-nexus-api.jar` estÃ¡ travado por um processo Java de outra sessÃ£o (nÃ£o encerrado); o `repackage` foi pulado localmente com `-Dspring-boot.repackage.skip=true` e o CI faz o build completo. Cada Issue de implementaÃ§Ã£o (#11â€“#17) deve retirar sua operaÃ§Ã£o de `PENDENTES` em `ContratoOpenApiTest`. RÃ³tulos de categoria/tipo de documento e o catÃ¡logo de tipos de histÃ³rico (`TipoHistorico`) podem ser refinados em #11/#15 mantendo o contrato.

## 2026-11-10 19:30 â€” GH-8 Â· docker-compose.yml com Oracle Free + API + Web, .env.example, healthchecks e volumes
- **PR:** (este PR) Â· **Status:** mesclado se o CI ficar verde
- **Executado por:** autopilot (sessÃ£o API, Sonnet 5.5)
- **O que foi feito:** `docker-compose.yml` na raiz (projeto `sema-nexus`) com `oracle` (`gvenzl/oracle-free:23.26.3-slim-faststart`, volume `oradata`, healthcheck `healthcheck.sh`), `api` (build `.`, `depends_on: oracle healthy`, profile `demo`, datasource apontando para `oracle:1521/FREEPDB1`, volume `uploads`, healthcheck em `/actuator/health`, `start_period` 90 s) e `web` (build `../Sema_Nexus_Web`, `depends_on: api healthy`). `.env.example` reescrito e comentado; `docs/DOCKER.md` e `README.md` atualizados (Implementado vs Planejado).
- **DecisÃµes tÃ©cnicas:** nenhum segredo no compose â€” `ORACLE_PASSWORD`, `APP_USER`, `APP_USER_PASSWORD` e `JWT_SECRET` usam `${VAR:?mensagem}` e o compose recusa subir sem eles; `SPRING_DATASOURCE_USERNAME/PASSWORD` derivam de `APP_USER/APP_USER_PASSWORD` dentro do compose (removidos do `.env.example` para evitar interpolaÃ§Ã£o aninhada); portas do host configurÃ¡veis (`API_PORT`, `WEB_PORT`, `ORACLE_PORT`, esta Ãºltima acrescentada porque 1521 costuma estar ocupada por outros Oracle locais); tag fixada em `23.26.3-slim-faststart` (mesmo digest de `23-slim-faststart`); o container do Web escuta 8080 (nginx-unprivileged), mapeado para `WEB_PORT`.
- **Arquivos principais:** `docker-compose.yml`, `.env.example`, `docs/DOCKER.md`, `README.md`, `docs/DIARIO_DESENVOLVIMENTO.md`.
- **Testes:** `mvn -B -ntp verify -Dspring-boot.repackage.skip=true` â†’ 132 testes, 0 falhas, BUILD SUCCESS (nenhum cÃ³digo Java alterado). Docker (trava `docker.lock`, `.env` local com `API_PORT=18081` e `ORACLE_PORT=11521` porque 8080, 18080 e 1521 estavam ocupadas por processos de outras sessÃµes/projetos, que nÃ£o foram tocados): `docker compose config -q` OK; `docker compose up -d --build` a partir de volumes novos â†’ `oracle`, `api` e `web` `healthy`; `GET /healthz` do Web â†’ 200; `POST http://localhost:3000/api/auth/login` (proxy Nginx) com o usuÃ¡rio de teste â†’ 200, perfil ADMIN; Flyway aplicou 2 migrations e o seed criou 3 setores/6 usuÃ¡rios/4 equipes; `docker compose down` (sem `-v`) + `up` â†’ Flyway "up to date", log "Seed demonstrativo jÃ¡ existente", contagens idÃªnticas (6/4/3, 1 admin demo); arquivo gravado em `/data/uploads` (usuÃ¡rio 10001) persistiu. Volumes `sema-nexus_oradata`/`uploads` mantidos.
- **Docker:** validado conforme acima; `docker compose down` executado e trava liberada.
- **PendÃªncias / observaÃ§Ãµes:** scripts `demo-up/down/reset` (#9), `docs/DOCKER.md` completo com requisitos e soluÃ§Ã£o de problemas (#10) e smoke test no CI (#20) sÃ£o Issues seguintes. Aviso do Flyway "Oracle 23.26 is newer than this version" Ã© informativo (a migraÃ§Ã£o funciona).

## 2026-11-10 18:50 — GH-9 · Scripts demo-up, demo-down e demo-reset para Linux/macOS e Windows
- **PR:** #94 · **Status:** PR aberto; merge conforme CI
- **Executado por:** autopilot (sessão API, Sonnet 5.5) na execução original; retomada e conclusão pelo orquestrador em 2026-11-11 (commit `45fad54` existente, merge com a `main` e conflitos de docs resolvidos pelo orquestrador)
- **Agentes que atuaram:** `planejador-issue` não atuou (retomada de trabalho existente); `implementador-backend` não atuou nesta execução; `validador-qa` APROVADO; `revisor-codigo` APROVADO; `revisor-seguranca` sem bloqueios; `documentador` (esta entrada, status na documentação e rascunho do PR). `diagnosticador` não atuou.
- **O que foi feito:** `scripts/demo-up|demo-down|demo-reset` em `.sh` (POSIX) e `.ps1`, com funções comuns em `scripts/lib/demo-common.{sh,ps1}`; `Makefile` opcional (`up`, `down`, `reset`, `logs`, `ps`); testes `scripts/tests/demo-scripts.test.sh` (41 verificações) e job `scripts-demo` no CI; `docs/DOCKER.md`, `README.md`, `docs/DEMO.md`, `docs/ARCHITECTURE.md` atualizados.
- **Decisões técnicas:** (1) `demo-up` não sobrescreve `.env` existente e, ao criá-lo, copia o `.env.example` como está (senhas de exemplo — sortear senhas quebraria um volume `oradata` já criado com as de exemplo) e avisa para trocá-las; (2) a espera usa `docker compose ps -q` + `docker inspect` (`State.Health.Status`), falha cedo em `unhealthy` e no timeout (`DEMO_UP_TIMEOUT`, padrão 600 s); (3) `demo-reset` recusa por padrão: valida profile (`SPRING_PROFILES_ACTIVE` do ambiente, senão do `.env`, senão `demo`; qualquer elemento ≠ demo/dev recusa) **antes** de qualquer chamada ao Docker e exige `DEMO_RESET_CONFIRM=yes` (ambiente ou `.env`) ou digitar `RESETAR` em terminal interativo; sem terminal e sem confirmação → recusa; (4) os scripts imprimem o e-mail do login de teste e apontam para `docs/DEMO.md` em vez de imprimir a senha, para respeitar a regra do CLAUDE.md §7 (senha em texto puro só em README, DEMO.md, DOCKER.md e testes); (5) `demo-down` nunca usa `-v`; (6) arquivos `.ps1` gravados com BOM UTF-8 (Windows PowerShell 5.1 lê UTF-8 sem BOM como ANSI e corrompe acentos); `Invoke-Nativo` usa `| Out-Host` para que só o exit code seja devolvido; (7) testes com `docker` falso no PATH: provam as recusas e a ordem `down -v` → `up` sem tocar em volumes reais.
- **Arquivos principais:** `scripts/demo-{up,down,reset}.{sh,ps1}`, `scripts/lib/demo-common.{sh,ps1}`, `scripts/tests/demo-scripts.test.sh`, `Makefile`, `.github/workflows/ci.yml`, `docs/DOCKER.md`.
- **Testes:**
  - `sh scripts/tests/demo-scripts.test.sh` → 41/41 ok (validador-qa; execução anterior também com `SH_BIN=dash dash …`, POSIX estrito).
  - `mvn -B verify` → 161 testes, 0 falhas (validador-qa). O `repackage` foi pulado porque o `target/sema-nexus-api.jar` estava travado por processo Java de outra sessão, não encerrado; o CI faz o build completo.
  - **Scripts `.ps1` executados no Windows PowerShell, contra Docker real** (validador-qa): `scripts/demo-up.ps1` → exit 0, `oracle`/`api`/`web` `healthy`; `scripts/demo-down.ps1` → exit 0, volumes preservados; `scripts/demo-reset.ps1` recusou sem confirmação e com `SPRING_PROFILES_ACTIVE=prod` → exit 1, nada apagado, volumes intactos.
  - **Execução anterior (Git Bash, Docker real):** `./scripts/demo-up.sh` → `oracle`, `api` e `web` `healthy` em 53 s; `POST http://127.0.0.1:3000/api/auth/login` (proxy Nginx) com o usuário de teste → 200, perfil ADMIN; `./scripts/demo-down.sh` → containers removidos e volumes `sema-nexus_oradata`/`sema-nexus_uploads` preservados; `demo-up.sh` de novo → healthy, login 200, Flyway "up to date" e "Seed demonstrativo já existente". No host, `localhost` (IPv6) deu reset de conexão com o Docker Desktop; `127.0.0.1` funciona.
  - Caminho completo do reset (`down -v`) exercitado **somente com `docker` falso**; não foi executado contra o Docker real (proibido sem confirmação do proprietário).
- **Docker:** validado com o Docker real: `demo-up` (`.sh` e `.ps1`) e `demo-down` (`.sh` e `.ps1`) conforme acima; `demo-reset.ps1` validado apenas nas recusas (sem confirmação e com profile `prod`), sem apagar nada. **Não executado no Docker real:** o caminho `demo-reset` com `down -v` (proibido sem confirmação do proprietário, CLAUDE.md §7 e `.claude/settings.json`); exercitado só com `docker` falso.
- **Pendências / observações para as próximas Issues:**
  1. `DEMO_RESET_CONFIRM=yes` persistido no `.env` elimina a pergunta de confirmação do `demo-reset`; avaliar aviso ou não aceitar essa variável só no `.env`.
  2. `COMPOSE_FILE` / `COMPOSE_PROJECT_NAME` herdados do ambiente podem redirecionar o `down -v` do reset para outro projeto ou arquivo.
  3. `DEMO_UP_TIMEOUT` não numérico produz erro críptico; validar antes do uso.
  4. Os `.ps1` não têm teste automatizado no CI (só o `.sh` roda em `scripts-demo`).

## 2026-11-10 19:00 â€” GH-10 Â· docs/DOCKER.md e seÃ§Ã£o de execuÃ§Ã£o no README da API
- **PR:** (este PR) Â· **Status:** mesclado se as condiÃ§Ãµes do autopilot forem atendidas
- **Executado por:** autopilot (sessÃ£o API, Sonnet 5.5)
- **O que foi feito:** `docs/DOCKER.md` reorganizado com tabela de status (Implementado / Em desenvolvimento / Planejado / Futuro), passo a passo rÃ¡pido, estrutura de pastas lado a lado, requisitos **medidos** (disco ~12 GB, RAM â‰¥ 8 GB no host e ~2 GB para o Oracle, tempos de subida), seÃ§Ã£o Windows/WSL2 (`.wslconfig`), Apple Silicon (marcado como nÃ£o validado), operaÃ§Ã£o do dia a dia, reset (`down -v` destrutivo, em destaque) e tabela de problemas comuns (variÃ¡vel ausente, repositÃ³rio do Web ausente, porta em uso, Oracle unhealthy/memÃ³ria, senha alterada apÃ³s criar o volume, trava do usuÃ¡rio de teste, permissÃ£o de volume, `localhost` x `127.0.0.1`). README: situaÃ§Ã£o atualizada e seÃ§Ã£o "Executar via Docker" com requisitos e como parar sem perder dados.
- **DecisÃµes tÃ©cnicas:** os scripts `demo-*` (Issue #9, PR #46 ainda aberto) aparecem como **Em desenvolvimento**, com os comandos `docker compose` equivalentes â€” nÃ£o se documenta como pronto o que nÃ£o estÃ¡ na `main`; mensagens de erro da tabela foram capturadas de execuÃ§Ãµes reais (`docker compose config` sem variÃ¡vel; build sem o repositÃ³rio do Web); a mediÃ§Ã£o de disco veio de `docker images` e do tamanho do volume `oradata` (3,16 GB), pois a imagem `faststart` ocupa ~6,5 GB (a estimativa de 4 GB da Issue estava baixa).
- **Arquivos principais:** `docs/DOCKER.md`, `README.md`, `docs/DIARIO_DESENVOLVIMENTO.md`.
- **Testes:** passo a passo executado **do zero pelo autopilot**, seguindo sÃ³ o documento, em clones novos lado a lado (API a partir deste branch + Web da `main`), `.env` criado de `.env.example` com segredos aleatÃ³rios e portas 18081/13000/11521 (8080 e 1521 ocupadas por serviÃ§os de outros projetos, nÃ£o tocados), projeto Compose `sema-doc-test` (volumes novos): `docker compose up -d --build` â†’ exit 0 em 41 s, `oracle`/`api`/`web` `healthy`; `GET /actuator/health` â†’ UP; `POST /api/auth/login` (proxy do Nginx) com o usuÃ¡rio de teste â†’ 200 (`token`, `expiresIn`, `usuario`); Web `/` â†’ 200; `docker compose down` (sem `-v`). `mvn -B -ntp verify -Dspring-boot.repackage.skip=true` â†’ 132 testes, 0 falhas, BUILD SUCCESS (nenhum cÃ³digo Java alterado).
- **Docker:** validado conforme acima, com trava `docker.lock` (liberada). Os volumes `sema-doc-test_oradata` (~3,2 GB) e `sema-doc-test_uploads` ficaram no Docker porque removÃª-los (`down -v`/`volume rm`) exige confirmaÃ§Ã£o do proprietÃ¡rio.
- **PendÃªncias / observaÃ§Ãµes:** o critÃ©rio "executado por alguÃ©m do time" foi atendido pelo autopilot, nÃ£o por uma pessoa â€” vale uma releitura humana; conflitos de merge previsÃ­veis com o PR #46 (Issue #9) em `README.md`/`docs/DOCKER.md`, a resolver por quem mesclar depois; Apple Silicon nÃ£o validado.

## 2026-11-11 11:32 â€” GH-11 Â· Schema do Flux e endpoints de cadastro, ediÃ§Ã£o, listagem e detalhes de demanda
- **PR:** #94 Â· **Status:** aguardando CI e merge
- **Executado por:** autopilot (sessÃ£o API), retomada de WIP (commits `88fd16e` salvo antes da pausa e `02ad489` com testes, locks e correÃ§Ãµes). O `planejador-issue` nÃ£o atuou nesta retomada.
- **Agentes que atuaram:** `implementador-backend` (conclusÃ£o do WIP e correÃ§Ãµes apontadas na revisÃ£o); `validador-qa` (build, testes unitÃ¡rios e Oracle, 0 falhas); `revisor-codigo` (APROVADO apÃ³s 1 ciclo de correÃ§Ã£o); `revisor-seguranca` (sem bloqueios, com achados baixos listados abaixo); `documentador` (esta entrada, status na documentaÃ§Ã£o e rascunho do PR). `diagnosticador` nÃ£o atuou.
- **O que foi feito:** `V3__flux.sql` com `DEMANDA`, `NUMERACAO_DEMANDA`, `DEMANDA_HISTORICO`, `DISTRIBUICAO` e `FISCALIZACAO` (FKs, CHECKs de enums, Ã­ndices em status, municÃ­pio, categoria, data de recebimento e equipe). Endpoints `POST /api/demandas`, `PUT /api/demandas/{id}`, `GET /api/demandas` (paginaÃ§Ã£o e filtros `q`, `status`, `municipioId`, `categoria`, `prioridade`, `equipeId`, `fiscalId`, `atrasada`, `de`, `ate`), `GET /api/demandas/{id}`, `GET /api/demandas/{id}/historico` e `DELETE /api/demandas/{id}` (exclusÃ£o lÃ³gica). NÃºmero `DEM-AAAA/NNNN` gerado por `NumeracaoService`. Os endpoints saÃ­ram do contrato `501` (`ContratoOpenApiTest` com `PENDENTES` reduzido).
- **DecisÃµes tÃ©cnicas:** nÃºmero gerado com lock pessimista em `NUMERACAO_DEMANDA`, o que evita colisÃ£o em cadastros concorrentes (teste no Oracle real); ediÃ§Ã£o e exclusÃ£o tambÃ©m com lock pessimista; ediÃ§Ã£o sÃ³ em PENDENTE ou EM_TRIAGEM e exclusÃ£o lÃ³gica sÃ³ ADMIN e sÃ³ em PENDENTE (409 fora dessas condiÃ§Ãµes, sem efeitos); validaÃ§Ã£o territorial (municÃ­pio de MT e coordenadas no retÃ¢ngulo de MT) e prazo sugerido â‰¥ data de recebimento; `atrasada` Ã© derivada (prazo < hoje e status â‰  FINALIZADA), nÃ£o Ã© status; FISCAL sÃ³ enxerga demandas da sua equipe ou atribuÃ­das a ele; correÃ§Ã£o de fuso na data de exclusÃ£o lÃ³gica apontada na revisÃ£o.
- **Arquivos principais:** `src/main/resources/db/migration/V3__flux.sql`, `flux/demanda/{Demanda,DemandaService,DemandaSpecification,DemandaController,DemandaHistorico,Fiscalizacao,NumeracaoDemanda,NumeracaoService}.java` e repositÃ³rios, `common/exception/DadosInvalidosException.java`, `docs/API.md`, `docs/DATABASE.md`, `docs/REQUIREMENTS.md`, `README.md`.
- **Testes:**
  - `mvn -B verify -Dspring-boot.repackage.skip=true` â†’ 161 testes, 0 falhas. O `repackage` foi pulado porque o `target/sema-nexus-api.jar` estava travado pelo processo Java PID 42592, de outra sessÃ£o, que nÃ£o foi encerrado. O CI faz o build completo.
  - `mvn -Poracle` (Oracle 23ai Free via Testcontainers) â†’ 24 testes OK, incluindo `DemandaOracleTest` 4/4: cadastros concorrentes com nÃºmeros distintos e sequenciais, filtros combinados com paginaÃ§Ã£o, FISCAL sem equipe nÃ£o enxerga demandas de outros, e lock na ediÃ§Ã£o e exclusÃ£o.
  - CritÃ©rios de aceitaÃ§Ã£o da Issue: cadastro concorrente sem colisÃ£o (`DemandaOracleTest.cadastrosConcorrentesRecebemNumerosDistintosESequenciais`); municÃ­pio fora de MT e coordenada fora do retÃ¢ngulo â†’ 400 por campo (`DemandaRequestValidacaoTest`); listagem com filtros e paginaÃ§Ã£o (`DemandaOracleTest.filtrosCombinadosEPaginacao`); ediÃ§Ã£o fora de PENDENTE/EM_TRIAGEM â†’ 409 (`DemandaServiceTest.edicaoForaDePendenteOuEmTriagemResponde409ESemEfeitos`); FISCAL sem equipe (`DemandaOracleTest.fiscalSemEquipeNaoEnxergaDemandasDeOutros` e `DemandaServiceTest.fiscalSemEquipeNaoVeDemandaSemAtribuicao`); contrato OpenAPI (`ContratoOpenApiTest`).
- **Docker:** validaÃ§Ã£o do `docker compose up -d --build` em andamento no momento desta entrada; o resultado serÃ¡ registrado pelo orquestrador no PR (ver comentÃ¡rio do orquestrador).
- **PendÃªncias / observaÃ§Ãµes para as prÃ³ximas Issues:**
  - Achados baixos do `revisor-seguranca` (nÃ£o bloqueantes): (1) FISCAL que consulta demanda de outra equipe recebe 403 em vez de 404, o que revela a existÃªncia do id; (2) nÃ£o hÃ¡ `@Version` (lock otimista), apenas o pessimista; (3) a mensagem de erro de ordenaÃ§Ã£o (`sort`) ecoa o nome do campo informado. O relatÃ³rio de seguranÃ§a citou 4 achados baixos, mas sÃ³ 3 foram repassados a este registro; o 4Âº deve ser conferido com o `revisor-seguranca` antes do merge.
  - Documentos anexados Ã  demanda (`totalDocumentos` com conteÃºdo real) dependem da Issue #16; triagem, distribuiÃ§Ã£o e fiscalizaÃ§Ã£o sÃ£o #12 e #13.

## 2026-11-11 · GH-12 · Máquina de estados, triagem e distribuição de demandas
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `DemandaStateMachine` (todas as transições de ARCHITECTURE §5; ação fora do status de origem gera 409), `TriagemService` (`POST /triagem/iniciar` e `/triagem` com `decisao` CLASSIFICAR ou SOLICITAR_COMPLEMENTACAO), `DistribuicaoService` (`POST /distribuicao`: fiscal deve ser membro da equipe ativa e prazo >= hoje em Cuiabá, senão 400; grava `DISTRIBUICAO`, equipe/fiscal/prazo da demanda), entidade `Distribuicao` e `GET /api/equipes/{id}/demandas`. Toda transição grava `DEMANDA_HISTORICO` (status anterior/novo, texto, usuário, data) e auditoria, com lock pessimista na demanda.
- **Decisões técnicas:** mantido o contrato já publicado na #7 (`decisao`/`CLASSIFICAR|SOLICITAR_COMPLEMENTACAO`) em vez de `acao: CONCLUIR|COMPLEMENTAR` do texto da Issue; a máquina também expõe as ações da #13 (início, fiscalização, conclusão, devolução) para reaproveitamento; sem migration nova (tabelas já em V3).
- **Arquivos principais:** `flux/demanda/{DemandaStateMachine,TriagemService,DistribuicaoService,Distribuicao,DistribuicaoRepository,DemandaController,EquipeDemandasController,DemandaService}.java`, testes `DemandaStateMachineTest`, `TriagemDistribuicaoServiceTest`, `EndpointsContratoWebMvcTest`, `ContratoOpenApiTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador rodou só os afetados).
- **Pendências:** teste Oracle de ponta a ponta da distribuição não incluído (validador pode rodar `-Poracle`).

## 2026-11-11 · GH-13 · Acompanhamento, fiscalização, conclusão e devolução de demandas
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `AcompanhamentoService` (`POST /inicio` DISTRIBUIDA → EM_ANDAMENTO e `POST /atividades` com registro no histórico), `FiscalizacaoService` (`POST /fiscalizacao`: grava/atualiza `FISCALIZACAO` 1:1 e vai para AGUARDANDO_CONCLUSAO) e `ConclusaoService` (`POST /conclusao` → FINALIZADA com `resultadoFinal`, justificativa e `concluidaEm`; `POST /devolucao` → EM_ANDAMENTO). Todos usam a máquina de estados, lock pessimista, histórico e auditoria. Endpoints saíram do contrato `501`.
- **Decisões técnicas:** FISCAL só age se for o fiscal designado (senão 403); GESTOR/ADMIN em qualquer demanda. Conclusão sem registro de fiscalização responde 409 (além da exigência de status). Demanda FINALIZADA não aceita nenhuma transição (409). Reenvio da fiscalização após devolução atualiza o mesmo registro (1:1). Mantido o contrato do DTO da #7 (`descricao`/`data` em atividades) em vez de `{texto}` do texto da Issue. Textos do histórico limitados a 2000 caracteres. Sem migration nova.
- **Arquivos principais:** `flux/demanda/{AcompanhamentoService,FiscalizacaoService,ConclusaoService,ExecucaoSuporte,DemandaController,Fiscalizacao,FiscalizacaoRepository}.java`, testes `ExecucaoServicosTest`, `EndpointsContratoWebMvcTest`, `ContratoOpenApiTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador rodou só os afetados).
- **Pendências:** sem teste Oracle ponta a ponta do fluxo completo (validador pode rodar `-Poracle`).

## 2026-11-11 · GH-14 · Auditoria centralizada e consulta GET /api/auditoria
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `AuditService` ganhou `consultar` (paginado, filtros de período, usuário, ação e entidade, por Specification), `recentes(n)` (limite 1..100, para o dashboard da #17) e mascaramento de senha/token/Bearer/JWT no `detalhe`. Novo `AuditoriaController` (`GET /api/auditoria`, somente ADMIN) com DTOs `AuditoriaDto` (inclui `usuarioNome`) e `AuditoriaFiltro`. Cadastro, edição, exclusão, status e distribuição de demanda já gravavam auditoria (#11 a #13); upload fica para a #15.
- **Decisões técnicas:** sem migration (tabela e índices por data e entidade já existem na V1); `sort` restrito a `id,data,usuarioId,acao,entidade` (fora disso usa data desc); `de`/`ate` inclusivos por dia.
- **Arquivos principais:** `core/auditoria/{AuditService,AuditoriaController,AuditoriaRepository}.java`, `core/auditoria/dto/*`, testes `AuditServiceTest`, `AuditoriaControllerWebMvcTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador rodou AuditServiceTest, AuditoriaControllerWebMvcTest, EndpointsContratoWebMvcTest, AuthServiceTest: 0 falhas).
- **Pendências:** sem teste Oracle do filtro; validador pode rodar `-Poracle`.
## 2026-11-11 · GH-17 · Indicadores agregados, pontos do mapa e dashboard inicial
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** implementados `GET /api/intelligence/{resumo,serie-mensal,por-municipio,por-bioma,por-categoria,por-situacao,mapa}` e `GET /api/dashboard` (removido `@ContratoPendente`). Novos `IndicadorRepository` (Criteria API, filtros `de/ate/municipioId/bioma/categoria/status`), `IndicadorService` e `DashboardService`. `ResumoIndicadores` ganhou `fiscalizacoes`, `percentualConclusao`, `tempoMedioAtendimentoDias`; `DashboardResponse` ganhou `porBioma` e `porCategoria` (campos aditivos).
- **Decisões técnicas:** agregação em memória sobre as demandas filtradas (volume demonstrativo; uma consulta); documentos = 0 até a #15; atividades recentes vêm de `DemandaHistorico` (`findRecentes`, contrato já publicado da #7), não da tabela AUDITORIA; hoje calculado no fuso America/Cuiaba. Sem migration.
- **Arquivos principais:** `intelligence/indicador/{IndicadorRepository,IndicadorService,IndicadorController}.java`, `intelligence/mapa/MapaController.java`, `core/dashboard/{DashboardService,DashboardController}.java`, `flux/demanda/DemandaHistoricoRepository.java`, testes `IndicadorServiceTest`, `DashboardServiceTest`, `IndicadorOracleTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: IndicadorServiceTest, DashboardServiceTest, EndpointsContratoWebMvcTest, ContratoOpenApiTest e IndicadorOracleTest com Testcontainers: 0 falhas).
- **Pendências:** `totalDocumentos` = 0 (TODO até a #15).
- **Observações:** agregados, mapa e dashboard são visão institucional aberta a todos os perfis autenticados (sem escopo de FISCAL); `atividadesRecentes` vem de `DemandaHistorico` (desvio da AUDITORIA citada na Issue, justificado pelo DTO publicado); `de`/`ate` aplicam-se à data de recebimento; campos novos nos DTOs (`ResumoIndicadores`, `DashboardResponse`): avisar o Web; mapa medido em 10–19 ms com 7 demandas do seed; `IndicadorOracleTest` cobre cadastro e finalização alterando `resumo` e `por-situacao`.

## 2026-11-11 · GH-15 · DocIA: schema, armazenamento em volume, upload, download e extração de texto
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `V4__docia.sql` (`DOCUMENTO`, `DOCUMENTO_DEMANDA`); `StorageService` + `LocalFileStorageService` (`${STORAGE_DIR}/AAAA/MM/<uuid>`, chave confinada à raiz); `TipoArquivo` (extensão + bytes iniciais), `TextExtractor` (PDFBox/UTF-8; DOCX sem extração), `DocumentoService` (SHA-256, auditoria `UPLOAD`, vínculo opcional por `demandaId`); `POST /api/documentos` e `GET /api/documentos/{id}/arquivo` saíram do contrato `501`. Erros 415/413 no `GlobalExceptionHandler`; limite multipart 10 MB no `application.yml`; `STORAGE_DIR` no `.env.example`.
- **Decisões técnicas:** nome original saneado (sem caminho nem controles) só no banco; palavras-chave em coluna única separada por vírgula; texto sem extração (PDF ilegível/criptografado, DOCX) fica nulo; download sempre `octet-stream` + `attachment` + `nosniff`; arquivo órfão é removido se a persistência falhar. SHA256 em `VARCHAR2(64)` (o Hibernate valida CHAR como outro tipo). Dependência nova: `pdfbox 3.0.4`.
- **Arquivos principais:** `docia/storage/*`, `docia/documento/{Documento,DocumentoRepository,DocumentoService,DocumentoController,TipoArquivo,TextExtractor}.java`, `V4__docia.sql`, testes `DocumentoServiceTest`, `LocalFileStorageServiceTest`, `DocumentoOracleTest`, `EndpointsContratoWebMvcTest`, `ContratoOpenApiTest`.
- **Testes:** a confirmar pelo validador (implementador: DocumentoServiceTest, LocalFileStorageServiceTest, EndpointsContratoWebMvcTest, ContratoOpenApiTest e DocumentoOracleTest `-Poracle`: 0 falhas).
- **Pendências:** persistência após `docker compose down`/`up` não verificada (volume `uploads` já está no compose); `totalDocumentos` do dashboard (#17) ainda 0, a ligar na #16.


## 2026-11-11 · GH-16 · DocIA: biblioteca documental, pesquisa textual, detalhes e vínculo com demandas
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** implementados `GET /api/documentos` (paginado; `q`, categoria, tipo, município, demanda, `de`/`ate`, ordenação), `GET /api/documentos/{id}` (metadados, trecho do texto, demandas vinculadas, histórico) e `GET|POST|DELETE /api/demandas/{id}/documentos` (removido `@ContratoPendente`; `PENDENTES` do `ContratoOpenApiTest` ficou vazio). Vínculo/desvínculo gravam evento `DOCUMENTO` no histórico da demanda (`DemandaService.registrarEventoDocumento`) e `VINCULAR`/`DESVINCULAR` na auditoria; duplicado → 409.
- **Decisões técnicas:** pesquisa ignora caixa e acento via `TRANSLATE(coluna, ...) LIKE ? ESCAPE ''` em SQL parametrizado (a Criteria API do Hibernate recusa funções de texto sobre CLOB), com o termo normalizado no Java (`Normalizer`); ids encontrados alimentam a Specification dos demais filtros; `BINARY_AI` não foi usado para não alterar NLS da sessão; mantido o contrato publicado na #7 (`POST /api/demandas/{id}/documentos` com corpo `{documentoId}`, não `/{docId}`); `DocumentoDetalhe` ganhou `historico` (aditivo) e `textoExtraido` passou a ser trecho de 2000 caracteres; `AuditService.historicoDaEntidade` (até 50).
- **Arquivos principais:** `docia/documento/{DocumentoService,DocumentoController,DemandaDocumentoController,DocumentoSpecification,DocumentoRepository}.java`, `dto/DocumentoDetalhe.java`, `flux/demanda/DemandaService.java`, `core/auditoria/{AuditService,AuditoriaRepository}.java`, testes `DocumentoServiceTest`, `DocumentoOracleTest`, `EndpointsContratoWebMvcTest`, `ContratoOpenApiTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: DocumentoServiceTest, EndpointsContratoWebMvcTest, ContratoOpenApiTest, DemandaServiceTest, AuditServiceTest = 75 testes, 0 falhas; DocumentoOracleTest `-Poracle` = 3, 0 falhas).
- **Pendências:** `totalDocumentos` do dashboard (#17) continua 0 (fora do escopo da #16); busca é textual, semântica é P2.


## 2026-11-11 · GH-18 · Seed de demandas e documentos fictícios coerentes para a demonstração
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** massa demonstrativa idempotente (`DemoMassaRunner` → `DemoMassaService`, após o `DemoSeedRunner`): 120 demandas em 26 municípios de MT (3 biomas, 7 status, algumas atrasadas), histórico, distribuições e 46 fiscalizações coerentes com o status, 12 documentos fictícios (PDF gerados com PDFBox e TXT) no volume, incluindo as duas versões do "Parecer Técnico nº 014/2026", vinculados às demandas do cenário fixo; cenário documentado em `docs/DEMO.md`.
- **Decisões técnicas:** JDBC direto (preserva datas retroativas e evita a regra de máquina de estados); `DemoMassaGerador` puro e determinístico (semente fixa, datas relativas ao dia); idempotência pela marca `[DEMO-SEED]` em `OBSERVACOES` (demandas) e por (título, arquivo) (documentos); `NUMERACAO_DEMANDA` atualizada; execução fora do `DemoSeedRunner` para não acoplar o teste de profile (ordem via `@Order`). Textos autorais adaptados (o `seed-docs.js` do protótipo não estava acessível). Sem migration.
- **Arquivos principais:** `demo/{DemoMassaGerador,DemoMassaDocumentos,DemoMassaService,DemoMassaRunner}.java`, testes `DemoMassaGeradorTest`, `DemoMassaDocumentosTest`, `DemoSeedOracleTest`, `docs/DEMO.md`, `docs/DATABASE.md`.
- **Testes:** a confirmar pelo validador (implementador: DemoMassa*Test e DemoSeed*Test sem Oracle: 0 falhas; DemoSeedOracleTest `-Poracle`: 4 testes, 0 falhas).
- **Pendências:** Docker/`demo-reset` com volume real não exercitado; "números do dashboard conferem" cobertos por contagens SQL, não por chamada ao `/api/dashboard`.

## 2026-11-11 · GH-19 · Cobertura de testes dos serviços críticos e revisão de segurança da API
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `RevisaoSegurancaApiTest` (checklist automatizado: `@PreAuthorize` em todos os endpoints, cabeçalhos de segurança, actuator só health, erros sem stacktrace, CORS fora do dev, logs sem segredos); `docs/REVISAO_SEGURANCA.md` com o checklist e evidências. Achado corrigido: leituras de demandas/documentos/catálogos/indicadores sem `@PreAuthorize` explícito; agora `@PreAuthorize(Autorizacao.AUTENTICADO)` na classe (sem mudança de comportamento).
- **Decisões técnicas:** os testes de máquina de estados, numeração concorrente (Oracle), território, upload, trava e perfis já existiam; não foram duplicados.
- **Arquivos principais:** `security/Autorizacao.java`, controllers de flux/docia/core/intelligence, `security/RevisaoSegurancaApiTest.java`, `docs/REVISAO_SEGURANCA.md`.
- **Testes:** a confirmar pelo validador (implementador: RevisaoSegurancaApiTest, EndpointsContratoWebMvcTest, *WebMvcTest, ContratoOpenApiTest: 0 falhas).
- **Pendências:** varredura de CVE em dependências não executada (sem scanner no projeto).

## 2026-11-11 · GH-21 · Roteiro de demonstração de 10 minutos e cenário integrado
- **PR:** #68 · **Status:** mesclado após ensaio do proprietário (12/11/2026: roteiro coube em 10 min)
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `docs/DEMO.md` reescrito com preparação (demo-up, `docker compose ps`, demo-reset, login, modo offline), roteiro cronometrado 0-10 min usando os registros C1-C6 do seed (#18), usuários/senhas demonstrativos e limitações/futuro restritos ao implementado (conferido contra rotas do frontend e endpoints da API).
- **Decisões técnicas:** só documentação; comparação de versões, assistente e satélite listados como futuro; o comportamento do mapa sem internet e a atualização dos indicadores ficam marcados "a confirmar no ensaio".
- **Arquivos principais:** `docs/DEMO.md`, `docs/DIARIO_DESENVOLVIMENTO.md`.
- **Testes:** a confirmar pelo validador (sem código alterado).
- **Pendências:** ensaio cronometrado em 26/11 e execução no Docker após `demo-reset` não realizados; nomes exatos de botões do frontend podem divergir.

## 2026-11-11 · GH-27 · Assistente DocIA com provider desacoplado, modo demonstrativo e citações
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `POST /api/docia/perguntas` (`docia/assistente`): `AssistenteService` recupera trechos por busca textual em `DOCUMENTO`, `AssistenteProvider` desacoplado, `DemoAssistenteProvider` (extrativo, padrão) e `ExternalAssistenteProvider` (HTTP, só por `DOCIA_PROVIDER`/`DOCIA_API_URL`/`DOCIA_API_KEY`; cai no demonstrativo se faltar configuração ou falhar). Resposta sempre com `modo` e `referencias`; sem base → "não há informação suficiente"; legislação/multas/procedimentos oficiais → recusa.
- **Decisões técnicas:** reuso do SQL de busca da #16 (`DocumentoSpecification` tornou-se pública); recusa por palavras-chave sobre texto normalizado (heurística simples); contrato do provider externo definido aqui (`{pergunta, contexto[]}` → `{resposta}`); sem migration.
- **Arquivos principais:** `docia/assistente/*.java`, `docia/documento/DocumentoSpecification.java`, teste `AssistenteServiceTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: AssistenteServiceTest sem Oracle).
- **Pendências:** sem teste Oracle ponta a ponta contra o seed; provider externo sem teste de integração HTTP.

## 2026-11-11 · GH-34 · Camada de referência com dados públicos de satélite do INPE (focos de calor e DETER) em MT
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** migration `V5__referencia_satelite.sql` (`IMPORTACAO_REFERENCIA`, `REFERENCIA_SATELITE`), snapshot versionado (focos de calor e alertas DETER, ~230 KB) em `resources/referencia/`, `ReferenciaSateliteLoader` idempotente por SHA-256 (profiles demo/dev), endpoints somente leitura `/api/intelligence/referencia/{metadados,pontos,por-municipio}`, scripts `scripts/referencia/baixar-inpe.{ps1,sh}`.
- **Decisões técnicas:** exceção de escopo aprovada (só dados públicos do INPE, camada separada, `oficialSema=false`); detalhes de fonte/licença em `docs/DATABASE.md` e `docs/ARCHITECTURE.md`.
- **Arquivos principais:** `intelligence/referencia/*.java`, `V5__referencia_satelite.sql`, testes `ReferenciaSateliteLoaderTest`, `ReferenciaSateliteServiceTest`, `ContratoOpenApiTest`, docs API/DATABASE/ARCHITECTURE/REQUIREMENTS/DEMO.
- **Testes:** a confirmar pelo validador (implementador: ReferenciaSatelite*Test e ContratoOpenApiTest sem Oracle: 0 falhas).
- **Pendências:** carga em Oracle real/Docker e tempo de resposta < 1 s não exercitados.

## 2026-11-11 · GH-26 · Comparação documental de metadados e conteúdo textual
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `POST /api/documentos/comparacao` (`docia/comparacao`): `ComparacaoService` + `TextoDiff` (LCS por linhas e palavras, Jaccard de termos, trechos semelhantes, termos exclusivos, metadados iguais/diferentes). Resposta declara `geradoPorIa=false`. Documento sem texto extraído → 400 com mensagem clara; inexistente → 404.
- **Decisões técnicas:** sem migration (lê `DOCUMENTO.TEXTO_EXTRAIDO`); limite de 2000 palavras/1500 linhas por documento (flag `truncado`) para conter a matriz LCS; comparação ignora caixa/acento/pontuação; números curtos contam como termos.
- **Arquivos principais:** `docia/comparacao/*.java`, `ComparacaoServiceTest`, `ContratoOpenApiTest` (novo endpoint), `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: ComparacaoServiceTest 7 testes e ContratoOpenApiTest 14, 0 falhas).
- **Pendências:** sem teste Oracle ponta a ponta contra as versões do parecer no seed (o teste unitário usa texto equivalente).

## 2026-11-11 · GH-25 · Notificações de novas demandas, prazos, atrasos, distribuição e documentos
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** migration `V6__notificacoes.sql` (`NOTIFICACAO`, UK `(USUARIO_ID, CHAVE)`), `core/notificacao` (`NotificacaoService`, controller `GET /api/notificacoes`, `GET /api/notificacoes/nao-lidas/contagem`, `PATCH /api/notificacoes/{id}/lida`), `PrazoNotificacaoService` com `@Scheduled` diário (07:00 America/Cuiaba) e ganchos em `DemandaService.cadastrar`, `DistribuicaoService.distribuir` e `DocumentoService.vincular`.
- **Decisões técnicas:** a Issue cita V5, mas V5 já era da #34, então a migration é V6; anti-duplicidade por chave única (inclui a data para prazo/atraso); "gestores" = GESTOR + ADMIN ativos; atraso notifica fiscal e gestores; autor da ação não é notificado de si mesmo.
- **Arquivos principais:** `core/notificacao/*`, `flux/demanda/PrazoNotificacaoService.java`, `common/AgendamentoConfig.java`, testes `NotificacaoServiceTest`, `PrazoNotificacaoServiceTest`, ajustes em `DemandaServiceTest`/`TriagemDistribuicaoServiceTest`/`DocumentoServiceTest`, `docs/API.md`, `docs/DATABASE.md`.
- **Testes:** a confirmar pelo validador (implementador: testes unitários dos 5 arquivos citados, 0 falhas).
- **Pendências:** JPQL das consultas de prazo/atraso e a migration V6 não foram exercitadas em Oracle (validar com perfil `oracle`/Docker).

## 2026-11-12 · GH-45 · Contrato OpenAPI com resposta 2xx em login e demandas
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `200` declarado com schema em `POST /api/auth/login` (`LoginResponse`), `GET /api/demandas/{id}` (`DemandaDetalhe`), `/historico` (lista de `HistoricoItem`) e `/documentos` (lista de `DocumentoResumo`). Causa: `@ApiResponse` só com códigos de erro suprimia o 200 implícito.
- **Decisões técnicas:** `@ApiResponse(200)` explícito nos controllers; sem mudança de comportamento em runtime.
- **Arquivos principais:** `AuthController`, `DemandaController`, `DemandaDocumentoController`, `ContratoOpenApiTest` (novo teste: toda operação tem 2xx).
- **Testes:** a confirmar pelo validador (implementador: ContratoOpenApiTest, EndpointsContratoWebMvcTest, AuthControllerWebMvcTest, 0 falhas).
- **Pendências:** nenhuma.

## 2026-11-12 · GH-24 · Gestão de usuários, setores e equipes
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `UsuarioController/Service` (`GET/POST/PUT /api/usuarios`, `GET /{id}`, `PATCH /{id}/ativacao`, pesquisa `q` + filtros perfil/ativo, paginado), cadastro/edição de setores (`POST/PUT /api/setores`) e equipes (`POST/PUT /api/equipes`, `PUT /{id}/membros`, responsável). Senha de 8 a 72 caracteres (400), BCrypt, e-mail único (409), proteção do último ADMIN ativo (409 ao desativar ou rebaixar), escrita só ADMIN e consulta ADMIN/GESTOR, auditoria de todas as operações sem senha.
- **Decisões técnicas:** sem migration (tabelas existentes); responsável sempre é membro; PUT de equipe sem `responsavelId` limpa o responsável; membros e responsável precisam estar ativos; `Specification` para a pesquisa (sem problemas de parâmetro nulo no Oracle).
- **Arquivos principais:** `core/usuario/*`, `core/setor/*`, `core/equipe/*` (services, controllers, DTOs de requisição), testes `UsuarioServiceTest`, `UsuarioControllerWebMvcTest`, `SetorCadastroServiceTest`, `EquipeCadastroServiceTest`, `ContratoOpenApiTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: testes afetados sem Oracle, ver relatório).
- **Pendências:** sem teste Oracle da pesquisa `q`/Specification; token de usuário desativado depende do filtro JWT existente.

## 2026-11-12 · GH-28 · Relatórios de demandas com filtros e exportação CSV
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `GET /api/relatorios/demandas` (JSON paginado) e `/demandas.csv` (UTF-8 com BOM, `;`, aviso demonstrativo na linha 1) com filtros período, município, bioma, categoria e status (`intelligence/relatorio`: controller, service, DTOs; consultas novas em `IndicadorRepository`).
- **Decisões técnicas:** sem migration; reaproveita `IndicadorFiltro` e o predicado dos indicadores; `Autorizacao.RELATORIOS` (ADMIN, GESTOR, ANALISTA, CONSULTA; FISCAL → 403, conforme matriz); `size` limitado a 100; CSV neutraliza fórmulas e escapa aspas/`;`.
- **Arquivos principais:** `RelatorioController/Service`, `IndicadorRepository`, `Autorizacao`, testes `RelatorioServiceTest`, `RelatorioControllerWebMvcTest`, `ContratoOpenApiTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: RelatorioServiceTest, RelatorioControllerWebMvcTest e ContratoOpenApiTest, 0 falhas).
- **Pendências:** consulta paginada/CSV não exercitada contra Oracle real; abertura no Excel não verificada manualmente (BOM e `;` cobertos por teste).

## 2026-11-12 · GH-23 · Testes de integração Oracle em job separado
- **PR:** #75 · **Status:** concluído (reforço Opus)
- **Agentes que atuaram:** `implementador-backend`, `validador-qa`, `revisor`, orquestrador (reforço Opus)
- **O que foi feito:** `.github/workflows/oracle-it.yml` (`workflow_dispatch` + cron noturno, `mvn verify -Poracle`, upload dos relatórios); `@Testcontainers(disabledWithoutDocker = true)` nos 8 testes `oracle`, que passam a aparecer como ignorados com mensagem quando não há Docker. Cobertura já existente: migrations/schema (`CoreSchemaOracleTest`), numeração e filtros (`DemandaOracleTest`), busca documental (`DocumentoOracleTest`), agregações (`IndicadorOracleTest`).
- **Decisões técnicas:** perfil Maven `oracle` e exclusão da tag no build padrão já existiam; sem credenciais (container efêmero via Testcontainers); sem secrets no workflow.
- **Arquivos principais:** `.github/workflows/oracle-it.yml`, `*OracleTest.java`, `docs/DEVELOPMENT.md`.
- **Testes:** `mvn verify -Poracle` local: 30 testes, 0 falhas. Job `oracle-it` no GitHub (run 43160648983, gatilho `push` temporário no branch, já removido): 30 testes, 0 falhas. A 1ª execução no runner falhou com `AccessDeniedException: /data` (padrão `STORAGE_DIR=/data/uploads` não gravável) → o workflow define `STORAGE_DIR` em `runner.temp`.
- **Pendências:** nenhuma; após o merge o job também fica disponível via `workflow_dispatch`.

## 2026-11-11 · GH-20 · Smoke test do Docker Compose em workflow manual e noturno
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `compose-smoke.yml` (`workflow_dispatch` + cron noturno): gate do secret `CROSS_REPO_TOKEN` (ausente → aviso + resumo explicativo, sem falha silenciosa), checkout API+Web lado a lado, `.env` com segredos aleatórios, `compose up --build`, espera `healthy` (15 min), login e `GET /api/demandas` em `localhost:3000`, logs como artefato em falha, limpeza final.
- **Decisões técnicas:** segredos do `.env` gerados por `openssl rand` (nada fixo); usuário demo (profile demo) em env do job; limpeza com `down --remove-orphans` (runner efêmero, sem `-v`).
- **Arquivos principais:** `.github/workflows/compose-smoke.yml`, `docs/DOCKER.md`.
- **Testes:** a confirmar pelo validador (implementador: sintaxe YAML validada com PyYAML; execução real do workflow depende do secret e de push).
- **Pendências:** execução verde com o secret só após merge/dispatch no GitHub.

## 2026-11-12 · GH-47 · prazoSugerido no DemandaDetalhe
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** verificado que `DemandaDetalhe.prazoSugerido` (nullable) e seu mapeamento em `DemandaService` já existiam desde o GH-11; adicionados 2 testes unitários de regressão (cadastro e obtenção, com valor e com null).
- **Decisões técnicas:** nenhuma mudança de código de produção; contrato OpenAPI já inclui o campo.
- **Arquivos principais:** `DemandaServiceTest`.
- **Testes:** a confirmar pelo validador (implementador: `mvn -Dtest=DemandaServiceTest test`, 0 falhas).
- **Pendências:** nenhuma.

## 2026-11-12 · GH-83 · Login demonstrativo por perfil (Modo demonstração)
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `POST /api/auth/demo/{perfil}` (`AuthDemoController`, `@Profile("demo")`) devolve `LoginResponse` do primeiro usuário ativo com `DEMO=1` do perfil; `GET /api/auth/config` inclui `perfisDemo` quando `demo=true`. Auditoria `LOGIN` com detalhe "modo demonstração"; perfil inválido 400, sem usuário 404.
- **Decisões técnicas:** rota liberada no `SecurityConfig` (`permitAll`), mas o bean só existe no profile `demo` (404 nos demais); nenhuma senha envolvida; lógica em `AuthService.loginDemo`.
- **Arquivos principais:** `AuthDemoController`, `AuthService`, `PerfilDemo`, `AuthConfigResponse`, `UsuarioRepository`, `SecurityConfig`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: AuthServiceTest, AuthControllerWebMvcTest, AuthDemoControllerWebMvcTest, 0 falhas).
- **Pendências:** nenhuma.

## 2026-11-12 · GH-84 · Endpoints de controle de prazos e de produtividade
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `GET /api/flux/prazos/resumo`, `GET /api/flux/prazos` (por estado, paginado) e `GET /api/flux/produtividade` (KPIs, por setor, tempo médio por setor, histórico mensal, por responsável), todos `demonstrativo: true` e com o escopo de perfil da listagem de demandas (reuso de `DemandaSpecification`).
- **Decisões técnicas:** agregações em memória sobre o recorte já filtrado/escopado (volume demonstrativo), cálculo puro testável sem banco; setor da demanda = setor da equipe; sem migration.
- **Arquivos principais:** `PrazoController/Service`, `ProdutividadeController/Service`, `PrazoDtos`, `ProdutividadeResposta`, `PrazoProdutividadeServiceTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: `PrazoProdutividadeServiceTest`, 10 testes, 0 falhas).
- **Pendências:** conferência contra o seed real (teste Oracle) fica para o validador.

## 2026-11-12 · GH-89 · Data de conclusão e de atualização na listagem de demandas
- **PR:** #94 · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `DemandaResumo` ganhou `concluidaEm` (nullable) e `atualizadoEm`, mapeados em `DemandaService.resumo` e refletidos no OpenAPI; `spring.data.web.pageable.max-page-size` passou de 100 para 500 para o quadro carregar todas as demandas.
- **Decisões técnicas:** limite de 500 é global da paginação (config única); campos aditivos, sem migration.
- **Arquivos principais:** `DemandaResumo`, `DemandaService`, `application.yml`, `DemandaServiceTest`, `ContratoOpenApiTest` (comentário), `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: `DemandaServiceTest`, ContratoOpenApiTest, EndpointsContratoWebMvcTest).
- **Pendências:** nenhuma.

## 2026-11-12 · GH-90 · Filtro por setor nos indicadores, no mapa e nos relatórios
- **PR:** a abrir · **Status:** em validação
- **Executado por:** autopilot (sessão API)
- **O que foi feito:** parâmetro opcional `setorId` (setor da equipe responsável) em `/api/intelligence/*` (resumo, série mensal, por município/bioma/categoria/situação, mapa) e `/api/relatorios/demandas[.csv]`. Demandas sem equipe ficam fora quando informado; sem `setorId` nada muda.
- **Decisões técnicas:** campo `setorId` adicionado ao record `IndicadorFiltro` (compartilhado por todos os endpoints); join interno `demanda.equipe.setor` só quando o filtro é usado; sem migration.
- **Arquivos principais:** `IndicadorFiltro`, `IndicadorRepository`, `DashboardService`, controllers (descrições OpenAPI), `IndicadorOracleTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: IndicadorServiceTest, RelatorioServiceTest, teste Oracle de setor adicionado, não executado sem Docker/Oracle).
- **Pendências:** nenhuma.

## 2026-11-12 · GH-91 · Seed com núcleos regionais de fiscalização fictícios
- **PR:** a abrir · **Status:** em validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** 5 setores `NR-CBA/CAC/ROO/BAG/SNO` (um por região intermediária do IBGE), cada um com 1 equipe e 2 fiscais fictícios em `demo/usuarios.json` (hash BCrypt); a massa passa a distribuir cada demanda à equipe do núcleo da região do município (fiscal = responsável da equipe); 6 municípios de Barra do Garças entram na massa (sem eles a região ficaria vazia); `docs/DEMO.md` atualizado.
- **Decisões técnicas:** usuários/equipes antigos preservados (Alfa a Delta ficam sem demandas da massa); sem migration; `MunicipioRef` ganhou `regiao`; reset do banco demo necessário após o merge (sessão principal).
- **Arquivos principais:** `usuarios.json`, `DemoMassaGerador`, `DemoMassaService`, testes `DemoMassaGeradorTest`, `DemoSeedServiceTest`, `DemoSeedRecursoTest`, `DemoSeedOracleTest`, `docs/DEMO.md`.
- **Testes:** a confirmar pelo validador (implementador: testes unitários do pacote demo e `DemoSeedOracleTest` com Oracle, 0 falhas).
- **Pendências:** `demo-reset` após o merge.

## 2026-11-12 · GH-97 · Seed com conclusões recentes e tempos variados por núcleo
- **PR:** a abrir · **Status:** em validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** as 30 finalizadas do seed passam a ser distribuídas em rodízio pelos 5 núcleos (6 cada); `concluidaEm` relativo ao dia do reset (cerca de metade entre 1 e 25 dias atrás, o resto de 35 dias em diante); recebimento = conclusão − tempo do núcleo (base 6/9/13/16/20 dias + 0 a 2, médias distintas); fiscalização 1 dia antes da conclusão; prazos mistos (no prazo e atrasadas).
- **Decisões técnicas:** sem migration; só `DemoMassaGerador` (determinístico por dia); a numeração DEM-AAAA/NNNN pode mudar.
- **Arquivos principais:** `DemoMassaGerador`, `DemoMassaGeradorTest`, `docs/DEMO.md`.
- **Testes:** a confirmar pelo validador (implementador: testes do pacote demo, 0 falhas).
- **Pendências:** `demo-reset` após o merge (sessão principal).

## 2026-11-12 · GH-76 · Relatório de demandas com demanda sem categoria
- **PR:** (ver GH-76) · **Status:** mesclado pela sessão principal antes da tag `v0.1.0-demo`
- **O que foi feito:** `RelatorioService.linha` usa "Não informada" quando a demanda não tem categoria (caso da demanda cadastrada ao vivo no roteiro, ainda Pendente), evitando o 500 em `/api/relatorios/demandas` e no CSV.
- **Arquivos principais:** `RelatorioService`, `RelatorioServiceTest`.
- **Testes:** `RelatorioServiceTest` e `RelatorioControllerWebMvcTest` (0 falhas, local); CI do PR.

## 2026-11-12 · GH-100 · README: acessar por 127.0.0.1
- **Status:** mesclado pela sessão principal (pedido do proprietário)
- **O que foi feito:** instruções trocam `localhost` por `127.0.0.1` (no Windows do proprietário `localhost` resolve para IPv6 e cai em outro serviço).
- **Arquivos principais:** `README.md`.
- **Testes:** CI do PR.

## 2026-11-12 · GH-102 · Variável ESRI_API_KEY para a visão de satélite
- **Status:** feito pela sessão principal (pedido do proprietário)
- **O que foi feito:** `ESRI_API_KEY` repassada ao serviço `web` no compose (vazia por padrão), entrada comentada no `.env.example` e instruções em `docs/DOCKER.md`/`docs/DEMO.md`. A chave é do proprietário (ArcGIS Location Platform) e fica só no `.env`.
- **Arquivos principais:** `docker-compose.yml`, `.env.example`, `docs/DOCKER.md`, `docs/DEMO.md`.
- **Testes:** `docker compose config` local; CI do PR.

## 2026-11-13 · GH-104 · Skills do Claude Code (API)
- **Status:** feito pela sessão principal (pedido do proprietário)
- **O que foi feito:** skills `preparar-demo`, `verificar-seguranca`, `nova-issue-sema`, `preparar-tag` e `dados-ficticios` em `.claude/skills/`; seção "Skills" em `docs/AGENTES.md` (inclui as do Web, Web#115).
- **Apoio:** `preparar-demo/verificar-ambiente.ps1` (verificação somente leitura: `.env`, chave Esri só pelo formato, containers `sema-nexus-*`, web, API em modo demo, Swagger, tile Esri); `verificar-seguranca/verificar-diff.mjs` (linhas adicionadas + arquivos novos; compara com os segredos do `.env` sem exibi-los).
- **Testes:** `verificar-ambiente.ps1` com o ambiente no ar (saída 0, 11 OK); `verificar-diff.mjs` contra `origin/main` e `v0.1.0-demo` (0 achados) e com arquivo temporário contendo a chave real, `VITE_API_KEY`, senha e CPF (5 bloqueantes e 2 avisos detectados, sem exibir valores; arquivo apagado).

## 2026-11-13 · GH-106 · Infraestrutura de agentes de IA (modelo local + Claude opcional)
- **Status:** feito pela sessão principal (MVP aprovado pelo proprietário: agentes rodando dentro do sistema). Durante a implementação o proprietário informou que a conta institucional não permite chave da Anthropic sem cartão e escolheu **modelo local gratuito** (Ollama).
- **O que foi feito:** pacote `agentes` independente de provedor: `ClienteModelo` (histórico neutro), `OllamaClienteModelo` (padrão; `/api/chat` com ferramentas e chamada final com `format` = esquema JSON), `AnthropicClienteModelo` (opcional; SDK `anthropic-java` 3.3.0, cache do prompt de sistema, structured outputs), `ExecutorAgente` (laço de ferramentas, limite de iterações, falha → modo demonstrativo + auditoria), `GET /api/agentes/status` (LOCAL/CLAUDE/DEMONSTRATIVO). Compose: serviços `ollama` (perfil `ia`, GPU NVIDIA) e `ollama-cpu` (perfil `ia-cpu`), imagem `ollama/ollama:0.40.11`, sem porta no host, `OLLAMA_NO_CLOUD=1`; `scripts/ia-preparar.ps1|sh`; `docs/AGENTES_IA.md`.
- **Decisões técnicas:** modelo `qwen3:4b` (Apache 2.0) em vez de `qwen2.5:3b` (licença de pesquisa não comercial); `think=true` + remoção de `</think>` (com `think=false` o Qwen3 mistura o raciocínio no texto); SDK 3.3.0 (3 semanas) em vez da 3.8.0 (3 dias); sem migration.
- **Ambiente:** download do modelo bloqueado pela inspeção HTTPS do firewall da SEMA (`x509`); com autorização do proprietário, o certificado público da CA do firewall (já confiado pelo Windows) foi adicionado só no container do Ollama para o download e o container foi recriado em seguida (confiança removida, conferido).
- **Testes:** `ExecutorAgenteTest` (6) e `ProvedoresModeloTest` (4: Ollama com servidor HTTP simulado e montagem da requisição do Claude); suíte 331/331. Real: `qwen3:4b` 100% na GPU (GTX 1660 SUPER), chamada de ferramenta correta em ~3,6 s (primeira carga ~48 s).

## 2026-11-13 · GH-107 · Agente de Triagem (SEMA Flux)
- **Status:** feito pela sessão principal (MVP de agentes aprovado pelo proprietário)
- **O que foi feito:** `POST /api/demandas/{id}/triagem/sugestao` (`flux/agente`): `AgenteTriagemService` com 3 ferramentas (`obter_demanda`, `buscar_demandas_semelhantes`, `alertas_inpe_municipio`), validação da resposta (enums, duplicatas só das ferramentas, 3 frases completas), núcleo regional pela região do IBGE e `RegrasTriagem` (modo demonstrativo e reserva em falhas).
- **Ajustes na infraestrutura (#106):** modelo padrão `qwen3:4b-instruct` (o `qwen3:4b` raciocinava ~1.000 tokens por etapa); `OLLAMA_PENSAR` (campo `think` só quando ativado); `DefinicaoAgente.maxRodadasFerramentas` (esgotadas as rodadas, o pedido vai sem ferramentas e com o esquema obrigatório — uma chamada a menos); resposta local só aceita JSON com todos os campos obrigatórios; JSON extraído do primeiro `{` ao último `}`; limites de tamanho no esquema (removidos no provedor Claude, que não os aceita).
- **Ambiente:** novo download (`qwen3:4b-instruct`, 2,5 GB) autorizado pelo proprietário, com o mesmo procedimento temporário do certificado da CA do firewall da SEMA (container recriado em seguida; confiança removida, conferido).
- **Testes:** `AgenteTriagemServiceTest` (8), `ExecutorAgenteTest` (7), `ProvedoresModeloTest` (4); suíte 339/339. Real no Docker: 10 de 10 demandas pendentes com IA local, 7–11 s cada; categorias e núcleos coerentes (ex.: queimada em Cuiabá com 158 focos do INPE → ALTA, NR-CBA).

## 2026-11-13 · GH-108 · Assistente DocIA agêntico
- **Status:** feito pela sessão principal (MVP de agentes aprovado pelo proprietário)
- **O que foi feito:** `AgenteDocia` (ferramentas `buscar_documentos` e `ler_documento`, até 2 rodadas, resposta no esquema JSON) dentro do `AssistenteService` (mesmo endpoint); `BuscaDocumentos` extraída (busca por pontuação compartilhada com o modo extrativo); validação das referências (só documentos vistos pelas ferramentas, título do banco, trecho conferido no texto, ids removidos do texto da resposta); `RespostaAssistente` com `agente`, `modelo` e `passos` (construtor antigo mantido). `perguntar` deixou de ser `readOnly` (a auditoria do agente grava).
- **Testes:** `AgenteDociaTest` (5) e `AssistenteServiceTest` (6, inalterado); suíte completa. Real no Docker: 4 perguntas, 3–12 s, respostas corretas com citação; pergunta sem base → "não há informação suficiente".

## 2026-11-13 · GH-112 · Perfil Fiscal da demonstração sem demandas
- **Status:** corrigido pela sessão principal (relatado pelo proprietário)
- **Causa:** `loginDemo` usava o primeiro fiscal demonstrativo (`fiscal1`, equipes Alfa–Delta), que ficou sem demandas desde os núcleos regionais (#91); a regra de visibilidade do fiscal (equipe ou atribuídas) está correta.
- **O que foi feito:** `AuthService.CONTA_DEMO_PREFERIDA` (FISCAL → `fiscal.cba1@demo.semanexus.local`, Núcleo Cuiabá), com reserva no primeiro usuário demonstrativo do perfil se a conta não existir ou estiver inativa; `docs/DEMO.md` atualizado. Sem mudança de autorização nem de massa (não exige demo-reset).
- **Testes:** `AuthServiceTest` (novo caso: conta preferida e reserva); conferido no Docker.

## 2026-11-13 · GH-114 · Painel do sistema: verificações ao vivo
- **Status:** feito pela sessão principal (pedido e aprovado pelo proprietário: aba "Painel do sistema" só para ADMIN)
- **O que foi feito:** `GET /api/sistema/painel` (`core/sistema`): `PainelSistemaService` com verificações isoladas (API, Oracle, IA dos agentes, INPE, armazenamento) e estado OK/ATENCAO/INDISPONIVEL; `MetricasAgentes` (memória) alimentada pelo `ExecutorAgente` (sucesso, falha, tempo) e pelos modos demonstrativos (triagem por regras, DocIA extrativo); `ClienteModelo.diagnostico()` (Ollama: versão, modelos instalados e carregados com % na GPU; Claude: só chave configurada sim/não).
- **Testes:** `PainelSistemaServiceTest` (2), `PainelSistemaControllerWebMvcTest` (2: só ADMIN, 401/403); suíte 349/349. Docker: 5 verificações OK, Ollama 0.40.11 com o modelo 100% na GPU, métricas da triagem registradas; gestor → 403.

## 2026-11-13 · GH-116 · CLAUDE.md com as skills e os agentes de IA
- **Status:** feito pela sessão principal (pedido do proprietário)
- **O que foi feito:** seções "Agentes de IA no sistema" (pacote `agentes`, provedores ollama/anthropic/demo, Triagem e DocIA com suas ferramentas, regras: só consulta, nunca grava nem decide, chaves só no `.env`, aquecer o modelo antes da demonstração) e "Skills do Claude Code" (as 5 skills deste repositório e o aviso de que não autorizam nada além do `CLAUDE.md`); `docs/AGENTES_IA.md` na lista de detalhes.
- **Testes:** só documentação; nomes de classes, endpoints, ferramentas e scripts conferidos no código.

## 2026-11-16 · GH-118 · O sistema passa a se chamar SEMA ALERTA
- **Status:** feito pela sessão principal (decisão do proprietário: renomear tudo o que é visível e os e-mails demonstrativos; identificadores técnicos e repositórios ficam como estão)
- **O que foi feito:** "SEMA NEXUS" → "SEMA ALERTA" no OpenAPI, nas instruções dos agentes de IA, nos scripts, no `pom.xml` (nome/descrição; o `artifactId` continua `sema-nexus-api`), no README, no `CLAUDE.md`, nas skills/agentes do Claude Code e em `docs/`. E-mails demonstrativos `@demo.semanexus.local` → `@demo.semaalerta.local` no seed, no `AuthService` e nos exemplos; `DemoSeedService.migrarDominioAntigo()` renomeia no banco existente os usuários demonstrativos do domínio antigo antes da carga (mantém id, senha, equipes, demandas e auditoria; não duplica). Migrations já mescladas e as entradas anteriores deste diário não foram alteradas (registros históricos).
- **Testes:** `DemoSeedServiceTest.migraEmailsDoDominioAntigoSemDuplicarUsuarios`; suíte 356/356.

## 2026-11-16 · GH-120 · Agentes de IA mais rápidos mantendo a qualidade
- **Status:** feito pela sessão principal (pedido do proprietário: opção A — mesmo modelo local, mais rápido e com qualidade)
- **Medição de partida:** DocIA 11,3 s de média em 5 perguntas, 2 delas falhando (~18 s e modo demonstrativo); Triagem 9,4 s; modelo descarregado após 30 min (+30–50 s).
- **Causas (reproduzidas direto no Ollama):** limite de 2 rodadas contra instruções de 3 (o modelo gerava 900 tokens vazios na etapa final); resposta escrita duas vezes (texto + JSON); trechos longos copiados para citações; busca sem radical e trecho de amostra fraco levando ao documento errado.
- **O que foi feito:** `DefinicaoAgente.campoTexto`/`PedidoModelo.aceitaTexto` (resposta em texto livre sem reescrita); pedido explícito "não chame mais ferramentas" na etapa final do Ollama; DocIA com 3 rodadas, resposta `{resposta, suficiente}`, citações montadas pela API, `buscar_documentos` com radical, peso do título, corte dos pouco relevantes e texto do mais relevante já incluído; frases de aviso sem dado removidas da resposta; Triagem com justificativa de até 220 caracteres e 400 tokens; `AquecimentoModelo` + `OLLAMA_MANTER_CARREGADO` (4 h) + estado do aquecimento no diagnóstico do Painel.
- **Resultado (Docker, GTX 1660 SUPER):** DocIA ~3 s (9 perguntas, 2 rodadas iguais: 8 corretas com o documento certo + "não há informação" na sem resposta); Triagem 7,2 s com as mesmas classificações; modelo aquecido 10–50 s após subir a API.
- **Testes:** novos `BuscaDocumentosTest` (2), casos em `AgenteDociaTest` (6) e `ProvedoresModeloTest` (3); suíte 367/367.

## 2026-11-17 · GH-122 · demo-reset preserva o modelo de IA
- **Status:** feito pela sessão principal (encontrado antes do reset pedido pelo proprietário para a gravação do vídeo)
- **Problema:** `demo-reset` usava `docker compose down -v`, que remove todos os volumes do compose — desde a #106 inclusive `ollama` (modelo de IA, ~2,5 GB), cujo novo download esbarra na inspeção TLS da rede da SEMA.
- **O que foi feito:** `demo-reset.ps1`/`.sh` fazem `docker compose down` e removem só `sema-nexus_oradata` e `sema-nexus_uploads`; teste com docker falso exige `down` sem `-v`, remoção só desses volumes e nenhuma menção a `ollama`; `docs/DOCKER.md` atualizado.
- **Testes:** `demo-scripts.test.sh` 43/43 (bash). Reset real: modelo mantido (`ollama list`), seed recriado (120 demandas, 12 documentos, 16 usuários), modelo reaquecido em 25 s.

## 2026-11-17 · GH-124 · Aquecimento com geração: primeira resposta de IA sem espera longa
- **Status:** feito pela sessão principal (problema observado na gravação do vídeo de demonstração; correção autorizada pelo proprietário)
- **Causa (medida no Ollama):** custo único de inicialização da GPU na 1ª leitura de texto grande após carregar o modelo (1.203 tokens: 21,3 s na 1ª chamada, 5,0 s na 2ª). Flash Attention testado desligado: não é a causa (mantido no padrão).
- **O que foi feito:** `OllamaClienteModelo.aquecer()` faz, depois de carregar, uma geração curta (`num_predict` 8) com ~1.000 tokens de entrada fictícios e a mesma janela de contexto; docs atualizados.
- **Testes:** `ProvedoresModeloTest` (aquecimento espera a geração); suíte 367/367. Docker: modelo descarregado + reinício da API → aquecimento ~60 s em segundo plano; 1ª triagem 12,2 s (antes 28–36 s), 2ª 8,5 s, 1º DocIA 4,9 s com resposta correta.

## 2026-11-20 · GH-126 · Melhorias do relatório técnico: integração, aderência textual, erros e orçamento dos agentes
- **Status:** commit local na branch `feature/GH-126` (sem push/PR; autorizado pelo proprietário)
- **Executado por:** Claude Code, a pedido do proprietário (relatório técnico da Formação Claude Code, projeto 14, que analisou API `caa0da6` + Web `03494762`).
- **O que foi feito:** catálogo `ErroFerramenta` (modelo, passos e auditoria recebem só código + mensagem pública + referência; `e.getMessage()` só no log); orçamento total por execução (`AGENTES_ORCAMENTO_SEGUNDOS` 150, `AGENTES_MAX_CHAMADAS_FERRAMENTA` 8) e auditoria com latência e chamadas; "relevância" → **aderência textual** na busca, na ferramenta e em `referencias[].aderenciaTextual`; citações da DocIA pelo **suporte à resposta**; avaliação adversarial `AvaliacaoAgentesTest`; skill `validar-integracao-sema`; `docs/VALIDACAO_INTEGRADA.md`; matriz Implementado/Parcial/Planejado no README; fronteira agentes de desenvolvimento × aplicação em `docs/AGENTES.md`.
- **Decisões técnicas:** erro classificado pelo **tipo** da exceção, nunca pela mensagem; orçamento conferido antes de cada chamada ao modelo (pior caso = orçamento + 1 timeout); citação exige um número da resposta quando houver documento com ele (senão, termos), com cobertura de números para comparações de versões; hooks, MCP próprio e busca semântica ficam **planejados** (sem necessidade concreta agora).
- **Testes:** `mvn -B -ntp -Dspring-boot.repackage.skip=true verify` → 376 testes, 0 falhas (eram 367). O *repackage* local foi pulado porque o Windows bloqueou `target/sema-nexus-api.jar`; a imagem Docker empacota normalmente.
- **Docker:** validado — `validar-integracao.mjs --fluxo` 35/35; navegador (Playwright) 9/9; avaliação DocIA 10/10 (antes da correção das citações: 9/10, com 1 citação errada e 5 excedentes); falha real com `docker compose stop ollama` → modo demonstrativo identificado. `demo-reset` no fim (seed restaurado).
- **Pendências / observações:** a validação integrada não roda no CI; provedor Anthropic não validado (sem chave); com duas notas adversariais no acervo o modelo se absteve na pergunta de Colniza; o modo extrativo (sem IA) pode exibir trechos de documento adversarial.

## 2026-11-24 · GH-128 · Área afetada (hectares) e detalhe de focos, alertas DETER e demandas no mapa
- **Status:** PR aberto a pedido do proprietário (Issue, revisão, validação, PR e merge autorizados em 24/11/2026). Web#133 depende desta Issue.
- **Executado por:** Claude Code (subagente Opus, esforço médio), a pedido do proprietário.
- **O que foi feito:** snapshot DETER em GeoJSON (`deter_mt.geojson.gz`, 1.538 alertas, 13/09–29/10/2026) com polígono e todos os atributos; focos regenerados a partir do arquivo **mensal** do INPE (o diário só fica ~30 dias no servidor) — 18.455 focos (+36 do consolidado; os 18.419 anteriores estão contidos) com dias sem chuva, precipitação e risco de fogo; `V7__area_afetada.sql`; áreas fictícias determinísticas nas demandas `[DEMO-SEED]` de desmatamento, queimada, mineração e APP; `GET /api/intelligence/referencia/pontos/{id}`; `Ponto.areaHa`, `DemandaDetalhe.areaHa/areaGeometria`.
- **Decisões técnicas:** área só onde a fonte informa — DETER usa `areamunkm` (recorte por município, o polígono desenhado; no Cerrado `areatotalkm` é o alerta inteiro e só fica nos atributos), ha = km² × 100; foco de calor é ponto no BDQueimadas e fica "área não informada pela fonte" (nada inventado); novo snapshot substitui a importação anterior do mesmo tipo (antes duplicava); alertas do Cerrado associados ao município pelo nome da fonte; permissões inalteradas.
- **Testes:** `mvn verify` → 400 testes, 0 falhas (eram 376), incluindo 18/18 com Oracle real (`AreaAfetadaOracleTest`); o *repackage* local falhou só porque um processo Java externo segurava `target/sema-nexus-api.jar`.
- **Docker:** validado — `demo-up` sem `demo-reset`: V7 aplicada, snapshots substituídos sem duplicar; `validar-integracao.mjs` 15/15 (contrato da Web idêntico ao da API); polígono DETER, painel do foco e área fictícia conferidos no navegador, com a visão de satélite Esri.
- **Revisão independente (antes do merge):** sem bloqueios; corrigidos 3 achados — área do Cerrado vinha de `areatotalkm` (8 de 279 alertas não batiam com o polígono; agora `areamunkm` + `V8__area_deter_recorte.sql` para bancos já carregados), schemas das geometrias saíam como `string` no OpenAPI (agora `object`), e snapshot vazio ou malformado apagaria o anterior (agora é recusado e o anterior fica).
- **Pendências / observações:** seleção do mapa não é limpa ao desligar a camada/trocar filtros e o painel não aparece em tela cheia (melhorias futuras); licenças do TerraBrasilis seguem com a ressalva "confirmar os termos vigentes" em `docs/DATABASE.md`.

## 2026-11-24 · GH-149 · Hooks PreToolUse de proteção (volumes, .env, migrations)
- **Status:** PR aberto (subagente Claude Code, a pedido do proprietário).
- **O que foi feito:** `.claude/hooks/proteger.mjs` + registro em `.claude/settings.json`; bloqueia `down -v/--volumes`, `docker volume rm/prune`, `docker system prune`, leitura do `.env` (não `.env.example`) e edição de migration já existente na `origin/main` (nova continua permitida); `docs/AGENTES.md` atualizado.
- **Testes:** `node .claude/hooks/testar-hooks.mjs` → 39/39 (bloqueio e caso permitido por regra, Bash e PowerShell).

## 2026-11-24 · GH-140 · DOCX/OCR/tabelas e pré-análise no upload (PE-09A-API)
- **Status:** PR solicitado (subagente Claude Code, a pedido do proprietário).
- **O que foi feito:** `TextExtractor` extrai texto e tabelas (Markdown) de `.docx` (Apache POI 5.3.0, Apache 2.0) e tabelas de PDF (PDFBox, por lacunas de posição entre colunas); OCR opcional por Tesseract para PDF sem camada de texto (`TESSERACT_CMD`/`TESSERACT_LANG`, detecção em tempo de execução, até 10 páginas, 60 s por página); sem o binário devolve aviso e o upload segue normalmente. `POST /api/documentos/pre-analise` (multipart `arquivo`; mesmos perfis do upload, CONSULTA recebe 403) devolve título, município (maior citação), data, palavras-chave e vínculos `DEM-AAAA/NNNN` sugeridos por regras determinísticas (`PreAnaliseRegras`) e avisos; nada é gravado. Dockerfile instala `tesseract-ocr` + `tesseract-ocr-data-por` (Apache 2.0) no runtime.
- **Decisões técnicas:** a API não tinha motor de análise a reaproveitar (a Web tem `analiseEngine.js`), então as regras ficaram no backend, pequenas e testadas; OCR nunca derruba o upload; migration: nenhuma.
- **Testes:** `TextExtractorTest` (DOCX, tabela de PDF, caminho sem Tesseract, regras da pré-análise) e contrato 200/403/401 do endpoint; `mvn verify` local: 406 testes, 0 falhas (eram 400). OCR real com o binário a confirmar no Docker (validador).

## 2026-11-24 · GH-141 · Busca híbrida Oracle 23ai VECTOR e benchmark
- **Status:** PR solicitado (subagente implementador, a pedido do proprietário).
- **O que foi feito:** migration `V10__documento_trecho.sql` (`DOCUMENTO_TRECHO` com `VECTOR(768, FLOAT32)`); pacote `docia/vetorial` (configuração, cliente Ollama `/api/embed` com `nomic-embed-text`, `Fatiador`, `TrechoRepositorio` com `VECTOR_DISTANCE` cosseno, `IndexadorTrechos` no upload e preenchimento idempotente, `BuscaVetorialOracle`); `BuscaDocumentos.pesquisar` com fusão RRF e limiar semântico; `recuperacao` (LEXICAL/HIBRIDA) e `similaridadeSemantica` na resposta do Assistente; novo `GET /api/documentos/busca` (`modo=AUTO|LEXICAL`); benchmark offline (`BenchmarkBuscaTest`) e `scripts/benchmark-busca.{ps1,sh}`; `scripts/ia-preparar.*` baixam o modelo de embeddings; compose e `.env.example`.
- **Decisões técnicas:** busca vetorial exata (sem índice HNSW/IVF: acervo pequeno, sem `VECTOR_MEMORY_SIZE`); vetor enviado como CLOB a `TO_VECTOR`; documento só-vetorial exige similaridade mínima (abstenção); degradação silenciosa para lexical (pausa de 30 s após falha); embeddings fora da fila da GPU; instruções e ferramentas do agente inalteradas; evita `ExecutorAgente` (#135).
- **Arquivos principais:** `db/migration/V10__documento_trecho.sql`, `docia/vetorial/*`, `docia/assistente/{BuscaDocumentos,AssistenteService,AgenteDocia}.java`, `docia/documento/PesquisaDocumentos*`, `scripts/benchmark-busca.*`, `scripts/ia-preparar.*`, `docs/{API,DATABASE,AGENTES_IA}.md`.
- **Testes:** implementador — unitários afetados e novos sem Oracle: 0 falhas (contagens no PR); benchmark offline: LEXICAL precisão@3 0,80 / acerto@1 0,93 × HIBRIDA (embeddings falsos) 0,71 / 1,00, abstenção 0,75 nos dois. `DocumentoTrechoOracleTest` (tag `oracle`) a confirmar pelo validador (implementador sem Docker).
- **Pendências:** benchmark com o nomic-embed-text real (Docker) e calibração de `EMBEDDINGS_SIMILARIDADE_MINIMA`; Web#145 mostra `recuperacao`.

## 2026-11-24 · GH-142 · Resumo semântico das diferenças na comparação
- **Status:** PR solicitado (subagente implementador, a pedido do proprietário).
- **O que foi feito:** `POST /api/docia/comparacao/explicar` (`aId`, `bId`; mesma autorização da comparação). Reaproveita `ComparacaoService` (acesso aos dois documentos, 404/400, similaridade) e `GrandezasExtrator` extrai por regex áreas (ha), vazões, valores R$, datas, prazos, percentuais e conclusões, pareando por categoria. Resumo curto pelo agente `comparacao` (`ExecutorAgente`, somente leitura, orçamento e auditoria; tabela entregue como dado) com fallback por regras identificado como `DEMONSTRATIVO`; `modo` LOCAL/CLAUDE/DEMONSTRATIVO.
- **Decisões técnicas:** números da tabela nunca vêm do modelo; sem ferramenta (modelo 4B se confunde sem parâmetros; a tabela vai na mensagem); sem migration; nada é gravado.
- **Arquivos principais:** `docia/comparacao/{ExplicacaoComparacaoService,GrandezasExtrator,ComparacaoController}.java`, `dto/ExplicacaoComparacaoResponse.java`, `ExplicacaoComparacaoServiceTest`, `ContratoOpenApiTest`, `docs/API.md`, `docs/AGENTES_IA.md`.
- **Testes:** a confirmar pelo validador (implementador: ExplicacaoComparacaoServiceTest, ComparacaoServiceTest e ContratoOpenApiTest sem Oracle: 0 falhas).
- **Pendências:** qualidade do resumo com Ollama real não exercitada (sem Docker).

## 2026-11-24 · GH-134 · Fila de GPU, rate limiting e revogação de JWT
- **Status:** PR aberto (pacote de evoluções autorizado pelo proprietário em 24/11/2026, frente 13A). Web#152 depende desta Issue.
- **Executado por:** Claude Code (subagente implementador, Opus), a pedido do proprietário.
- **O que foi feito:** `FilaGpu` (`Semaphore` justo, `AGENTES_GPU_CONCORRENCIA`=1, `AGENTES_GPU_ESPERA_SEGUNDOS`=20) em toda chamada do `ExecutorAgente` ao modelo; `LimitadorRequisicoes` + `LimiteRequisicoesFilter` (janela fixa em memória; login por IP, IA e upload por usuário; 429 `problem+json` com `Retry-After`, `categoria` e `retryAfterSegundos`; `LIMITE_*` no `.env.example` e no compose); `jti` obrigatório na validação do JWT, `V9__token_revogado.sql`, `RevogacaoTokenService` (gravação idempotente, cache local, limpeza diária dos expirados) e `POST /api/auth/logout` revogando o token na hora; 429 documentado automaticamente no OpenAPI pelas mesmas regras do filtro.
- **Decisões técnicas:** fila cheia → modo demonstrativo identificado (mesma regra de qualquer falha de IA), sem novo status HTTP; revogação no Oracle (sobrevive a reinício) e sem FK para `USUARIO` (registro temporário); `X-Forwarded-For` só vale vindo de endereço privado/local (Nginx do Web); contadores do limite em memória (uma instância na demonstração; zeram ao reiniciar); filtros opcionais apenas nas fatias `@WebMvcTest` (sem banco).
- **Testes:** novos `FilaGpuTest` (5), `LimitadorRequisicoesTest` (4), `LimiteRequisicoesWebMvcTest` (1), `RevogacaoTokenServiceTest` (5), casos em `JwtServiceTest` (2), `AuthControllerWebMvcTest` (token revogado → 401) e `AuthOracleTest` (logout revoga; mesmo token → 401). Suíte completa: a confirmar pelo validador.
- **Correções da revisão (sem bloqueios, 4 itens):** `X-Forwarded-For` só de proxies configurados (`LIMITE_PROXY_CONFIAVEL`, padrão `web` no compose; vazio = loopback) e porta da API publicada só em `127.0.0.1`; teto de chaves vivas (`LIMITE_MAX_CHAVES`) com descarte amortizado das vencidas, sem varredura por requisição; logouts simultâneos sem 500 (`MERGE` em `REQUIRES_NEW`, auditoria uma vez); aquecimento do modelo pela `FilaGpu`. Trade-offs registrados em `docs/API.md` (custo da consulta de revogação por requisição; cache antes do commit como falha segura).
- **Pendências / observações:** Web#152 trata o 429 na interface; com mais de uma instância da API o limite precisaria de armazenamento compartilhado.

## 2026-11-24 · GH-132 · Ferramentas geoespaciais por raio/polígono no Agente de Triagem
- **Status:** PR solicitado (subagente implementador, pacote de evoluções de 24/11/2026, frente 1). Web#136 consome o novo campo `espacial`.
- **O que foi feito:** ferramentas `alertas_inpe_raio_coordenada`, `demandas_proximas_coordenada` e `documentos_mesma_localidade` no `AgenteTriagemService`; `AnaliseEspacial` (focos/DETER/demandas/documentos), `common/geo/Geodesia` (Haversine, caixa, ponto em polígono e distância ao polígono), `DemandaService.proximas` (escopo do FISCAL), consulta por caixa em `REFERENCIA_SATELITE`; `RegrasTriagem` usa proximidade quando há coordenada; `SugestaoTriagem.espacial` e `Duplicata.distanciaKm` (campos novos, nulos quando não aplicável).
- **Decisões técnicas:** pré-filtro por caixa envolvente em SQL e refinamento em Java (centroide DETER com margem de 30 km); sem migration; a evidência espacial é calculada pela API com raio padrão (10 km), não pelo que o modelo pedir; documentos não têm coordenada, então a proximidade vem das demandas vinculadas.
- **Testes:** novos `GeodesiaTest` (5), `AnaliseEspacialTest` (7), 2 em `DemandaServiceTest`, 2 em `AgenteTriagemServiceTest` (sem Oracle: 0 falhas). SQL novo não exercitado em Oracle real (sem Docker): a confirmar pelo validador.
- **Pendências:** teste `oracle` das consultas por caixa e `FETCH FIRST ? ROWS ONLY`; mocks/OpenAPI da Web#136.

## 2026-11-24 — GH-133 · Assistente DocIA contextualizado na demanda e multi-turno
- **PR:** (este PR) · **Status:** aguardando validação
- **Executado por:** agente implementador (Claude Code)
- **O que foi feito:** `POST /api/docia/perguntas` ganhou `demandaId` e `historico` opcionais (compatível). `demandaId`: autorização pelo escopo da demanda (403/404) e busca/leitura restritas aos documentos vinculados (também no modo extrativo). `historico`: sanitizado e limitado (`HistoricoConversa`), enviado em bloco delimitado de dados; instruções do agente proíbem obedecê-lo.
- **Decisões técnicas:** restrição estrita (não só priorização) por ser verificável; histórico ignorado no modo extrativo; citações seguem conferidas só nos documentos lidos; sem migration.
- **Arquivos principais:** `docia/assistente/{AssistenteService,AgenteDocia,BuscaDocumentos,AssistenteController,HistoricoConversa}.java`, `dto/{PerguntaRequest,TurnoHistorico}.java`, `AssistenteContextoTest`, `docs/API.md`, `docs/AGENTES_IA.md`.
- **Testes:** `mvn -Dtest=AssistenteContextoTest,AgenteDociaTest,AssistenteServiceTest,BuscaDocumentosTest test` → verde (7 novos: contexto da demanda, leitura fora da demanda, 403/404, demanda sem documentos, compatibilidade, injeção no histórico, truncamento). Suíte completa: a confirmar pelo validador.
- **Docker:** não se aplica nesta etapa (validador).
- **Pendências:** Web#137 consome os campos novos; contrato OpenAPI atualizado por anotações.

## 2026-11-24 · GH-130 · Agente de Distribuição e balanceamento de equipes
- **Status:** PR solicitado (pacote de evoluções autorizado pelo proprietário em 24/11/2026, frente 5). API#131 e Web#140 dependem desta Issue.
- **Executado por:** Claude Code (subagente implementador, Opus), a pedido do proprietário.
- **O que foi feito:** `POST /api/demandas/{id}/distribuicao/sugestao` (ADMIN, GESTOR; só AGUARDANDO_DISTRIBUICAO, pela `DemandaStateMachine`): `AgenteDistribuicaoController`/`AgenteDistribuicaoService` com 3 ferramentas somente leitura e parametrizadas (`carga_trabalho_fiscais`, `demandas_ativas_na_regiao`, `tempo_medio_categoria`) sobre `ConsultasDistribuicao` (transação de leitura, registros sem entidades); validação da sugestão da IA (equipe ativa, fiscal membro ativo, prazo entre hoje e hoje + 180 dias) com reserva determinística `RegrasDistribuicao` (modo DEMONSTRATIVO identificado, auditoria `AGENTE_DEMONSTRATIVO`); registro no Painel do sistema e em `GET /api/agentes/status`; rota na categoria `IA` do `LimiteRequisicoesFilter`.
- **Decisões técnicas:** sem migration; o fluxo não tem redistribuição, então só AGUARDANDO_DISTRIBUICAO; sugestão inválida da IA descarta a sugestão inteira (não mistura campos da IA e das regras); tarefa ao modelo sem título/relato da demanda (prompt injection); reaproveita o modelo da triagem (sem nova variável de ambiente); agente novo no fim das listas do painel/status (ordem dos existentes preservada).
- **Testes:** novos `AgenteDistribuicaoServiceTest` (10), `RegrasDistribuicaoTest` (5), `AgenteDistribuicaoControllerWebMvcTest` (3); casos em `LimitadorRequisicoesTest` e `ContratoOpenApiTest`. Afetados rodados localmente sem falhas; suíte completa: a confirmar pelo validador.
- **Correções da revisão (sem bloqueios, 8 itens):** fiscal da IA validado pela mesma preferência das regras (perfil FISCAL); equipe validada contra as candidatas do núcleo; `ConsultasDistribuicao` em JPQL com projeções, `COUNT/SUM` agrupados, ordem e limite no banco e membros numa só consulta (sem N+1); `RuntimeException` na etapa de IA → regras com aviso (sem 500, detalhe só no log); ampliação para todos os núcleos explícita na ferramenta, passos e justificativa; justificativa limitada a 400 (igual ao esquema); GESTOR sem restrição de setor (igual ao `distribuir`). Merge da `main` (conflito no padrão `IA`/`UPLOAD` do `LimiteRequisicoesFilter` resolvido mantendo os dois). Novos casos no `AgenteDistribuicaoServiceTest` (14) e `ConsultasDistribuicaoOracleTest` (tag `oracle`); `mvn verify` local: 463 testes, 0 falhas.
- **Pendências / observações:** `ConsultasDistribuicaoOracleTest` não rodou localmente (Testcontainers/Docker não autorizado nesta etapa) — rodar com `-Poracle`; qualidade da sugestão com Ollama real a medir no Docker.

## 2026-11-24 · GH-135 · Streaming SSE de passos e respostas dos agentes
- **Status:** PR solicitado (pacote de evoluções autorizado pelo proprietário em 24/11/2026, frente 3). Web#138 depende desta Issue.
- **Executado por:** Claude Code (subagente implementador), a pedido do proprietário.
- **O que foi feito:** callback `ObservadorAgente`/`EventoAgente` no `ExecutorAgente` (sobrecarga; a antiga usa o no-op `NENHUM`); `TransmissaoAgente` (SseEmitter, pool próprio com contexto de segurança, `:keepalive`, timeout ≥ orçamento, cancelamento na desconexão); endpoints `POST /api/demandas/{id}/triagem/sugestao/stream` e `POST /api/docia/perguntas/stream` com eventos `inicio`, `ferramenta_iniciada`, `ferramenta_concluida`, `fallback_demonstrativo`, `delta_texto`, `concluido` e `erro`; `/stream` na categoria IA do `LimiteRequisicoesFilter`; despacho ASYNC liberado na `SecurityConfig`; `FilaGpu` com espera cancelável.
- **Decisões técnicas:** validações (404/403/409/400) antes de abrir o stream, para saírem como `problem+json`; `delta_texto` único com o texto final (clientes de modelo não transmitem tokens — documentado); motivos de fallback e erro públicos e fixos; espera fatiada só quando há observador (endpoints JSON mantêm a ordem de chegada da fila); sem migration.
- **Arquivos principais:** `agentes/{ExecutorAgente,FilaGpu,EventoAgente,ObservadorAgente,ExecucaoCanceladaException,TransmissaoAgente}.java`, `flux/agente/AgenteTriagem{Controller,Service}.java`, `docia/assistente/{AssistenteController,AssistenteService,AgenteDocia}.java`, `security/{LimiteRequisicoesFilter,SecurityConfig}.java`, `StreamAgentesWebMvcTest`, `docs/API.md`, `docs/AGENTES_IA.md`.
- **Testes:** novos `StreamAgentesWebMvcTest` (8: ordem dos eventos, fallback, erro sanitizado, 401 sem token e com token na URL, 403, 404 antes do stream, 429, desconexão), casos em `ExecutorAgenteTest` (4), `FilaGpuTest` (1), `AgenteTriagemServiceTest` (2), `AssistenteServiceTest` (1), `LimitadorRequisicoesTest`. `mvn -B -ntp -q verify` → 517 testes, 0 falhas, 0 erros (local).
- **Docker:** não se aplica nesta etapa (validador).
- **Pendências / observações:** Nginx do Web não precisa de mudança (ver `docs/AGENTES_IA.md`); Web#138 consome com `fetch` + `ReadableStream` (JWT no cabeçalho).

## 2026-11-24 · GH-137 · Agente Copiloto de Minuta de relatório de vistoria (PE-06-API)
- **Status:** PR solicitado (subagente implementador, pacote de evoluções autorizado pelo proprietário em 24/11/2026, frente 6). Web#141 depende desta Issue.
- **O que foi feito:** `AgenteMinutaService` + `AgenteMinutaController` (`POST /api/demandas/{id}/minuta-vistoria`; ADMIN, GESTOR, ANALISTA e FISCAL designado), ferramentas somente leitura `obter_dossie_demanda`, `ler_registro_fiscalizacao`, `resumo_documentos_vinculados`, `alertas_inpe_area`; minuta Markdown em 6 seções sempre encabeçada por "MINUTA GERADA PARA REVISÃO HUMANA — AMBIENTE DEMONSTRATIVO"; template determinístico (DEMONSTRATIVO); registro em `AgentesController`, `PainelSistemaService` e na categoria IA do `LimiteRequisicoesFilter`; `Autorizacao.ADMIN_GESTOR_ANALISTA_FISCAL`.
- **Decisões técnicas:** fatos montados pela API e IA só nas sínteses/conclusões; números da IA conferidos com os dados das ferramentas chamadas e barreira de conteúdo fora do escopo (sanção, R$, culpa, eco de instrução), porque números plantados num relato malicioso passariam pela conferência; textos de terceiros delimitados como dado; fiscal designado = `demanda.fiscal` (mesma regra de `ExecucaoSuporte`); INPE por município (#132 não mesclado); sem migration; nada é gravado.
- **Arquivos principais:** `flux/agente/{AgenteMinutaService,AgenteMinutaController,MinutaVistoria}.java`, `AgenteMinutaServiceTest`, `AgenteMinutaControllerWebMvcTest`, `docs/API.md`, `docs/AGENTES_IA.md`.
- **Testes:** `mvn -B -ntp verify` local (sem Oracle) → 476 testes, 0 falhas (após a revisão); novos `AgenteMinutaServiceTest` (13: inclui número plantado no relato, ordem de pagamento barrada, links/imagens neutralizados; minuta com IA e números conferidos, número inventado → template, injeção no relato, delimitação, sem IA, falha da IA, nada gravado, fiscal designado × não designado, parâmetro de outra demanda, formatos pt-BR) e `AgenteMinutaControllerWebMvcTest` (8: 401, ADMIN/GESTOR/ANALISTA/FISCAL 200, CONSULTA 403, fiscal não designado 403, 404), mais casos em `LimitadorRequisicoesTest`, `PainelSistemaServiceTest`, `ExecutorAgenteTest` e `ContratoOpenApiTest`.
- **Correções da revisão (sem bloqueios):** `texto()` remove URLs e escapa `[ ] ( ) !`; números permitidos só de campos estruturados (texto livre de terceiros não autoriza números); barreira ampliada (reais, sanção, penalidade, embargo, responsabiliz…) e sem "instruções"; `limpar()` remove cercas ```; histórico confirmado `desc` (ficam os 15 mais recentes).
- **Após o QA (aprovado, 3/3 LOCAL):** merge da `main` (#157 e #158; listas de agentes mantidas); `alertas_inpe_area` usa a `AnaliseEspacial` do #132 com coordenada/área (fallback por município); conferência das datas-chave (recebimento, fiscalização, conclusão, prazo) no texto da IA, motivada por "finalizada em 24 de novembro" com conclusão em 19/11; parênteses do histórico sem escape.
- **Após o 2º QA (IA caía no template em ~3 de 4 chamadas):** a IA não escreve datas (marcadores `{data_*}`/`{prazo}` substituídos pela API; conferência de datas mantida como rede de segurança); termos graves descartam o texto, medidas administrativas só removem a frase (template se sobrar pouco); termo logado; "( 3)" → "(3)".
- **Após o 3º QA (6/6 no template por data):** diagnóstico com saída real do `qwen3:4b-instruct` no Docker (WARN com o trecho): o modelo ignora os marcadores e copia datas de eventos — "triagem concluída em 14 de novembro" (correta) era lida como conclusão da demanda (19/11). Correção: a data junto de um marco vale se for a registrada de algum marco citado no trecho ou a de um evento do histórico que cita o mesmo marco; datas só isoladas (sem casar pedaços de `DEM-2026/0118`); datas ISO da IA exibidas como dd/mm/aaaa. Medição (6 chamadas: ANALISTA 118 ×4, FISCAL designado 118, ANALISTA 115): antes 1 LOCAL / 5 template (4 por data, 1 sem resposta da IA); depois **5 LOCAL / 1 template**, ~15 s cada — o único template foi a IA escrever "finalização da demanda pelo gestor em 24 de novembro" (registrada 19/11), barrada corretamente.
- **Pendências:** nenhuma de código.

## 2026-11-24 · GH-138 · Sentinela Territorial e Briefing Executivo (PE-07-API)
- **Status:** PR solicitado (subagente implementador, pacote de evoluções autorizado pelo proprietário em 24/11/2026, frente 7). Web#142 depende desta Issue.
- **O que foi feito:** pacote `intelligence/sentinela`: `SentinelaTerritorialService`, `SentinelaController` (`GET /api/intelligence/sentinela/briefing`; ADMIN, GESTOR), `RegrasSentinela` (regras puras), `ConsultasSentinela` (SQL/JPQL agregados, só leitura), `SentinelaAgendador` (`SENTINELA_CRON`, padrão 07:00 America/Cuiaba; `SENTINELA_AGENDAMENTO_ATIVO=false` desliga), `ParametrosSentinela`/`SentinelaConfig` (`SENTINELA_*` no compose e no `.env.example`). Anomalias `INPE_SEM_DEMANDA`, `FILA_ACUMULADA` e `EQUIPE_SOBRECARREGADA` com severidade, números e ações (`FILTRAR_MAPA`, `FILTRAR_DEMANDAS`, `CRIAR_DEMANDA_MONITORAMENTO` com município, coordenadas do agrupamento mais denso e origem `MONITORAMENTO`). Agente `sentinela` (ferramentas `anomalias_sentinela`, `indicadores_operacionais`) registrado em `AgentesController` e `PainelSistemaService`.
- **Decisões técnicas:** o pacote do INPE é estático, então a janela recente (30 dias) termina no `DATA_HORA` mais recente de cada tipo; "desde quando na fila" = último evento do histórico que levou ao status (senão a criação); carga das equipes reaproveita `ConsultasDistribuicao` (#130); números do texto da IA conferidos com os dados calculados (senão template, `modo` DEMONSTRATIVO); cache em memória (`SENTINELA_CACHE_MINUTOS`=10) com geração única — o modelo roda no máximo uma vez por janela, por isso o `GET` não entrou na categoria IA do limite (que só conta POST); sem migration; nada é gravado além da auditoria.
- **Arquivos principais:** `intelligence/sentinela/*.java`, `RegrasSentinelaTest`, `SentinelaTerritorialServiceTest`, `SentinelaAgendadorTest`, `SentinelaControllerWebMvcTest`, `ConsultasSentinelaOracleTest` (tag `oracle`), `docs/API.md`, `docs/AGENTES_IA.md`.
- **Testes:** `mvn -B -ntp verify` local (sem Oracle) → 571 testes, 0 falhas; novos: regras (7), serviço (7: template sem IA, janela do INPE, IA com números conferidos, número inventado → template, falha da IA, cache, rotina), agendador (5: desligado, ligado, falha não propaga, cron padrão, pisos), controller (2: ANALISTA/FISCAL/CONSULTA 403 e 401; ADMIN/GESTOR 200). `-Poracle` não rodado aqui (sem Docker): a confirmar pelo validador.
- **Correções da revisão (sem bloqueios):** cache *stale-while-revalidate* (último briefing servido na hora; atualização com IA numa thread própria, uma por janela; primeira geração síncrona e sem IA); falha de geração → último briefing com aviso e espera de 60 s (sem briefing: 503 com `Retry-After`); números ≥ 10 numa frase que cita o assunto de uma anomalia precisam ser dela (campo novo `assunto`); URLs/HTML/Markdown removidos do texto da IA; decisão de não limitar o `GET` e teto de 5.000 pontos documentados; merge da `main` (#161). Novos testes: concorrência (8 threads → 1 geração), falha repetida com espera, briefing vencido servido, número de outra anomalia, limpeza, 503.
- **Pendências:** medir o resumo com o `qwen3:4b-instruct` real no Docker (validador).

## 2026-11-24 · GH-136 · Action Cards (PE-04-API)
- **Status:** PR solicitado (subagente implementador, pacote de evoluções de 24/11/2026, frente 4). Web#139 depende desta Issue.
- **O que foi feito:** `acoesSugeridas[{tipo, rotulo, parametros}]` em `RespostaAssistente` e `SugestaoTriagem` (JSON e SSE `concluido`, mesmos DTOs); `AcaoSugerida` (enum `TipoAcaoSugerida`) e `AcoesSugeridas` (derivação determinística e revalidação de acesso); `AssistenteService.responder(ator, ...)` e `AgenteTriagemService.sugerir` acrescentam as ações; `AgenteDocia` não foi alterado.
- **Decisões técnicas:** sem migration; ids só de citações/duplicatas (nunca do modelo), revalidados com `DemandaService.detalhar` (escopo FISCAL) e `DocumentoRepository`; CONSULTA não recebe vínculo; construtores antigos mantidos (campo novo vazio). Distribuição/minuta ficaram fora.
- **Testes:** novo `AcoesSugeridasTest` (6: derivação, acesso/escopo, id inexistente/inacessível descartado, CONSULTA, sem gravação); afetados (Stream*, AgenteDocia, Assistente*, AgenteTriagemService, ContratoOpenApi) verdes localmente; `mvn -B -ntp verify` local (sem Oracle) → 550 testes, 0 falhas.
- **Pendências:** nenhuma de código.

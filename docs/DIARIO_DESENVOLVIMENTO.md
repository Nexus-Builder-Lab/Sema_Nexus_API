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
- **PR:** (a preencher pelo orquestrador) · **Status:** PR aberto; merge conforme CI
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
- **PR:** (a preencher pelo orquestrador) Â· **Status:** aguardando CI e merge
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
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `DemandaStateMachine` (todas as transições de ARCHITECTURE §5; ação fora do status de origem gera 409), `TriagemService` (`POST /triagem/iniciar` e `/triagem` com `decisao` CLASSIFICAR ou SOLICITAR_COMPLEMENTACAO), `DistribuicaoService` (`POST /distribuicao`: fiscal deve ser membro da equipe ativa e prazo >= hoje em Cuiabá, senão 400; grava `DISTRIBUICAO`, equipe/fiscal/prazo da demanda), entidade `Distribuicao` e `GET /api/equipes/{id}/demandas`. Toda transição grava `DEMANDA_HISTORICO` (status anterior/novo, texto, usuário, data) e auditoria, com lock pessimista na demanda.
- **Decisões técnicas:** mantido o contrato já publicado na #7 (`decisao`/`CLASSIFICAR|SOLICITAR_COMPLEMENTACAO`) em vez de `acao: CONCLUIR|COMPLEMENTAR` do texto da Issue; a máquina também expõe as ações da #13 (início, fiscalização, conclusão, devolução) para reaproveitamento; sem migration nova (tabelas já em V3).
- **Arquivos principais:** `flux/demanda/{DemandaStateMachine,TriagemService,DistribuicaoService,Distribuicao,DistribuicaoRepository,DemandaController,EquipeDemandasController,DemandaService}.java`, testes `DemandaStateMachineTest`, `TriagemDistribuicaoServiceTest`, `EndpointsContratoWebMvcTest`, `ContratoOpenApiTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador rodou só os afetados).
- **Pendências:** teste Oracle de ponta a ponta da distribuição não incluído (validador pode rodar `-Poracle`).

## 2026-11-11 · GH-13 · Acompanhamento, fiscalização, conclusão e devolução de demandas
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `AcompanhamentoService` (`POST /inicio` DISTRIBUIDA → EM_ANDAMENTO e `POST /atividades` com registro no histórico), `FiscalizacaoService` (`POST /fiscalizacao`: grava/atualiza `FISCALIZACAO` 1:1 e vai para AGUARDANDO_CONCLUSAO) e `ConclusaoService` (`POST /conclusao` → FINALIZADA com `resultadoFinal`, justificativa e `concluidaEm`; `POST /devolucao` → EM_ANDAMENTO). Todos usam a máquina de estados, lock pessimista, histórico e auditoria. Endpoints saíram do contrato `501`.
- **Decisões técnicas:** FISCAL só age se for o fiscal designado (senão 403); GESTOR/ADMIN em qualquer demanda. Conclusão sem registro de fiscalização responde 409 (além da exigência de status). Demanda FINALIZADA não aceita nenhuma transição (409). Reenvio da fiscalização após devolução atualiza o mesmo registro (1:1). Mantido o contrato do DTO da #7 (`descricao`/`data` em atividades) em vez de `{texto}` do texto da Issue. Textos do histórico limitados a 2000 caracteres. Sem migration nova.
- **Arquivos principais:** `flux/demanda/{AcompanhamentoService,FiscalizacaoService,ConclusaoService,ExecucaoSuporte,DemandaController,Fiscalizacao,FiscalizacaoRepository}.java`, testes `ExecucaoServicosTest`, `EndpointsContratoWebMvcTest`, `ContratoOpenApiTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador rodou só os afetados).
- **Pendências:** sem teste Oracle ponta a ponta do fluxo completo (validador pode rodar `-Poracle`).

## 2026-11-11 · GH-14 · Auditoria centralizada e consulta GET /api/auditoria
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `AuditService` ganhou `consultar` (paginado, filtros de período, usuário, ação e entidade, por Specification), `recentes(n)` (limite 1..100, para o dashboard da #17) e mascaramento de senha/token/Bearer/JWT no `detalhe`. Novo `AuditoriaController` (`GET /api/auditoria`, somente ADMIN) com DTOs `AuditoriaDto` (inclui `usuarioNome`) e `AuditoriaFiltro`. Cadastro, edição, exclusão, status e distribuição de demanda já gravavam auditoria (#11 a #13); upload fica para a #15.
- **Decisões técnicas:** sem migration (tabela e índices por data e entidade já existem na V1); `sort` restrito a `id,data,usuarioId,acao,entidade` (fora disso usa data desc); `de`/`ate` inclusivos por dia.
- **Arquivos principais:** `core/auditoria/{AuditService,AuditoriaController,AuditoriaRepository}.java`, `core/auditoria/dto/*`, testes `AuditServiceTest`, `AuditoriaControllerWebMvcTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador rodou AuditServiceTest, AuditoriaControllerWebMvcTest, EndpointsContratoWebMvcTest, AuthServiceTest: 0 falhas).
- **Pendências:** sem teste Oracle do filtro; validador pode rodar `-Poracle`.
## 2026-11-11 · GH-17 · Indicadores agregados, pontos do mapa e dashboard inicial
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** implementados `GET /api/intelligence/{resumo,serie-mensal,por-municipio,por-bioma,por-categoria,por-situacao,mapa}` e `GET /api/dashboard` (removido `@ContratoPendente`). Novos `IndicadorRepository` (Criteria API, filtros `de/ate/municipioId/bioma/categoria/status`), `IndicadorService` e `DashboardService`. `ResumoIndicadores` ganhou `fiscalizacoes`, `percentualConclusao`, `tempoMedioAtendimentoDias`; `DashboardResponse` ganhou `porBioma` e `porCategoria` (campos aditivos).
- **Decisões técnicas:** agregação em memória sobre as demandas filtradas (volume demonstrativo; uma consulta); documentos = 0 até a #15; atividades recentes vêm de `DemandaHistorico` (`findRecentes`, contrato já publicado da #7), não da tabela AUDITORIA; hoje calculado no fuso America/Cuiaba. Sem migration.
- **Arquivos principais:** `intelligence/indicador/{IndicadorRepository,IndicadorService,IndicadorController}.java`, `intelligence/mapa/MapaController.java`, `core/dashboard/{DashboardService,DashboardController}.java`, `flux/demanda/DemandaHistoricoRepository.java`, testes `IndicadorServiceTest`, `DashboardServiceTest`, `IndicadorOracleTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: IndicadorServiceTest, DashboardServiceTest, EndpointsContratoWebMvcTest, ContratoOpenApiTest e IndicadorOracleTest com Testcontainers: 0 falhas).
- **Pendências:** `totalDocumentos` = 0 (TODO até a #15).
- **Observações:** agregados, mapa e dashboard são visão institucional aberta a todos os perfis autenticados (sem escopo de FISCAL); `atividadesRecentes` vem de `DemandaHistorico` (desvio da AUDITORIA citada na Issue, justificado pelo DTO publicado); `de`/`ate` aplicam-se à data de recebimento; campos novos nos DTOs (`ResumoIndicadores`, `DashboardResponse`): avisar o Web; mapa medido em 10–19 ms com 7 demandas do seed; `IndicadorOracleTest` cobre cadastro e finalização alterando `resumo` e `por-situacao`.

## 2026-11-11 · GH-15 · DocIA: schema, armazenamento em volume, upload, download e extração de texto
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `V4__docia.sql` (`DOCUMENTO`, `DOCUMENTO_DEMANDA`); `StorageService` + `LocalFileStorageService` (`${STORAGE_DIR}/AAAA/MM/<uuid>`, chave confinada à raiz); `TipoArquivo` (extensão + bytes iniciais), `TextExtractor` (PDFBox/UTF-8; DOCX sem extração), `DocumentoService` (SHA-256, auditoria `UPLOAD`, vínculo opcional por `demandaId`); `POST /api/documentos` e `GET /api/documentos/{id}/arquivo` saíram do contrato `501`. Erros 415/413 no `GlobalExceptionHandler`; limite multipart 10 MB no `application.yml`; `STORAGE_DIR` no `.env.example`.
- **Decisões técnicas:** nome original saneado (sem caminho nem controles) só no banco; palavras-chave em coluna única separada por vírgula; texto sem extração (PDF ilegível/criptografado, DOCX) fica nulo; download sempre `octet-stream` + `attachment` + `nosniff`; arquivo órfão é removido se a persistência falhar. SHA256 em `VARCHAR2(64)` (o Hibernate valida CHAR como outro tipo). Dependência nova: `pdfbox 3.0.4`.
- **Arquivos principais:** `docia/storage/*`, `docia/documento/{Documento,DocumentoRepository,DocumentoService,DocumentoController,TipoArquivo,TextExtractor}.java`, `V4__docia.sql`, testes `DocumentoServiceTest`, `LocalFileStorageServiceTest`, `DocumentoOracleTest`, `EndpointsContratoWebMvcTest`, `ContratoOpenApiTest`.
- **Testes:** a confirmar pelo validador (implementador: DocumentoServiceTest, LocalFileStorageServiceTest, EndpointsContratoWebMvcTest, ContratoOpenApiTest e DocumentoOracleTest `-Poracle`: 0 falhas).
- **Pendências:** persistência após `docker compose down`/`up` não verificada (volume `uploads` já está no compose); `totalDocumentos` do dashboard (#17) ainda 0, a ligar na #16.


## 2026-11-11 · GH-16 · DocIA: biblioteca documental, pesquisa textual, detalhes e vínculo com demandas
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** implementados `GET /api/documentos` (paginado; `q`, categoria, tipo, município, demanda, `de`/`ate`, ordenação), `GET /api/documentos/{id}` (metadados, trecho do texto, demandas vinculadas, histórico) e `GET|POST|DELETE /api/demandas/{id}/documentos` (removido `@ContratoPendente`; `PENDENTES` do `ContratoOpenApiTest` ficou vazio). Vínculo/desvínculo gravam evento `DOCUMENTO` no histórico da demanda (`DemandaService.registrarEventoDocumento`) e `VINCULAR`/`DESVINCULAR` na auditoria; duplicado → 409.
- **Decisões técnicas:** pesquisa ignora caixa e acento via `TRANSLATE(coluna, ...) LIKE ? ESCAPE ''` em SQL parametrizado (a Criteria API do Hibernate recusa funções de texto sobre CLOB), com o termo normalizado no Java (`Normalizer`); ids encontrados alimentam a Specification dos demais filtros; `BINARY_AI` não foi usado para não alterar NLS da sessão; mantido o contrato publicado na #7 (`POST /api/demandas/{id}/documentos` com corpo `{documentoId}`, não `/{docId}`); `DocumentoDetalhe` ganhou `historico` (aditivo) e `textoExtraido` passou a ser trecho de 2000 caracteres; `AuditService.historicoDaEntidade` (até 50).
- **Arquivos principais:** `docia/documento/{DocumentoService,DocumentoController,DemandaDocumentoController,DocumentoSpecification,DocumentoRepository}.java`, `dto/DocumentoDetalhe.java`, `flux/demanda/DemandaService.java`, `core/auditoria/{AuditService,AuditoriaRepository}.java`, testes `DocumentoServiceTest`, `DocumentoOracleTest`, `EndpointsContratoWebMvcTest`, `ContratoOpenApiTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: DocumentoServiceTest, EndpointsContratoWebMvcTest, ContratoOpenApiTest, DemandaServiceTest, AuditServiceTest = 75 testes, 0 falhas; DocumentoOracleTest `-Poracle` = 3, 0 falhas).
- **Pendências:** `totalDocumentos` do dashboard (#17) continua 0 (fora do escopo da #16); busca é textual, semântica é P2.


## 2026-11-11 · GH-18 · Seed de demandas e documentos fictícios coerentes para a demonstração
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** massa demonstrativa idempotente (`DemoMassaRunner` → `DemoMassaService`, após o `DemoSeedRunner`): 120 demandas em 26 municípios de MT (3 biomas, 7 status, algumas atrasadas), histórico, distribuições e 46 fiscalizações coerentes com o status, 12 documentos fictícios (PDF gerados com PDFBox e TXT) no volume, incluindo as duas versões do "Parecer Técnico nº 014/2026", vinculados às demandas do cenário fixo; cenário documentado em `docs/DEMO.md`.
- **Decisões técnicas:** JDBC direto (preserva datas retroativas e evita a regra de máquina de estados); `DemoMassaGerador` puro e determinístico (semente fixa, datas relativas ao dia); idempotência pela marca `[DEMO-SEED]` em `OBSERVACOES` (demandas) e por (título, arquivo) (documentos); `NUMERACAO_DEMANDA` atualizada; execução fora do `DemoSeedRunner` para não acoplar o teste de profile (ordem via `@Order`). Textos autorais adaptados (o `seed-docs.js` do protótipo não estava acessível). Sem migration.
- **Arquivos principais:** `demo/{DemoMassaGerador,DemoMassaDocumentos,DemoMassaService,DemoMassaRunner}.java`, testes `DemoMassaGeradorTest`, `DemoMassaDocumentosTest`, `DemoSeedOracleTest`, `docs/DEMO.md`, `docs/DATABASE.md`.
- **Testes:** a confirmar pelo validador (implementador: DemoMassa*Test e DemoSeed*Test sem Oracle: 0 falhas; DemoSeedOracleTest `-Poracle`: 4 testes, 0 falhas).
- **Pendências:** Docker/`demo-reset` com volume real não exercitado; "números do dashboard conferem" cobertos por contagens SQL, não por chamada ao `/api/dashboard`.

## 2026-11-11 · GH-19 · Cobertura de testes dos serviços críticos e revisão de segurança da API
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `RevisaoSegurancaApiTest` (checklist automatizado: `@PreAuthorize` em todos os endpoints, cabeçalhos de segurança, actuator só health, erros sem stacktrace, CORS fora do dev, logs sem segredos); `docs/REVISAO_SEGURANCA.md` com o checklist e evidências. Achado corrigido: leituras de demandas/documentos/catálogos/indicadores sem `@PreAuthorize` explícito; agora `@PreAuthorize(Autorizacao.AUTENTICADO)` na classe (sem mudança de comportamento).
- **Decisões técnicas:** os testes de máquina de estados, numeração concorrente (Oracle), território, upload, trava e perfis já existiam; não foram duplicados.
- **Arquivos principais:** `security/Autorizacao.java`, controllers de flux/docia/core/intelligence, `security/RevisaoSegurancaApiTest.java`, `docs/REVISAO_SEGURANCA.md`.
- **Testes:** a confirmar pelo validador (implementador: RevisaoSegurancaApiTest, EndpointsContratoWebMvcTest, *WebMvcTest, ContratoOpenApiTest: 0 falhas).
- **Pendências:** varredura de CVE em dependências não executada (sem scanner no projeto).

## 2026-11-11 · GH-27 · Assistente DocIA com provider desacoplado, modo demonstrativo e citações
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `POST /api/docia/perguntas` (`docia/assistente`): `AssistenteService` recupera trechos por busca textual em `DOCUMENTO`, `AssistenteProvider` desacoplado, `DemoAssistenteProvider` (extrativo, padrão) e `ExternalAssistenteProvider` (HTTP, só por `DOCIA_PROVIDER`/`DOCIA_API_URL`/`DOCIA_API_KEY`; cai no demonstrativo se faltar configuração ou falhar). Resposta sempre com `modo` e `referencias`; sem base → "não há informação suficiente"; legislação/multas/procedimentos oficiais → recusa.
- **Decisões técnicas:** reuso do SQL de busca da #16 (`DocumentoSpecification` tornou-se pública); recusa por palavras-chave sobre texto normalizado (heurística simples); contrato do provider externo definido aqui (`{pergunta, contexto[]}` → `{resposta}`); sem migration.
- **Arquivos principais:** `docia/assistente/*.java`, `docia/documento/DocumentoSpecification.java`, teste `AssistenteServiceTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: AssistenteServiceTest sem Oracle).
- **Pendências:** sem teste Oracle ponta a ponta contra o seed; provider externo sem teste de integração HTTP.

## 2026-11-11 · GH-34 · Camada de referência com dados públicos de satélite do INPE (focos de calor e DETER) em MT
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** migration `V5__referencia_satelite.sql` (`IMPORTACAO_REFERENCIA`, `REFERENCIA_SATELITE`), snapshot versionado (focos de calor e alertas DETER, ~230 KB) em `resources/referencia/`, `ReferenciaSateliteLoader` idempotente por SHA-256 (profiles demo/dev), endpoints somente leitura `/api/intelligence/referencia/{metadados,pontos,por-municipio}`, scripts `scripts/referencia/baixar-inpe.{ps1,sh}`.
- **Decisões técnicas:** exceção de escopo aprovada (só dados públicos do INPE, camada separada, `oficialSema=false`); detalhes de fonte/licença em `docs/DATABASE.md` e `docs/ARCHITECTURE.md`.
- **Arquivos principais:** `intelligence/referencia/*.java`, `V5__referencia_satelite.sql`, testes `ReferenciaSateliteLoaderTest`, `ReferenciaSateliteServiceTest`, `ContratoOpenApiTest`, docs API/DATABASE/ARCHITECTURE/REQUIREMENTS/DEMO.
- **Testes:** a confirmar pelo validador (implementador: ReferenciaSatelite*Test e ContratoOpenApiTest sem Oracle: 0 falhas).
- **Pendências:** carga em Oracle real/Docker e tempo de resposta < 1 s não exercitados.

## 2026-11-11 · GH-26 · Comparação documental de metadados e conteúdo textual
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `POST /api/documentos/comparacao` (`docia/comparacao`): `ComparacaoService` + `TextoDiff` (LCS por linhas e palavras, Jaccard de termos, trechos semelhantes, termos exclusivos, metadados iguais/diferentes). Resposta declara `geradoPorIa=false`. Documento sem texto extraído → 400 com mensagem clara; inexistente → 404.
- **Decisões técnicas:** sem migration (lê `DOCUMENTO.TEXTO_EXTRAIDO`); limite de 2000 palavras/1500 linhas por documento (flag `truncado`) para conter a matriz LCS; comparação ignora caixa/acento/pontuação; números curtos contam como termos.
- **Arquivos principais:** `docia/comparacao/*.java`, `ComparacaoServiceTest`, `ContratoOpenApiTest` (novo endpoint), `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: ComparacaoServiceTest 7 testes e ContratoOpenApiTest 14, 0 falhas).
- **Pendências:** sem teste Oracle ponta a ponta contra as versões do parecer no seed (o teste unitário usa texto equivalente).

## 2026-11-11 · GH-25 · Notificações de novas demandas, prazos, atrasos, distribuição e documentos
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** migration `V6__notificacoes.sql` (`NOTIFICACAO`, UK `(USUARIO_ID, CHAVE)`), `core/notificacao` (`NotificacaoService`, controller `GET /api/notificacoes`, `GET /api/notificacoes/nao-lidas/contagem`, `PATCH /api/notificacoes/{id}/lida`), `PrazoNotificacaoService` com `@Scheduled` diário (07:00 America/Cuiaba) e ganchos em `DemandaService.cadastrar`, `DistribuicaoService.distribuir` e `DocumentoService.vincular`.
- **Decisões técnicas:** a Issue cita V5, mas V5 já era da #34, então a migration é V6; anti-duplicidade por chave única (inclui a data para prazo/atraso); "gestores" = GESTOR + ADMIN ativos; atraso notifica fiscal e gestores; autor da ação não é notificado de si mesmo.
- **Arquivos principais:** `core/notificacao/*`, `flux/demanda/PrazoNotificacaoService.java`, `common/AgendamentoConfig.java`, testes `NotificacaoServiceTest`, `PrazoNotificacaoServiceTest`, ajustes em `DemandaServiceTest`/`TriagemDistribuicaoServiceTest`/`DocumentoServiceTest`, `docs/API.md`, `docs/DATABASE.md`.
- **Testes:** a confirmar pelo validador (implementador: testes unitários dos 5 arquivos citados, 0 falhas).
- **Pendências:** JPQL das consultas de prazo/atraso e a migration V6 não foram exercitadas em Oracle (validar com perfil `oracle`/Docker).

## 2026-11-12 · GH-45 · Contrato OpenAPI com resposta 2xx em login e demandas
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `200` declarado com schema em `POST /api/auth/login` (`LoginResponse`), `GET /api/demandas/{id}` (`DemandaDetalhe`), `/historico` (lista de `HistoricoItem`) e `/documentos` (lista de `DocumentoResumo`). Causa: `@ApiResponse` só com códigos de erro suprimia o 200 implícito.
- **Decisões técnicas:** `@ApiResponse(200)` explícito nos controllers; sem mudança de comportamento em runtime.
- **Arquivos principais:** `AuthController`, `DemandaController`, `DemandaDocumentoController`, `ContratoOpenApiTest` (novo teste: toda operação tem 2xx).
- **Testes:** a confirmar pelo validador (implementador: ContratoOpenApiTest, EndpointsContratoWebMvcTest, AuthControllerWebMvcTest, 0 falhas).
- **Pendências:** nenhuma.

## 2026-11-12 · GH-24 · Gestão de usuários, setores e equipes
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `UsuarioController/Service` (`GET/POST/PUT /api/usuarios`, `GET /{id}`, `PATCH /{id}/ativacao`, pesquisa `q` + filtros perfil/ativo, paginado), cadastro/edição de setores (`POST/PUT /api/setores`) e equipes (`POST/PUT /api/equipes`, `PUT /{id}/membros`, responsável). Senha de 8 a 72 caracteres (400), BCrypt, e-mail único (409), proteção do último ADMIN ativo (409 ao desativar ou rebaixar), escrita só ADMIN e consulta ADMIN/GESTOR, auditoria de todas as operações sem senha.
- **Decisões técnicas:** sem migration (tabelas existentes); responsável sempre é membro; PUT de equipe sem `responsavelId` limpa o responsável; membros e responsável precisam estar ativos; `Specification` para a pesquisa (sem problemas de parâmetro nulo no Oracle).
- **Arquivos principais:** `core/usuario/*`, `core/setor/*`, `core/equipe/*` (services, controllers, DTOs de requisição), testes `UsuarioServiceTest`, `UsuarioControllerWebMvcTest`, `SetorCadastroServiceTest`, `EquipeCadastroServiceTest`, `ContratoOpenApiTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: testes afetados sem Oracle, ver relatório).
- **Pendências:** sem teste Oracle da pesquisa `q`/Specification; token de usuário desativado depende do filtro JWT existente.

## 2026-11-12 · GH-28 · Relatórios de demandas com filtros e exportação CSV
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
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
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `compose-smoke.yml` (`workflow_dispatch` + cron noturno): gate do secret `CROSS_REPO_TOKEN` (ausente → aviso + resumo explicativo, sem falha silenciosa), checkout API+Web lado a lado, `.env` com segredos aleatórios, `compose up --build`, espera `healthy` (15 min), login e `GET /api/demandas` em `localhost:3000`, logs como artefato em falha, limpeza final.
- **Decisões técnicas:** segredos do `.env` gerados por `openssl rand` (nada fixo); usuário demo (profile demo) em env do job; limpeza com `down --remove-orphans` (runner efêmero, sem `-v`).
- **Arquivos principais:** `.github/workflows/compose-smoke.yml`, `docs/DOCKER.md`.
- **Testes:** a confirmar pelo validador (implementador: sintaxe YAML validada com PyYAML; execução real do workflow depende do secret e de push).
- **Pendências:** execução verde com o secret só após merge/dispatch no GitHub.

## 2026-11-12 · GH-47 · prazoSugerido no DemandaDetalhe
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** verificado que `DemandaDetalhe.prazoSugerido` (nullable) e seu mapeamento em `DemandaService` já existiam desde o GH-11; adicionados 2 testes unitários de regressão (cadastro e obtenção, com valor e com null).
- **Decisões técnicas:** nenhuma mudança de código de produção; contrato OpenAPI já inclui o campo.
- **Arquivos principais:** `DemandaServiceTest`.
- **Testes:** a confirmar pelo validador (implementador: `mvn -Dtest=DemandaServiceTest test`, 0 falhas).
- **Pendências:** nenhuma.

## 2026-11-12 · GH-83 · Login demonstrativo por perfil (Modo demonstração)
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `POST /api/auth/demo/{perfil}` (`AuthDemoController`, `@Profile("demo")`) devolve `LoginResponse` do primeiro usuário ativo com `DEMO=1` do perfil; `GET /api/auth/config` inclui `perfisDemo` quando `demo=true`. Auditoria `LOGIN` com detalhe "modo demonstração"; perfil inválido 400, sem usuário 404.
- **Decisões técnicas:** rota liberada no `SecurityConfig` (`permitAll`), mas o bean só existe no profile `demo` (404 nos demais); nenhuma senha envolvida; lógica em `AuthService.loginDemo`.
- **Arquivos principais:** `AuthDemoController`, `AuthService`, `PerfilDemo`, `AuthConfigResponse`, `UsuarioRepository`, `SecurityConfig`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: AuthServiceTest, AuthControllerWebMvcTest, AuthDemoControllerWebMvcTest, 0 falhas).
- **Pendências:** nenhuma.

## 2026-11-12 · GH-84 · Endpoints de controle de prazos e de produtividade
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `GET /api/flux/prazos/resumo`, `GET /api/flux/prazos` (por estado, paginado) e `GET /api/flux/produtividade` (KPIs, por setor, tempo médio por setor, histórico mensal, por responsável), todos `demonstrativo: true` e com o escopo de perfil da listagem de demandas (reuso de `DemandaSpecification`).
- **Decisões técnicas:** agregações em memória sobre o recorte já filtrado/escopado (volume demonstrativo), cálculo puro testável sem banco; setor da demanda = setor da equipe; sem migration.
- **Arquivos principais:** `PrazoController/Service`, `ProdutividadeController/Service`, `PrazoDtos`, `ProdutividadeResposta`, `PrazoProdutividadeServiceTest`, `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: `PrazoProdutividadeServiceTest`, 10 testes, 0 falhas).
- **Pendências:** conferência contra o seed real (teste Oracle) fica para o validador.

## 2026-11-12 · GH-89 · Data de conclusão e de atualização na listagem de demandas
- **PR:** (a preencher pelo orquestrador) · **Status:** aguardando validação
- **Agentes que atuaram:** `implementador-backend`
- **O que foi feito:** `DemandaResumo` ganhou `concluidaEm` (nullable) e `atualizadoEm`, mapeados em `DemandaService.resumo` e refletidos no OpenAPI; `spring.data.web.pageable.max-page-size` passou de 100 para 500 para o quadro carregar todas as demandas.
- **Decisões técnicas:** limite de 500 é global da paginação (config única); campos aditivos, sem migration.
- **Arquivos principais:** `DemandaResumo`, `DemandaService`, `application.yml`, `DemandaServiceTest`, `ContratoOpenApiTest` (comentário), `docs/API.md`.
- **Testes:** a confirmar pelo validador (implementador: `DemandaServiceTest`, ContratoOpenApiTest, EndpointsContratoWebMvcTest).
- **Pendências:** nenhuma.

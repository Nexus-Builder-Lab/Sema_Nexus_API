# SEMA NEXUS — Arquitetura

> **Status:** Planejado (10/11/2026). Este documento descreve a arquitetura-alvo do MVP. Itens marcados **Implementado** só depois de mesclados e testados.
> AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Sem integração com sistemas institucionais.

## 1. Visão geral

```
 Navegador ──HTTP──▶ web (Nginx :3000 no host)
                      ├── /          → SPA React (build estático)
                      └── /api/*     → proxy reverso → api:8080
                                            │
                              api (Spring Boot :8080)
                               ├── REST + JWT (Spring Security)
                               ├── JPA/Hibernate ────▶ oracle (Oracle 23ai Free :1521 / FREEPDB1)
                               ├── Flyway (schema versionado)
                               └── StorageService ───▶ volume nomeado "uploads" (/data/uploads)
```

| Repositório | Papel |
|---|---|
| `Nexus-Builder-Lab/Sema_Nexus_API` | API Spring Boot + infraestrutura e documentação transversais (compose, scripts, docs, project-health). Não existe repositório hub. |
| `Nexus-Builder-Lab/Sema_Nexus_Web` | SPA React. |
| `FabricioJorge0/prototipo_SEMA_NEXUS` | Protótipo HTML/CSS/JS de referência (somente leitura). |

- O Web chama sempre `/api` relativo: em desenvolvimento o Vite faz proxy para `localhost:8080`; no Docker o Nginx faz proxy para `api:8080` (sem CORS). CORS só no profile `dev`, para `http://localhost:5173`.
- O frontend **nunca** acessa o Oracle.
- Contrato: OpenAPI 3 (springdoc) exportado pelo CI da API como artefato `openapi.json`; o Web gera mocks MSW a partir dele.

## 2. Stack

| Camada | Escolha | Por quê |
|---|---|---|
| Web | Vite + React 18 (JavaScript), Bootstrap 5.3, React Router 6, Axios, Chart.js 4 (react-chartjs-2), Leaflet 1.9 (react-leaflet) | Create React App está descontinuado; Chart.js e Leaflet são os mesmos do protótipo |
| Testes Web | Vitest, Testing Library, MSW 2 | Integração nativa com Vite |
| API | Spring Boot 3.5, Java 21, Maven | LTS; já instalados no ambiente |
| Persistência | Spring Data JPA / Hibernate 6, ojdbc11 | Requisito |
| Migrations | **Flyway** (+ `flyway-database-oracle`) | DDL Oracle em SQL puro é mais fácil de revisar (sequences, CHECK, índices) que changelogs XML/YAML; menor curva para 2 devs em 11 dias. O ponto forte do Liquibase (rollback) não é requisito |
| Segurança | Spring Security, JWT HS256 stateless, BCrypt | SPA + API sem sessão |
| Extração de texto | Apache PDFBox 3 | PDFs com texto; OCR é P2 |
| Testes API | JUnit 5, Mockito, Spring Boot Test, Testcontainers (Oracle Free) | Unitários sem Oracle; integração com Oracle real em job separado |
| Banco | Oracle Database 23ai Free (`gvenzl/oracle-free:23-slim-faststart`) | Requisito; imagem leve com inicialização rápida |
| Execução | Docker + Docker Compose v2 | Demonstração reprodutível e offline |

## 3. Organização de pastas

### Sema_Nexus_API
```
├── CLAUDE.md  README.md  .env.example  .gitignore  .editorconfig
├── Dockerfile  .dockerignore  docker-compose.yml        (Issues #2 e #8)
├── scripts/  demo-up|demo-down|demo-reset .sh/.ps1      (Issue #9)
├── docs/
├── .github/  workflows/ ISSUE_TEMPLATE/ scripts/ PULL_REQUEST_TEMPLATE.md
├── .claude/  settings.json  commands/start-issue.md
├── pom.xml
└── src/main/java/br/com/nexusbuilderlab/semanexus/
    ├── config/        OpenApiConfig, CorsConfig, StorageProperties
    ├── security/      SecurityConfig, JwtService, JwtAuthFilter, AuthController, TestUserProfileGuard
    ├── common/        exception/GlobalExceptionHandler, dto/PageResponse, audit/AuditService
    ├── core/          usuario/ setor/ equipe/ auditoria/ catalogo/ dashboard/ notificacao/
    ├── flux/          demanda/ triagem/ distribuicao/ fiscalizacao/
    ├── intelligence/  indicador/ mapa/ relatorio/
    ├── docia/         documento/ storage/ extracao/ comparacao/ assistente/
    └── demo/          DemoSeedRunner (@Profile demo,dev) + recursos demo/*.json
    src/main/resources/ application.yml, application-{dev,demo,prod,openapi}.yml, db/migration/V*.sql
```
O pacote usa `br.com.nexusbuilderlab` (e não `br.gov.mt`) para não aparentar sistema oficial.

### Sema_Nexus_Web
```
├── Dockerfile  nginx.conf  .dockerignore                  (Issue Web#2)
├── public/geo/ mt-uf.geojson, mt-municipios.geojson       (malha IBGE, do protótipo)
└── src/ api/ mocks/ auth/ layout/ components/ pages/{core,intelligence,flux,docia}/ styles/ utils/
```

## 4. Modelo de dados

Ver [DATABASE.md](DATABASE.md).

## 5. Fluxo de status da demanda

| De → Para | Ação | Quem pode | Exige |
|---|---|---|---|
| — → **Pendente** | cadastro | Admin, Gestor, Analista | campos obrigatórios |
| Pendente → **Em triagem** | iniciar triagem | Admin, Gestor, Analista | — |
| Em triagem → **Aguardando distribuição** | concluir triagem | Admin, Gestor, Analista | categoria, prioridade, justificativa |
| Em triagem → **Pendente** | solicitar complementação | Admin, Gestor, Analista | justificativa |
| Aguardando distribuição → **Distribuída** | distribuir | Admin, Gestor | equipe, fiscal da equipe, prazo ≥ hoje |
| Distribuída → **Em andamento** | iniciar execução | fiscal designado, Gestor, Admin | — |
| Em andamento → **Aguardando conclusão** | registrar fiscalização | fiscal designado, Gestor, Admin | registro completo |
| Aguardando conclusão → **Finalizada** | concluir | Gestor, Admin | resultado final, justificativa |
| Aguardando conclusão → **Em andamento** | devolver | Gestor, Admin | justificativa |

Transição inválida → `409 Conflict`. Toda transição grava `DEMANDA_HISTORICO` e `AUDITORIA`. **Atrasada** é derivada (`prazo < hoje` e status ≠ Finalizada). O sistema não gera autos de infração nem substitui sistemas oficiais.

## 6. Perfis e autorização

| Recurso | Admin | Gestor | Fiscal | Analista | Consulta |
|---|:-:|:-:|:-:|:-:|:-:|
| Dashboards, mapa, indicadores | ✔ | ✔ | ✔ | ✔ | ✔ |
| Relatórios (P1) | ✔ | ✔ | — | ✔ | ✔ |
| Demandas: consultar | ✔ | ✔ | da equipe / atribuídas | ✔ | ✔ |
| Demandas: cadastrar e editar | ✔ | ✔ | — | ✔ | — |
| Triagem | ✔ | ✔ | — | ✔ | — |
| Distribuição e conclusão | ✔ | ✔ | — | — | — |
| Acompanhamento e fiscalização | ✔ | ✔ | ✔ (designado) | — | — |
| Documentos: consultar | ✔ | ✔ | ✔ | ✔ | ✔ |
| Documentos: upload e vínculo | ✔ | ✔ | ✔ | ✔ | — |
| Usuários, setores, equipes, auditoria | ✔ | consulta | — | — | — |

A autorização é aplicada no backend; o Web apenas oculta o que o perfil não pode usar.

## 7. Autenticação e usuário de teste

- `POST /api/auth/login` → JWT HS256 (`JWT_SECRET` por ambiente, ≥ 32 bytes, obrigatório fora de `dev`), validade 8 h. Logout registrado na auditoria; o token é descartado no cliente (lista de revogação é P2).
- O Web guarda o token em memória + `sessionStorage`. Nenhum dado de negócio em LocalStorage.
- `DemoSeedRunner` (`@Profile({"demo","dev"})`) cria `admin@admin.com` (ADMIN) e os demais usuários fictícios com **hash BCrypt pré-calculado** — o texto puro das senhas não existe em `src/main`.
- `TestUserProfileGuard`: fora de `demo`/`dev`, se `admin@admin.com` existir, a aplicação **não sobe** e registra o motivo.
- `GET /api/auth/config` (público) informa `{demo: true}` para o Web exibir o aviso na tela de login.

## 8. Armazenamento de arquivos

`StorageService` (`store`, `load`, `delete`) com implementação local em `${STORAGE_DIR:/data/uploads}/AAAA/MM/<uuid>`; o nome original fica só no banco (evita path traversal). Extensões `pdf, txt, md, csv, docx`; máximo 10 MB; tipo verificado pelos bytes iniciais; SHA-256 registrado. No Docker, volume nomeado `uploads`. Trocar por S3/MinIO = nova implementação da interface.

## 9. Mapa sem internet

A camada base é a **malha GeoJSON do IBGE** (estado + 142 municípios, ~200 KB, extraída do protótipo) servida pelo próprio Web. Tiles OpenStreetMap são uma camada opcional, desligada por padrão. A demonstração funciona offline.

## 10. Testes e CI

| Repo | Unitário (todo PR) | Integração | Sistema |
|---|---|---|---|
| API | JUnit 5 + Mockito, sem Oracle | Testcontainers Oracle Free, tag `oracle`, `oracle-it.yml` manual/noturno (P1, Issue #23) | `compose-smoke.yml` manual/noturno (Issue #20) |
| Web | Vitest + Testing Library + MSW | — | — |

CI de cada repositório: validação do nome do branch (`^feature/GH-[0-9]+$`), build, testes, build da imagem Docker sem publicar; a API também exporta o OpenAPI. Detalhes em [DEVELOPMENT.md](DEVELOPMENT.md).

## 11. Decisões registradas

| Data | Decisão |
|---|---|
| 10/11/2026 | Sem repositório hub: infraestrutura e docs transversais ficam na API. |
| 10/11/2026 | Flyway (não Liquibase). |
| 10/11/2026 | Vite (não Create React App). JavaScript (sem TypeScript). |
| 10/11/2026 | Mapa com malha IBGE local; OSM opcional. |
| 10/11/2026 | Status, perfis e escopo da especificação prevalecem sobre o protótipo. |

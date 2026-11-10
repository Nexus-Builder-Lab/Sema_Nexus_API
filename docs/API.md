# SEMA NEXUS — Contrato da API

> **Status:** Planejado. A fonte da verdade será o OpenAPI gerado pelo springdoc (`/v3/api-docs`, `/swagger-ui.html`) e exportado pelo CI como artefato `openapi.json` (Issue #7). Este documento registra os **padrões**; alterou endpoint, atualize as anotações e este arquivo.

## Padrões

| Tema | Regra |
|---|---|
| Base | Prefixo `/api`, JSON UTF-8, datas ISO-8601 (`2026-11-27`, `2026-11-27T14:30:00-04:00`) |
| Autenticação | `Authorization: Bearer <jwt>`; públicos: `POST /api/auth/login`, `GET /api/auth/config`, `GET /actuator/health` |
| Paginação | `?page=0&size=20&sort=campo,asc` → `{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }` (máx. `size=100`) |
| Filtros | Query params opcionais e combináveis; datas `de`/`ate` inclusivas |
| Erros | `application/problem+json` (RFC 9457): `type`, `title`, `status`, `detail`, `instance` e, em validação, `errors: [{ "campo": "titulo", "mensagem": "..." }]`. Nunca stacktrace |
| Dados simulados | Respostas de indicadores incluem `"demonstrativo": true` |

## Códigos HTTP

| Código | Uso |
|---|---|
| 200 / 201 / 204 | Sucesso (201 com `Location` em criações) |
| 400 | Validação de campos |
| 401 | Sem token ou token inválido |
| 403 | Perfil sem permissão ou fora do escopo (ex.: fiscal em demanda de outra equipe) |
| 404 | Recurso inexistente |
| 409 | Conflito de estado (transição inválida, vínculo duplicado) |
| 413 / 415 | Upload grande demais / tipo não permitido |
| 501 | Endpoint do contrato ainda não implementado (somente até a Issue correspondente) |

## Endpoints (MVP)

| Método | Caminho | Perfil | Issue |
|---|---|---|:-:|
| POST | `/api/auth/login` · `/api/auth/logout` | público / autenticado | #4 |
| GET | `/api/auth/me` · `/api/auth/config` | autenticado / público | #4 |
| GET | `/api/catalogos/{municipios,biomas,categorias,origens,prioridades,status}` | autenticado | #7 |
| GET | `/api/setores` · `/api/equipes` · `/api/equipes/{id}/membros` | autenticado | #7 |
| POST · PUT · GET · DELETE | `/api/demandas` · `/api/demandas/{id}` | ver ARCHITECTURE §6 | #11 |
| GET | `/api/demandas/{id}/historico` | consulta | #11 |
| POST | `/api/demandas/{id}/triagem/iniciar` · `/triagem` | Admin, Gestor, Analista | #12 |
| POST | `/api/demandas/{id}/distribuicao` · GET `/api/equipes/{id}/demandas` | Admin, Gestor | #12 |
| POST | `/api/demandas/{id}/inicio` · `/atividades` · `/fiscalizacao` | fiscal designado, Gestor, Admin | #13 |
| POST | `/api/demandas/{id}/conclusao` · `/devolucao` | Gestor, Admin | #13 |
| GET | `/api/auditoria` | Admin | #14 |
| POST · GET | `/api/documentos` (multipart) · `/api/documentos/{id}` · `/api/documentos/{id}/arquivo` | ver ARCHITECTURE §6 | #15, #16 |
| GET · POST · DELETE | `/api/demandas/{id}/documentos[/{docId}]` | ver ARCHITECTURE §6 | #16 |
| GET | `/api/intelligence/{resumo,serie-mensal,por-municipio,por-bioma,por-categoria,por-situacao,mapa}` | autenticado | #17 |
| GET | `/api/dashboard` | autenticado | #17 |
| GET | `/actuator/health` | público | #1 |

### P1 (somente se houver folga)

| Método | Caminho | Issue |
|---|---|:-:|
| GET · POST · PUT · PATCH | `/api/usuarios[/{id}[/ativacao]]`, `/api/setores`, `/api/equipes[/{id}/membros]` | #24 |
| GET · PATCH | `/api/notificacoes[/{id}/lida]` | #25 |
| POST | `/api/documentos/comparacao` | #26 |
| POST | `/api/docia/perguntas` | #27 |
| GET | `/api/relatorios/demandas[.csv]` | #28 |

## Exemplo: login

```http
POST /api/auth/login
Content-Type: application/json

{ "email": "admin@admin.com", "senha": "<senha demonstrativa — ver docs/DEMO.md>" }
```
```json
{ "token": "<jwt>", "expiresIn": 28800,
  "usuario": { "id": 1, "nome": "Administrador (demonstração)", "email": "admin@admin.com", "perfil": "ADMIN" } }
```
> `admin@admin.com` é uma credencial **exclusivamente demonstrativa**, existente apenas nos profiles `demo`/`dev` (senha em [DEMO.md](DEMO.md)).

## Uso pelo frontend

O CI da API publica `openapi.json` em cada execução (job **Exportar contrato OpenAPI**). O Web baixa o artefato e mantém handlers MSW em `src/mocks/` (Issue Web#4), ativados por `VITE_USE_MOCKS=true`.

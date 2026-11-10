# SEMA NEXUS — Execução com Docker

> **Status:** o `docker-compose.yml` e o `.env.example` estão **Implementados** (Issue API#8), assim como a imagem da API (API#2), a do Web (Web#2) e os scripts `demo-up/down/reset` (API#9). **Planejado:** a seção final de requisitos validados e solução de problemas (API#10).

## Imagem da API

**Status:** Implementado (Issue API#2). Arquivos: `Dockerfile` e `.dockerignore` na raiz deste repositório.

| Item | Valor |
|---|---|
| Stage `build` | `maven:3.9-eclipse-temurin-21`; `dependency:go-offline` antes de copiar `src/` (cache de dependências); `mvn -B -DskipTests package` |
| Stage `runtime` | `eclipse-temurin:21-jre-alpine` |
| Usuário | `semanexus`, UID/GID 10001 (não-root) |
| Diretórios | `/app/app.jar`; `/data/uploads` (dono UID 10001, declarado como `VOLUME`, `STORAGE_DIR=/data/uploads`) |
| Porta | `EXPOSE 8080` (`SERVER_PORT`) |
| JVM | `JAVA_OPTS` (padrão `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`); Java roda como PID 1 (`exec`) |
| Healthcheck | `wget -qO- http://localhost:${SERVER_PORT}/actuator/health` a cada 15 s (início tolerado: 60 s) |
| Tamanho | ~283 MB descompactada (soma das camadas), ~138 MB compactada |
| Segredos | nenhum `ENV`/`ARG` sensível; credenciais só em tempo de execução (`.env` → compose) |

```bash
docker build -t sema-nexus-api .
docker run --rm --entrypoint id sema-nexus-api            # uid=10001(semanexus)
docker run -d --name api-teste -p 18080:8080 sema-nexus-api
docker inspect api-teste --format '{{.State.Health.Status}}'   # healthy
docker rm -f api-teste
```

## Estrutura

Os repositórios ficam lado a lado; o compose está na raiz da API (não há repositório hub):

```
projetos/
├── Sema_Nexus_API/      # docker-compose.yml, .env.example, scripts/, Dockerfile da API
└── Sema_Nexus_Web/      # Dockerfile do Web
```

## Serviços

| Serviço | Imagem / build | Porta no host | Observações |
|---|---|---|---|
| `oracle` | `gvenzl/oracle-free:23.26.3-slim-faststart` (tag fixada; mesma imagem do `23-slim-faststart` usado nos testes) | 1521 (`ORACLE_PORT`) | volume `oradata`; healthcheck `healthcheck.sh`; `ORACLE_PASSWORD`, `APP_USER`, `APP_USER_PASSWORD` do `.env` |
| `api` | build `.` | 8080 (`API_PORT`) | depende de `oracle` saudável; profile `demo`; `jdbc:oracle:thin:@//oracle:1521/FREEPDB1`; volume `uploads`; healthcheck `/actuator/health` |
| `web` | build `../Sema_Nexus_Web` | 3000 (`WEB_PORT`; container escuta 8080) | depende de `api` saudável; Nginx com proxy `/api` → `api:8080` |

Volumes nomeados: `oradata` (`/opt/oracle/oradata`) e `uploads` (`/data/uploads`); o projeto compose se chama `sema-nexus`. Variáveis obrigatórias (o compose falha com mensagem clara se faltarem): `ORACLE_PASSWORD`, `APP_USER`, `APP_USER_PASSWORD`, `JWT_SECRET`. Se uma porta do host já estiver em uso, troque `API_PORT`, `WEB_PORT` ou `ORACLE_PORT` no `.env`.

**Validado na Issue #8** (Docker 29 / Compose v5, Windows + WSL2): a partir de volumes novos, `docker compose up -d --build` deixou `oracle`, `api` e `web` `healthy` (Oracle em ~50 s com a imagem `faststart`); login com o usuário de teste via `http://localhost:3000/api/auth/login` (proxy do Nginx) retornou 200; após `down` + `up` o seed não duplicou (6 usuários, 4 equipes, 3 setores; log "Seed demonstrativo já existente"), o Flyway informou "up to date" e um arquivo gravado em `/data/uploads` continuou lá.

## Requisitos (a validar na Issue #10)

| Item | Recomendação |
|---|---|
| Docker | Docker Desktop (Windows/macOS) ou Docker Engine + Compose v2 (Linux) |
| Memória | Host com 8 GB+ (16 GB recomendado); ~2 GB para o Oracle. No Windows, WSL2 com ≥ 6 GB (`%UserProfile%\.wslconfig` → `[wsl2]` `memory=6GB`) |
| Disco | ~5 GB livres (imagens + volumes) |
| 1ª subida | build das imagens + inicialização do Oracle: alguns minutos; as seguintes, menos de 1 min |
| Apple Silicon / ARM | a imagem `gvenzl/oracle-free` tem variante ARM64 |
| Internet | necessária só no primeiro build (download de imagens e dependências); a demonstração roda offline |

## Como subir

```bash
# 1. Clonar os repositórios lado a lado
git clone https://github.com/Nexus-Builder-Lab/Sema_Nexus_API.git
git clone https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web.git

# 2. Configurar variáveis de ambiente
cd Sema_Nexus_API
cp .env.example .env        # Windows (PowerShell): Copy-Item .env.example .env
#    edite o .env e troque as senhas e o JWT_SECRET

# 3. Subir tudo (build + start em segundo plano)
docker compose up -d --build

# 4. Acompanhar a inicialização (Oracle leva alguns minutos na primeira vez)
docker compose ps
docker compose logs -f api

# 5. Acessar
#    Aplicação:  http://localhost:3000
#    API:        http://localhost:8080
#    Swagger:    http://localhost:8080/swagger-ui.html
#    Login de teste: admin@admin.com  /  12345678   (somente demonstração)
```

Com os scripts (**Implementado**, Issue #9) os passos 2–5 viram um comando:

```bash
./scripts/demo-up.sh         # Linux/macOS/WSL/Git Bash   (ou: make up)
.\scripts\demo-up.ps1        # Windows (PowerShell)
```

## Scripts `demo-*`

**Status:** Implementado (Issue API#9). Pares `.sh` (POSIX) e `.ps1` (PowerShell) em `scripts/`, com funções comuns em `scripts/lib/`. Há também um `Makefile` opcional (`make up | down | reset | logs | ps`).

| Script | O que faz |
|---|---|
| `demo-up` | Valida que o Docker (e o Compose v2) estão ativos; cria o `.env` a partir do `.env.example` **somente se não existir**; `docker compose up -d --build`; aguarda `oracle`, `api` e `web` ficarem `healthy` (falha em `unhealthy` ou no timeout); imprime as URLs (usando `WEB_PORT`/`API_PORT` do `.env`) e o e-mail do login de teste — a senha demonstrativa fica em [DEMO.md](DEMO.md), nunca nos scripts. |
| `demo-down` | `docker compose down` — **preserva** os volumes (`oradata`, `uploads`). Nunca usa `-v`. |
| `demo-reset` | **Destrutivo.** Recusa se `SPRING_PROFILES_ACTIVE` (ambiente ou `.env`; padrão `demo`) contiver algo além de `demo`/`dev`; exige `DEMO_RESET_CONFIRM=yes` (ambiente ou `.env`) **ou** digitar `RESETAR` em terminal interativo (sem terminal e sem a variável → recusa); então `docker compose down -v`, sobe de novo via `demo-up` e aguarda o seed (feito pela API na subida). Qualquer recusa termina com código ≠ 0 sem apagar nada. |

Variáveis opcionais: `DEMO_UP_TIMEOUT` (segundos de espera pelos healthchecks, padrão 600) e `DEMO_POLL_SECONDS` (intervalo, padrão 5).

**Testes:** `sh scripts/tests/demo-scripts.test.sh` (também roda no CI, job `scripts-demo`) usa um `docker` **falso** no PATH e verifica criação do `.env`, preservação do `.env` existente, healthchecks (ok/unhealthy/timeout), Docker inativo, `down` sem `-v` e todas as recusas do `demo-reset` (sem confirmação, profile `prod`, lista `demo,prod`, valor ≠ `yes`) além do caminho confirmado (`down -v` antes do `up`).

> No Windows com Docker Desktop, se `localhost` não responder no host (resolução para IPv6), use `127.0.0.1` nas URLs.

## Parar sem perder dados

```bash
./scripts/demo-down.sh       # ou: docker compose down
```

## Reset completo da demonstração

Apaga os volumes do ambiente demonstrativo (banco Oracle e uploads) e recarrega o seed. **Destrutivo.** Use o script, que pede confirmação e recusa profiles diferentes de `demo`/`dev`:

```bash
./scripts/demo-reset.sh      # Windows: .\scripts\demo-reset.ps1
DEMO_RESET_CONFIRM=yes ./scripts/demo-reset.sh   # sem pergunta (automação)
```

Equivalente manual (somente em ambiente de demonstração):

```bash
docker compose down -v      # destrutivo: remove os volumes do Oracle e dos uploads
docker compose up -d --build
```

> O Claude Code está configurado para **nunca** executar `down -v` sem confirmação do proprietário.

## Segurança

- Nenhum segredo em Dockerfiles, imagens ou `docker-compose.yml`; tudo vem do `.env` (ignorado pelo Git).
- Containers da API e do Web rodam como usuário não-root.
- A credencial `admin@admin.com` / `12345678` existe somente nos profiles `demo`/`dev`.

## Problemas comuns (a completar na Issue #10)

| Sintoma | Causa provável | Solução |
|---|---|---|
| `oracle` não fica `healthy` | memória insuficiente no Docker/WSL2 | aumentar memória (ver Requisitos) e `docker compose logs oracle` |
| `port is already allocated` | porta 1521, 8080 ou 3000 em uso | alterar `API_PORT`/`WEB_PORT` no `.env` ou liberar a porta |
| `api` reinicia em loop | `.env` sem `JWT_SECRET` ou senha do Oracle diferente da usada na criação do volume | conferir o `.env`; se a senha mudou, é preciso resetar o volume |
| Login falha com 401 | profile diferente de `demo` (sem seed) | `SPRING_PROFILES_ACTIVE=demo` no `.env` |

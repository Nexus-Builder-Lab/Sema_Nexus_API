# SEMA NEXUS — Execução com Docker

> **AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.** Nada aqui toca sistemas institucionais.

| Item | Status |
|---|---|
| `docker-compose.yml` (Oracle Free + API + Web), `.env.example`, healthchecks, volumes | **Implementado** (Issue API#8) |
| Imagem da API (Issue API#2) e imagem do Web (Web#2) | **Implementado** |
| Este documento (requisitos, passo a passo, Windows/WSL2, Apple Silicon, problemas comuns) | **Implementado** (Issue API#10) |
| Scripts `demo-up`, `demo-down` e `demo-reset` (`.sh` e `.ps1`) | **Em desenvolvimento** (Issue API#9, PR aberto, ainda não mesclado). Até a mesclagem, use os comandos `docker compose` descritos abaixo — os scripts apenas os encapsulam |
| Smoke test automático do Compose no GitHub Actions | **Planejado** (Issue API#20) |
| Imagens publicadas em registry, deploy em nuvem | **Futuro** (fora do escopo da demonstração) |

## Passo a passo rápido

Pré-requisito: Docker com Compose v2 funcionando (`docker compose version`). Veja os [requisitos](#requisitos) antes da primeira subida.

```bash
# 1. Clonar os dois repositórios LADO A LADO (o compose da API usa ../Sema_Nexus_Web)
git clone https://github.com/Nexus-Builder-Lab/Sema_Nexus_API.git
git clone https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web.git
cd Sema_Nexus_API

# 2. Criar o .env (ignorado pelo Git) e EDITAR as senhas e o JWT_SECRET
cp .env.example .env            # Windows (PowerShell): Copy-Item .env.example .env

# 3. Subir tudo: build das imagens + start em segundo plano
docker compose up -d --build

# 4. Esperar os três serviços ficarem "healthy" (1ª vez: alguns minutos)
docker compose ps
docker compose logs -f api      # Ctrl+C sai dos logs; os containers continuam

# 5. Abrir a aplicação
#    Aplicação: http://localhost:3000   (WEB_PORT)
#    API:       http://localhost:8080   (API_PORT)
#    Swagger:   http://localhost:8080/swagger-ui.html
#    Login de teste (SOMENTE DEMONSTRAÇÃO): admin@admin.com / 12345678
```

Os **valores do `.env.example` são só exemplos** (`troque-esta-senha…`): troque-os. `JWT_SECRET` precisa ter **32 caracteres ou mais**. Se uma porta do host já estiver em uso, altere `API_PORT`, `WEB_PORT` ou `ORACLE_PORT` no `.env` **antes** do primeiro `up` e use essa porta nas URLs acima.

Pronto quando `docker compose ps` mostrar `oracle`, `api` e `web` como `healthy`. O login de teste aparece **somente** como credencial demonstrativa e só existe nos profiles `demo`/`dev` (ver [Segurança](#segurança)).

### Estrutura de pastas

Os repositórios ficam lado a lado; o compose está na raiz da API (não há repositório hub):

```
projetos/
├── Sema_Nexus_API/      # docker-compose.yml, .env.example, Dockerfile da API, scripts/
└── Sema_Nexus_Web/      # Dockerfile do Web (build context do serviço `web`)
```

## Requisitos

Valores **medidos** na validação (Docker 29 / Compose v5, Windows 11 + WSL2, 8 GB de RAM para o Docker) ou marcados como recomendação.

| Item | Valor |
|---|---|
| Docker | Docker Desktop (Windows/macOS) ou Docker Engine + Compose v2 (Linux) |
| Memória | Host com **8 GB ou mais** (recomendação). O Oracle usa cerca de **2 GB**; o build do Maven/Node usa mais durante a construção das imagens. O Docker/WSL2 precisa ter pelo menos **6 GB** disponíveis |
| Disco | **~12 GB livres** (medido: imagem Oracle ~6,5 GB descompactada, API ~0,4 GB, Web ~0,1 GB, volume do banco ~3,2 GB, mais cache de build) |
| 1ª subida | download das imagens (Oracle ~6,5 GB descompactada) + build + inicialização do banco: de **1 a 3 min** com imagens já baixadas e internet rápida, **mais** o tempo do download na primeira vez. Com a imagem `faststart`, o Oracle ficou `healthy` em 35–50 s. Medido do zero (volumes novos, imagens já baixadas): `docker compose up -d --build` terminou em **41 s** com os três serviços `healthy` |
| Subidas seguintes | menos de 1 min (imagens e volumes já existem) |
| Internet | só na primeira vez (imagens, dependências Maven/npm). Depois a demonstração roda **offline** |
| Portas livres no host | 3000 (Web), 8080 (API), 1521 (Oracle) — ou as que você definir no `.env` |

### Windows e WSL2

O Docker Desktop no Windows usa WSL2, que limita a memória por padrão (em geral a **50 % da RAM do host**). Para garantir memória ao Oracle, crie/edite `%UserProfile%\.wslconfig`:

```ini
[wsl2]
memory=6GB
processors=4
```

Depois feche o Docker Desktop e execute `wsl --shutdown`; abra o Docker Desktop de novo. Confira com `docker info --format "mem={{.MemTotal}}"` (esperado ≥ 6 000 000 000).

- Clone os repositórios em um caminho **curto e sem espaços** (por exemplo `C:\SEMANEXUS\`). Caminhos dentro do WSL (`\\wsl$\...`) são mais rápidos para o build, mas não são necessários.
- No PowerShell use `Copy-Item .env.example .env`. Se o Git converter finais de linha, o `.gitattributes` do repositório já normaliza os scripts.
- Os comandos `docker compose ...` são idênticos em PowerShell, CMD e Git Bash.

### Apple Silicon (M1/M2/M3…) e outras máquinas ARM64

A imagem `gvenzl/oracle-free` é publicada também para **ARM64**, e as imagens da API (`eclipse-temurin`) e do Web (Node/Nginx) têm variante ARM64, portanto o Compose deve rodar nativamente. **Não foi validado em Apple Silicon** nesta demonstração (somente Windows/WSL2 x86-64). Se algo falhar, aumente a memória do Docker Desktop (Settings → Resources) para ao menos 6 GB e registre uma Issue com o log de `docker compose logs oracle`.

## Serviços

| Serviço | Imagem / build | Porta no host | Observações |
|---|---|---|---|
| `oracle` | `gvenzl/oracle-free:23.26.3-slim-faststart` (tag fixada; mesma imagem do `23-slim-faststart` usado nos testes) | 1521 (`ORACLE_PORT`) | volume `oradata`; healthcheck `healthcheck.sh`; `ORACLE_PASSWORD`, `APP_USER`, `APP_USER_PASSWORD` do `.env` |
| `api` | build `.` | 8080 (`API_PORT`) | depende de `oracle` saudável; profile `demo`; `jdbc:oracle:thin:@//oracle:1521/FREEPDB1`; volume `uploads`; healthcheck `/actuator/health` |
| `web` | build `../Sema_Nexus_Web` | 3000 (`WEB_PORT`; o container escuta 8080) | depende de `api` saudável; Nginx com proxy `/api` → `api:8080` |

Volumes nomeados: `oradata` (`/opt/oracle/oradata`) e `uploads` (`/data/uploads`); o projeto compose se chama `sema-nexus`, então os volumes reais são `sema-nexus_oradata` e `sema-nexus_uploads`. Variáveis obrigatórias (o compose falha com mensagem clara se faltarem): `ORACLE_PASSWORD`, `APP_USER`, `APP_USER_PASSWORD`, `JWT_SECRET`.

**Validado na Issue #8** (Windows + WSL2): a partir de volumes novos, `docker compose up -d --build` deixou `oracle`, `api` e `web` `healthy`; login com o usuário de teste via `http://localhost:3000/api/auth/login` (proxy do Nginx) retornou 200; após `down` + `up` o seed não duplicou, o Flyway informou "up to date" e um arquivo gravado em `/data/uploads` continuou lá.

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
| Tamanho | ~283 MB descompactada (soma das camadas) na validação da Issue #2; a imagem final do compose aparece com ~423 MB em `docker images` |
| Segredos | nenhum `ENV`/`ARG` sensível; credenciais só em tempo de execução (`.env` → compose) |

```bash
docker build -t sema-nexus-api .
docker run --rm --entrypoint id sema-nexus-api            # uid=10001(semanexus)
docker run -d --name api-teste -p 18080:8080 sema-nexus-api
docker inspect api-teste --format '{{.State.Health.Status}}'   # healthy
docker rm -f api-teste
```

## Operação do dia a dia

| Ação | Comando | Dados |
|---|---|---|
| Subir (ou aplicar mudanças de código) | `docker compose up -d --build` | preservados |
| Ver estado | `docker compose ps` | — |
| Ver logs | `docker compose logs -f api` (ou `oracle`, `web`) | — |
| **Parar sem perder dados** | `docker compose down` | **preservados** (volumes mantidos) |
| Reiniciar um serviço | `docker compose restart api` | preservados |
| Reset completo | ver abaixo | **apagados** |

Scripts equivalentes — **Em desenvolvimento (Issue API#9)**: `./scripts/demo-up.sh` / `.\scripts\demo-up.ps1`, `demo-down` e `demo-reset`. Enquanto não forem mesclados, use os comandos da tabela.

O seed demonstrativo é **idempotente**: subir de novo não duplica usuários, setores ou equipes.

## Reset completo da demonstração

Apaga os volumes do ambiente demonstrativo (banco Oracle **e** uploads) e recarrega o seed. **Destrutivo e irreversível.** Só em ambiente de demonstração e só quando for isso mesmo que você quer:

```bash
docker compose down -v      # remove os volumes sema-nexus_oradata e sema-nexus_uploads
docker compose up -d --build
```

O script `demo-reset` (Issue API#9, em desenvolvimento) fará o mesmo pedindo confirmação e recusando profiles diferentes de `demo`/`dev`.

> O Claude Code está configurado para **nunca** executar `down -v`, `docker volume rm` ou `docker system prune` sem confirmação do proprietário.

## Segurança

- Nenhum segredo em Dockerfiles, imagens ou `docker-compose.yml`; tudo vem do `.env` (ignorado pelo Git).
- Containers da API e do Web rodam como usuário não-root.
- A credencial `admin@admin.com` / `12345678` é **exclusivamente demonstrativa**: existe somente nos profiles `demo`/`dev` e é gravada apenas como hash BCrypt. Se a API subir em outro profile (ex.: `SPRING_PROFILES_ACTIVE=prod`) com esse usuário no banco, ela **se recusa a iniciar** (ver Problemas comuns). Não reutilize essa senha em nenhum outro sistema.
- Não publique as portas 1521/8080/3000 em redes não confiáveis: o ambiente é de demonstração.

## Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| `required variable APP_USER is missing a value: defina APP_USER no .env` (ou `ORACLE_PASSWORD`, `APP_USER_PASSWORD`, `JWT_SECRET`) | `.env` não existe ou está sem a variável | `cp .env.example .env` e preencha as quatro variáveis obrigatórias |
| `unable to prepare context: path ".../Sema_Nexus_Web" not found` | o repositório do Web não está **ao lado** do da API | clone `Sema_Nexus_Web` na mesma pasta pai de `Sema_Nexus_API` (ver Estrutura de pastas) |
| `port is already allocated` / `Bind for 0.0.0.0:8080 failed` | outra aplicação usa a porta 8080, 3000 ou 1521 (comum: outro Oracle local na 1521) | defina `API_PORT`, `WEB_PORT` ou `ORACLE_PORT` no `.env` (ex.: `API_PORT=18080`) e rode `docker compose up -d`. **Não encerre** processos que você não iniciou |
| `oracle` não fica `healthy` (fica `starting` por mais de ~5 min ou vira `unhealthy`) | memória insuficiente para o Docker/WSL2, disco cheio ou 1ª inicialização lenta | veja `docker compose logs oracle`; garanta ≥ 6 GB para o Docker (seção Windows e WSL2) e espaço em disco; depois `docker compose up -d` |
| `api` reinicia em loop (`Restarting`) | `JWT_SECRET` curto (< 32 caracteres), senha do Oracle diferente da usada na criação do volume, ou API recusando o profile (próximas linhas) | `docker compose logs api` mostra o motivo; corrija o `.env` |
| `ORA-01017: invalid username/password` nos logs da API | a senha do `.env` mudou **depois** de o volume `oradata` ser criado — a senha só vale na criação do volume | volte à senha original ou faça o [reset](#reset-completo-da-demonstração) (apaga os dados demonstrativos) |
| `api` encerra com log `ERROR` citando o usuário de teste e o profile | `SPRING_PROFILES_ACTIVE` ≠ `demo`/`dev` com o usuário `admin@admin.com` já no banco (trava `TestUserProfileGuard`) | use `SPRING_PROFILES_ACTIVE=demo` no `.env` (padrão) |
| Login falha com 401 | profile sem seed (`prod`) ou usuário/senha digitados errado | `SPRING_PROFILES_ACTIVE=demo`; credenciais em `docs/DEMO.md` |
| `web` não sobe (`Waiting`/`dependency failed to start`) | `api` não ficou `healthy` | resolva primeiro o `api` (`docker compose logs api`) |
| `permission denied` em `/data/uploads` | uso de **bind mount** (pasta do host) em vez do volume nomeado; o container roda como UID 10001 | mantenha o volume nomeado `uploads` do compose; se usar pasta do host: `sudo chown -R 10001:10001 <pasta>` |
| Aviso do Flyway `Oracle 23.26 is newer than this version` | apenas informativo | nenhuma ação: a migração funciona |
| `localhost` não responde (timeout) embora `docker compose ps` mostre `healthy` | observado no Windows com cliente que tenta IPv6 primeiro; a causa não foi investigada | use `http://127.0.0.1:<porta>` no lugar de `localhost` |
| Build lento ou falha por rede na 1ª vez | download das imagens e dependências | repita `docker compose up -d --build`; o cache de build aproveita o que já baixou |
| Sem espaço em disco | imagens, cache e volumes acumulados | libere espaço **do que você reconhece** (`docker image prune` de imagens não usadas); evite `docker system prune`/`volume rm` sem saber o que apaga |

Para pedir ajuda, anexe a saída de `docker compose ps` e de `docker compose logs --tail 100 <serviço>` (sem copiar o conteúdo do `.env`).

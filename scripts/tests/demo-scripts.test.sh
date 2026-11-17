#!/usr/bin/env sh
# Testes dos scripts demo-up / demo-down / demo-reset (shell POSIX) — Issue #9.
# Usam um `docker` FALSO no PATH (registra as chamadas): não tocam no Docker real nem em volumes.
# Uso: sh scripts/tests/demo-scripts.test.sh   (SH_BIN=dash para escolher o interpretador dos scripts)
set -u
AQUI=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$AQUI/../.." && pwd)

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT INT TERM

FALHAS=0
TOTAL=0
ok()   { TOTAL=$((TOTAL + 1)); printf '  ok   %s\n' "$1"; }
fail() { TOTAL=$((TOTAL + 1)); FALHAS=$((FALHAS + 1)); printf '  FAIL %s\n' "$1"; }
check() { # check "descrição" comando...
  _d=$1; shift
  if "$@"; then ok "$_d"; else fail "$_d"; fi
}

# ---- docker falso ----------------------------------------------------------
mkdir -p "$TMP/bin"
cat > "$TMP/bin/docker" <<'SHIM'
#!/bin/sh
echo "$*" >> "$DOCKER_LOG"
if [ "${SHIM_DOCKER_DOWN:-0}" = 1 ] && [ "$1" = info ]; then exit 1; fi
if [ "$1" = compose ] && [ "$2" = ps ]; then echo "cid-$4"; fi
if [ "$1" = inspect ]; then echo "${SHIM_HEALTH:-healthy}"; fi
exit 0
SHIM
chmod +x "$TMP/bin/docker"

# ---- raiz isolada (cópia dos scripts + .env.example reais) -------------------
novo_root() {
  ROOT="$TMP/root-$1"
  rm -rf "$ROOT"
  mkdir -p "$ROOT/scripts"
  cp -R "$REPO/scripts/lib" "$ROOT/scripts/lib"
  cp "$REPO/scripts"/demo-*.sh "$ROOT/scripts/"
  cp "$REPO/.env.example" "$ROOT/.env.example"
  DOCKER_LOG="$ROOT/docker.log"
  : > "$DOCKER_LOG"
  export DOCKER_LOG
}

# executa <script> com o docker falso; saída em $ROOT/out; devolve o exit code
rodar() {
  _s=$1; shift
  ( cd "$ROOT" && env PATH="$TMP/bin:$PATH" DEMO_POLL_SECONDS=0 "$@" "${SH_BIN:-sh}" "scripts/$_s" < /dev/null > "$ROOT/out" 2>&1 )
}
log_tem()   { grep -q -- "$1" "$DOCKER_LOG"; }
saida_tem() { grep -q -- "$1" "$ROOT/out"; }

limpar_env() { unset SPRING_PROFILES_ACTIVE DEMO_RESET_CONFIRM DEMO_UP_TIMEOUT WEB_PORT API_PORT SHIM_HEALTH SHIM_DOCKER_DOWN; }
limpar_env

echo "demo-up"
novo_root up1
rodar demo-up.sh; rc=$?
check "sobe com sucesso (exit 0)" test "$rc" -eq 0
check "cria .env a partir de .env.example" test -f "$ROOT/.env"
check "executa 'compose up -d --build'" log_tem "compose up -d --build"
check "imprime o aviso AMBIENTE DEMONSTRATIVO" saida_tem "AMBIENTE DEMONSTRATIVO"
check "imprime URL da aplicação (porta padrão 3000)" saida_tem "http://localhost:3000"
check "imprime o login de teste" saida_tem "admin@admin.com"
check "não imprime a senha em texto puro" sh -c "! grep -q 12345678 '$ROOT/out'"
check "consulta o estado dos três serviços" sh -c "grep -q 'compose ps -q oracle' '$DOCKER_LOG' && grep -q 'compose ps -q api' '$DOCKER_LOG' && grep -q 'compose ps -q web' '$DOCKER_LOG'"

novo_root up2
printf 'WEB_PORT=4000\nAPI_PORT=18080 # comentário\nORACLE_PASSWORD=x\n' > "$ROOT/.env"
cp "$ROOT/.env" "$ROOT/.env.antes"
rodar demo-up.sh; rc=$?
check ".env existente é preservado" cmp -s "$ROOT/.env" "$ROOT/.env.antes"
check "usa as portas do .env nas URLs" sh -c "grep -q 'http://localhost:4000' '$ROOT/out' && grep -q 'http://localhost:18080' '$ROOT/out'"
check "não cita 'comentário' como parte da porta" sh -c "! grep -q 'comentário' '$ROOT/out'"

novo_root up3
rodar demo-up.sh SHIM_HEALTH=unhealthy; rc=$?
check "serviço unhealthy → falha (exit != 0)" test "$rc" -ne 0
check "mensagem cita unhealthy" saida_tem "unhealthy"
check "não imprime URLs quando falha" sh -c "! grep -q 'http://localhost' '$ROOT/out'"

novo_root up4
rodar demo-up.sh SHIM_HEALTH=starting DEMO_UP_TIMEOUT=1 DEMO_POLL_SECONDS=1; rc=$?
check "timeout aguardando healthchecks → falha" test "$rc" -ne 0
check "mensagem cita timeout" saida_tem "timeout"

novo_root up5
rodar demo-up.sh SHIM_DOCKER_DOWN=1; rc=$?
check "Docker inativo → falha" test "$rc" -ne 0
check "mensagem orienta iniciar o Docker" saida_tem "não está ativo"
check "não chama 'compose up' com Docker inativo" sh -c "! grep -q 'compose up' '$DOCKER_LOG'"

echo "demo-down"
novo_root down
rodar demo-down.sh; rc=$?
check "para com sucesso" test "$rc" -eq 0
check "executa 'compose down'" log_tem "compose down"
check "nunca usa -v" sh -c "! grep -q 'down -v' '$DOCKER_LOG' && ! grep -q 'volume' '$DOCKER_LOG'"

echo "demo-reset"
novo_root r1
rodar demo-reset.sh; rc=$?
check "sem confirmação → recusa (exit != 0)" test "$rc" -ne 0
check "sem confirmação → nada é apagado (sem 'compose down')" sh -c "! grep -q 'compose down' '$DOCKER_LOG'"
check "sem confirmação → nem sobe o ambiente" sh -c "! grep -q 'compose up' '$DOCKER_LOG'"

novo_root r2
rodar demo-reset.sh DEMO_RESET_CONFIRM=yes SPRING_PROFILES_ACTIVE=prod; rc=$?
check "profile prod (ambiente) mesmo confirmado → recusa" test "$rc" -ne 0
check "profile prod → nada é apagado" sh -c "! grep -q 'compose down' '$DOCKER_LOG'"
check "profile prod → mensagem cita demo/dev" saida_tem "demo/dev"

novo_root r3
printf 'SPRING_PROFILES_ACTIVE=prod\nDEMO_RESET_CONFIRM=yes\n' > "$ROOT/.env"
rodar demo-reset.sh; rc=$?
check "profile prod lido do .env → recusa" test "$rc" -ne 0
check "profile prod do .env → nada é apagado" sh -c "! grep -q 'compose down' '$DOCKER_LOG'"

novo_root r4
rodar demo-reset.sh DEMO_RESET_CONFIRM=yes SPRING_PROFILES_ACTIVE=demo,prod; rc=$?
check "lista com profile não permitido (demo,prod) → recusa" test "$rc" -ne 0
check "demo,prod → nada é apagado" sh -c "! grep -q 'compose down' '$DOCKER_LOG'"

novo_root r5
rodar demo-reset.sh DEMO_RESET_CONFIRM=sim; rc=$?
check "confirmação diferente de 'yes' → recusa" test "$rc" -ne 0
check "confirmação 'sim' → nada é apagado" sh -c "! grep -q 'compose down' '$DOCKER_LOG'"

novo_root r6
rodar demo-reset.sh DEMO_RESET_CONFIRM=yes; rc=$?
check "confirmado + profile padrão (demo) → conclui (exit 0)" test "$rc" -eq 0
check "executa 'compose down' sem -v" sh -c "grep -q 'compose down' '$DOCKER_LOG' && ! grep -q 'down -v' '$DOCKER_LOG'"
check "remove só os volumes do banco e dos uploads" log_tem "volume rm -f sema-nexus_oradata sema-nexus_uploads"
check "preserva o volume do modelo de IA (ollama)" sh -c "! grep -q 'ollama' '$DOCKER_LOG'"
check "remoção dos volumes acontece antes do 'up'" sh -c "[ \$(grep -n 'volume rm' '$DOCKER_LOG' | head -1 | cut -d: -f1) -lt \$(grep -n 'compose up' '$DOCKER_LOG' | head -1 | cut -d: -f1) ]"
check "aguarda healthchecks e imprime URLs após o reset" saida_tem "http://localhost:3000"

novo_root r7
rodar demo-reset.sh DEMO_RESET_CONFIRM=yes SPRING_PROFILES_ACTIVE=demo,dev; rc=$?
check "profiles demo,dev → permitido" test "$rc" -eq 0

novo_root r8
printf 'DEMO_RESET_CONFIRM=yes\nSPRING_PROFILES_ACTIVE=dev\n' > "$ROOT/.env"
rodar demo-reset.sh; rc=$?
check "confirmação e profile dev lidos do .env → permitido" test "$rc" -eq 0

echo "segurança"
check "senha de teste não aparece nos scripts" sh -c "! grep -rq 12345678 '$REPO/scripts/demo-up.sh' '$REPO/scripts/demo-down.sh' '$REPO/scripts/demo-reset.sh' '$REPO/scripts/lib' '$REPO/scripts/demo-up.ps1' '$REPO/scripts/demo-down.ps1' '$REPO/scripts/demo-reset.ps1' '$REPO/Makefile'"

echo
echo "Resultado: $((TOTAL - FALHAS))/$TOTAL ok, $FALHAS falha(s)."
[ "$FALHAS" -eq 0 ]

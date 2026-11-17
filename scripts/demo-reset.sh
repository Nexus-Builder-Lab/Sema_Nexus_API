#!/usr/bin/env sh
# Restaura a demonstração: APAGA os volumes (banco Oracle e uploads), sobe de novo e aguarda o seed.
# DESTRUTIVO. Recusa profile diferente de demo/dev e exige confirmação explícita:
#   DEMO_RESET_CONFIRM=yes (ambiente do shell ou .env)  ou  digitar RESETAR quando solicitado.
# AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Ver docs/DOCKER.md.
set -eu
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
. "$SCRIPT_DIR/lib/demo-common.sh"

# 1. Profile: somente demo/dev (padrão do compose: demo).
perfil=$(env_get SPRING_PROFILES_ACTIVE demo)
ok=1
oldifs=$IFS
IFS=','
for p in $perfil; do
  p=$(printf '%s' "$p" | tr -d '[:space:]')
  case $p in demo | dev) ;; *) ok=0 ;; esac
done
IFS=$oldifs
if [ "$ok" != 1 ]; then
  die "demo-reset recusado: SPRING_PROFILES_ACTIVE='$perfil' não é demo/dev. Nada foi apagado."
fi

# 2. Confirmação explícita.
if [ "$(env_get DEMO_RESET_CONFIRM '')" = "yes" ]; then
  say "Confirmação recebida via DEMO_RESET_CONFIRM=yes."
elif [ -t 0 ]; then
  say "ATENÇÃO: isto apaga os volumes do ambiente demonstrativo (banco Oracle e uploads)."
  printf 'Digite RESETAR para continuar: '
  read -r resposta || resposta=
  if [ "$resposta" != "RESETAR" ]; then
    say "Reset cancelado. Nada foi apagado."
    exit 1
  fi
else
  die "demo-reset recusado: sem confirmação (defina DEMO_RESET_CONFIRM=yes ou execute em terminal interativo). Nada foi apagado."
fi

# 3. Reset.
require_docker
cd "$ROOT_DIR"
# Só o banco e os uploads: nunca 'down -v', que apagaria também o volume do modelo de IA (ollama) — Issue #122.
say "Removendo containers e os volumes do banco e dos uploads (o modelo de IA é preservado)..."
docker compose down || die "falha em 'docker compose down'."
docker volume rm -f sema-nexus_oradata sema-nexus_uploads || die "falha ao remover os volumes do banco e dos uploads."
say "Subindo novamente (o seed demonstrativo é recarregado pela API)..."
demo_up

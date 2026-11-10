#!/usr/bin/env sh
# Para o ambiente demonstrativo preservando os dados (volumes). Nunca usa -v.
# AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Ver docs/DOCKER.md.
set -eu
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
. "$SCRIPT_DIR/lib/demo-common.sh"

require_docker
cd "$ROOT_DIR"
docker compose down || die "falha em 'docker compose down'."
say "Ambiente parado. Os dados (volumes oradata e uploads) foram preservados."

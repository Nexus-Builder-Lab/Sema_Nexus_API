#!/usr/bin/env sh
# Sobe o ambiente demonstrativo (Oracle + API + Web) e aguarda os healthchecks.
# AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Ver docs/DOCKER.md.
set -eu
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
. "$SCRIPT_DIR/lib/demo-common.sh"

demo_up

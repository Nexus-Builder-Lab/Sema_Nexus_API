#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SAIDA="${1:-$DIR/../../src/main/resources/referencia/camadas}"
TOL="${2:-0.00015}"

python3 "$DIR/baixar-camadas-mt.py" --saida "$SAIDA" --tolerancia "$TOL"

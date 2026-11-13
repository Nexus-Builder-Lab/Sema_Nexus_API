#!/usr/bin/env bash
# Prepara a IA local dos agentes (Issue #106): sobe o serviço Ollama do projeto e baixa o modelo aberto.
# Usa COMPOSE_PROFILES do .env: "ia" (GPU NVIDIA) ou "ia-cpu" (sem GPU). Ver docs/AGENTES_IA.md.
# AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/demo-common.sh
. "$ROOT_DIR/scripts/lib/demo-common.sh"

perfis="$(env_get COMPOSE_PROFILES '')"
case ",${perfis// /}," in
  *,ia,*) servico=ollama ;;
  *,ia-cpu,*) servico=ollama-cpu ;;
  *) echo "ERRO: defina COMPOSE_PROFILES=ia (GPU NVIDIA) ou COMPOSE_PROFILES=ia-cpu (sem GPU) no .env." >&2; exit 1 ;;
esac
modelo="$(env_get OLLAMA_MODELO 'qwen3:4b')"
if ! [[ "$modelo" =~ ^[a-z0-9][a-z0-9._-]{1,60}(:[a-z0-9._-]{1,40})?$ ]]; then
  echo "ERRO: OLLAMA_MODELO inválido: '$modelo'." >&2; exit 1
fi

cd "$ROOT_DIR"
echo "Subindo o serviço '$servico'..."
docker compose up -d "$servico"
echo "Aguardando o Ollama responder..."
for _ in $(seq 1 40); do
  if docker compose exec -T "$servico" ollama list >/dev/null 2>&1; then pronto=1; break; fi
  sleep 3
done
[ "${pronto:-0}" = 1 ] || { echo "ERRO: o Ollama não respondeu. Veja: docker compose logs $servico" >&2; exit 1; }
echo "Baixando o modelo '$modelo' (só na primeira vez)..."
docker compose exec -T "$servico" ollama pull "$modelo"
echo "Aquecendo o modelo..."
docker compose exec -T "$servico" ollama run "$modelo" "/no_think Responda apenas: pronto" >/dev/null || true
echo
echo "IA local pronta: modelo '$modelo' no serviço '$servico'. Conferir: GET /api/agentes/status (modo LOCAL)."

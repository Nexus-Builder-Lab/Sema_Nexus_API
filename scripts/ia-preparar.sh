#!/usr/bin/env bash
# Prepara a IA local dos agentes (Issue #106): sobe o serviço Ollama do projeto e baixa o modelo aberto.
# Usa COMPOSE_PROFILES do .env: "ia" (GPU NVIDIA) ou "ia-cpu" (sem GPU). Ver docs/AGENTES_IA.md.
# Também baixa EMBEDDINGS_MODELO (padrão nomic-embed-text, Apache 2.0) da busca híbrida do DocIA (Issue #141), salvo
# EMBEDDINGS_ATIVO=false; falha nele só gera aviso (a busca fica lexical).
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
modelo="$(env_get OLLAMA_MODELO 'qwen3:4b-instruct')"
if ! [[ "$modelo" =~ ^[a-z0-9][a-z0-9._-]{1,60}(:[a-z0-9._-]{1,40})?$ ]]; then
  echo "ERRO: OLLAMA_MODELO inválido: '$modelo'." >&2; exit 1
fi
embeddings_ativo="$(env_get EMBEDDINGS_ATIVO 'true')"
modelo_embeddings="$(env_get EMBEDDINGS_MODELO 'nomic-embed-text')"
if ! [[ "$modelo_embeddings" =~ ^[a-z0-9][a-z0-9._/-]{1,60}(:[a-z0-9._-]{1,40})?$ ]]; then
  echo "ERRO: EMBEDDINGS_MODELO inválido: '$modelo_embeddings'." >&2; exit 1
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
if [ "$embeddings_ativo" != "false" ]; then
  echo "Baixando o modelo de embeddings '$modelo_embeddings' da busca híbrida (~0,3 GB, só na primeira vez)..."
  docker compose exec -T "$servico" ollama pull "$modelo_embeddings"     || echo "AVISO: falha ao baixar '$modelo_embeddings': a busca do DocIA segue só lexical até ele ser baixado." >&2
fi
echo "Aquecendo o modelo..."
docker compose exec -T "$servico" ollama run "$modelo" "/no_think Responda apenas: pronto" >/dev/null || true
echo
echo "IA local pronta: modelo '$modelo' no serviço '$servico'. Conferir: GET /api/agentes/status (modo LOCAL)."
if [ "$embeddings_ativo" != "false" ]; then
  echo "Busca híbrida: a API indexa os documentos com '$modelo_embeddings' em até 2 minutos (recuperacao=HIBRIDA)."
fi

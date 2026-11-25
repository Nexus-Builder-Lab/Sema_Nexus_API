#!/usr/bin/env bash
# Benchmark da busca do DocIA (Issue #141): textual (LEXICAL) x híbrida (AUTO) com o modelo de embeddings real.
# Usa a API já no ar (API_PORT do .env, em 127.0.0.1) e o conjunto fixo src/test/resources/benchmark/perguntas-busca.json
# (o mesmo do teste offline BenchmarkBuscaTest). Somente leitura. Requer curl e jq.
# Login: SEMA_EMAIL/SEMA_SENHA (ambiente) ou, sem eles, o login demonstrativo do perfil CONSULTA (profile demo).
# Uso: scripts/benchmark-busca.sh [saida.md]
# AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/demo-common.sh
. "$ROOT_DIR/scripts/lib/demo-common.sh"
command -v jq >/dev/null || die "instale o jq para rodar este script."

base="http://127.0.0.1:$(env_get API_PORT 18081)"
conjunto="$ROOT_DIR/src/test/resources/benchmark/perguntas-busca.json"
k="$(jq -r '.k' "$conjunto")"
saida="${1:-}"

if [ -n "${SEMA_EMAIL:-}" ] && [ -n "${SEMA_SENHA:-}" ]; then
  corpo="$(jq -n --arg e "$SEMA_EMAIL" --arg s "$SEMA_SENHA" '{email:$e, senha:$s}')"
  token="$(curl -fsS -H 'Content-Type: application/json' -d "$corpo" "$base/api/auth/login" | jq -r '.token')" \
    || die "não foi possível entrar na API em $base."
else
  token="$(curl -fsS -X POST "$base/api/auth/demo/CONSULTA" | jq -r '.token')" \
    || die "não foi possível entrar na API em $base (está no ar com o profile demo?)."
fi

detalhe=""
medir() {
  local modo="$1" resultados="[]" n i
  n="$(jq '.perguntas | length' "$conjunto")"
  for ((i = 0; i < n; i++)); do
    local p r
    p="$(jq -c ".perguntas[$i]" "$conjunto")"
    r="$(curl -fsS -G -H "Authorization: Bearer $token" --data-urlencode "q=$(jq -r '.pergunta' <<<"$p")" \
      --data-urlencode "limite=$k" --data-urlencode "modo=$modo" "$base/api/documentos/busca")"
    resultados="$(jq -c --argjson p "$p" --argjson r "$r" '. + [{pergunta: $p.pergunta, relevantes: $p.relevantes,
      recuperacao: $r.recuperacao, top: [$r.resultados[].documento.titulo]}]' <<<"$resultados")"
  done
  detalhe+="$(jq -r --arg m "$modo" '.[] | "| \($m) (\(.recuperacao)) | \(.pergunta) | \(if (.top|length)==0 then "(abstenção)" else (.top|join("; ")) end) |"' <<<"$resultados")"$'\n'
  jq -r --arg m "$modo" --argjson k "$k" '
    (map(select(.relevantes|length > 0))) as $resp | (map(select(.relevantes|length == 0))) as $fora |
    ($resp | map(select(.top|length > 0))) as $com |
    {
      p: (if ($resp|length)==0 then 0 else ([$com[] | ([.top[] as $t | select(.relevantes|index($t))] | length) / (.top|length)] | add // 0) / ($resp|length) end),
      a1: (([$com[] | . as $x | select($x.relevantes|index($x.top[0]))] | length) / ($resp|length)),
      r: (([$com[] | ([.top[] as $t | select(.relevantes|index($t))] | length) / ([$k, (.relevantes|length)]|min)] | add // 0) / ($resp|length)),
      ac: (if ($fora|length)==0 then 1 else (([$fora[] | select(.top|length == 0)] | length) / ($fora|length)) end),
      ai: (([$resp[] | select(.top|length == 0)] | length) / ($resp|length)),
      rec: ([.[].recuperacao] | unique | join("/"))
    } | "| \($m) | \(.rec) | \(.p*100|round/100) | \(.a1*100|round/100) | \(.r*100|round/100) | \(.ac*100|round/100) | \(.ai*100|round/100) |"
  ' <<<"$resultados"
}

tabela="| Modo pedido | Recuperação | Precisão@$k | Acerto@1 | Recall@$k | Abstenção correta | Abstenção indevida |
|---|---|---|---|---|---|---|
$(medir LEXICAL)
$(medir AUTO)"
say "$tabela"
grep -q '| AUTO | HIBRIDA |' <<<"$tabela" \
  || warn "o modo AUTO não foi híbrido em todas as perguntas: rode scripts/ia-preparar.sh e aguarde a indexação (~2 min)."
if [ -n "$saida" ]; then
  {
    printf '# Benchmark da busca do DocIA (Issue #141) — %s\n\n%s\n\n| Modo | Pergunta | Top-%s |\n|---|---|---|\n' \
      "$(date '+%Y-%m-%d %H:%M')" "$tabela" "$k"
    # O detalhe é recalculado (a substituição $(...) roda em subshell).
    for m in LEXICAL AUTO; do detalhe=""; medir "$m" >/dev/null; printf '%s' "$detalhe"; done
  } >"$saida"
  say "Detalhe gravado em $saida"
fi

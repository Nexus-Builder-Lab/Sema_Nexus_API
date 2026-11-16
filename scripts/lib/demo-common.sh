#!/usr/bin/env sh
# SEMA ALERTA — funções comuns dos scripts demo-up / demo-down / demo-reset (shell POSIX).
# AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Não execute diretamente: é carregado com `.`.
#
# Variáveis opcionais (ambiente do shell ou .env):
#   DEMO_UP_TIMEOUT      segundos de espera pelos healthchecks (padrão 600)
#   DEMO_POLL_SECONDS    intervalo entre verificações (padrão 5)

SERVICOS="oracle api web"

say()  { printf '%s\n' "$*"; }
warn() { printf 'AVISO: %s\n' "$*" >&2; }
die()  { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

# env_get CHAVE [PADRAO] — valor da variável no ambiente do shell; senão no .env; senão o padrão.
env_get() {
  _key=$1
  _default=${2-}
  eval "_val=\${$_key-}"
  if [ -z "$_val" ] && [ -f "$ROOT_DIR/.env" ]; then
    _val=$(grep -E "^[[:space:]]*$_key=" "$ROOT_DIR/.env" | tail -n 1 \
      | sed -E 's/^[^=]*=//; s/\r$//; s/[[:space:]]+#.*$//; s/^"(.*)"$/\1/; s/^'"'"'(.*)'"'"'$/\1/') || _val=
  fi
  if [ -z "$_val" ]; then _val=$_default; fi
  printf '%s' "$_val"
}

require_docker() {
  command -v docker >/dev/null 2>&1 || die "Docker não encontrado no PATH. Instale o Docker (ver docs/DOCKER.md)."
  docker info >/dev/null 2>&1 || die "O Docker não está ativo. Inicie o Docker Desktop/Engine e tente novamente."
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 não encontrado ('docker compose')."
}

# Cria o .env a partir do .env.example somente se ele não existir.
ensure_env() {
  if [ -f "$ROOT_DIR/.env" ]; then
    return 0
  fi
  [ -f "$ROOT_DIR/.env.example" ] || die ".env.example não encontrado em $ROOT_DIR."
  cp "$ROOT_DIR/.env.example" "$ROOT_DIR/.env"
  say ".env criado a partir de .env.example (valores de exemplo, só para a demonstração)."
  warn "troque as senhas e o JWT_SECRET no .env antes de usar fora de uma máquina de demonstração."
}

# Estado de saúde de um serviço do compose: healthy | unhealthy | starting | <status> | none
service_status() {
  _cid=$(docker compose ps -q "$1" 2>/dev/null || true)
  if [ -z "$_cid" ]; then
    printf 'none'
    return 0
  fi
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$_cid" 2>/dev/null \
    || printf 'none'
}

# Aguarda todos os serviços ficarem healthy (falha em unhealthy ou timeout).
wait_healthy() {
  _timeout=$(env_get DEMO_UP_TIMEOUT 600)
  _poll=$(env_get DEMO_POLL_SECONDS 5)
  _start=$(date +%s)
  say "Aguardando os serviços ficarem healthy (timeout ${_timeout}s)..."
  while :; do
    _all=1
    _resumo=
    for _svc in $SERVICOS; do
      _st=$(service_status "$_svc")
      _resumo="$_resumo $_svc=$_st"
      case $_st in
        healthy) ;;
        unhealthy) say "Estado:$_resumo"
                   die "o serviço '$_svc' ficou unhealthy. Veja: docker compose logs $_svc" ;;
        *) _all=0 ;;
      esac
    done
    if [ "$_all" = 1 ]; then
      say "Estado:$_resumo"
      return 0
    fi
    _agora=$(date +%s)
    if [ $((_agora - _start)) -ge "$_timeout" ]; then
      say "Estado:$_resumo"
      die "timeout de ${_timeout}s aguardando os serviços. Veja: docker compose ps && docker compose logs"
    fi
    say "  ...$_resumo"
    sleep "$_poll"
  done
}

print_urls() {
  _web=$(env_get WEB_PORT 3000)
  _api=$(env_get API_PORT 8080)
  say ""
  say "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS"
  say "  Aplicação : http://localhost:${_web}"
  say "  API       : http://localhost:${_api}"
  say "  Swagger   : http://localhost:${_api}/swagger-ui.html"
  say "  Login de teste (somente demonstração): admin@admin.com — senha em docs/DEMO.md"
}

demo_up() {
  require_docker
  ensure_env
  cd "$ROOT_DIR"
  docker compose up -d --build || die "falha em 'docker compose up -d --build'."
  wait_healthy
  print_urls
}

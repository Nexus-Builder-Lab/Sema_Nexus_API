# SEMA NEXUS — atalhos para os scripts de demonstração (Linux/macOS/WSL/Git Bash).
# AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Ver docs/DOCKER.md.
.PHONY: up down reset logs ps

up:      ## sobe o ambiente e aguarda os healthchecks
	./scripts/demo-up.sh

down:    ## para o ambiente preservando os dados
	./scripts/demo-down.sh

reset:   ## APAGA volumes e recarrega o seed (pede confirmação; recusa profile != demo/dev)
	./scripts/demo-reset.sh

logs:    ## acompanha os logs da API
	docker compose logs -f api

ps:      ## estado dos serviços
	docker compose ps

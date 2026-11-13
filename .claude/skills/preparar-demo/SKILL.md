---
name: preparar-demo
description: Prepara e confere o ambiente da demonstração do SEMA NEXUS (apresentação de 27/11/2026) — Docker, containers sema-nexus, .env, modo demo, satélite Esri, demo-reset e demo-up. Use quando o proprietário falar em apresentação, ensaio, demonstração, "deixar pronto", demo-reset, demo-up ou subir o ambiente.
---

# Preparar a demonstração

Referências: `docs/DEMO.md` (roteiro de 10 min, credenciais, cenário C1–C6) e `docs/DOCKER.md`.

## 1. Verificar (somente leitura — pode rodar sempre)

```
powershell -NoProfile -ExecutionPolicy Bypass -File .claude\skills\preparar-demo\verificar-ambiente.ps1
```

Confere: `.env` e profile `demo`; `ESRI_API_KEY` presente e com formato válido (**nunca exibe o valor**); Docker ativo; containers `sema-nexus-oracle/api/web` healthy; web em `127.0.0.1:WEB_PORT`; API em modo demo via `/api/auth/config`; Swagger; Nginx aceitando a chave e um tile Esri de teste. Saída 0 = pronto.

## 2. Corrigir o que faltar

| Falha | Ação |
|---|---|
| Docker parado | pedir ao proprietário para abrir o Docker Desktop |
| Container ausente/unhealthy | `.\scripts\demo-up.ps1` (sobe e aguarda os healthchecks) |
| Nginx não reconhece a chave | `.\scripts\demo-up.ps1` recria o `web` com a variável |
| Tile Esri falhou | conferir internet; a chave é do proprietário (ArcGIS Location Platform) — só ele a troca |
| Porta 3000/8080 ocupada em `localhost` | é a `dllhost` (IPv6, outro serviço): **não encerrar**; usar `127.0.0.1` |

## 3. Restaurar a massa (DESTRUTIVO — só com confirmação do proprietário na conversa)

`.\scripts\demo-reset.ps1` apaga **só** os volumes do projeto `sema-nexus` e recria o seed (120 demandas, 12 documentos). As datas do seed são **relativas ao dia do reset** → rodar **1 ou 2 dias antes** da apresentação.

- Peça confirmação explícita antes; a confirmação vale para aquela execução.
- O script exige `RESETAR` no terminal ou `DEMO_RESET_CONFIRM=yes`. Não grave `DEMO_RESET_CONFIRM=yes` no `.env`.
- Depois, rode a verificação do passo 1 de novo.

## 4. Lembretes para o proprietário (no dia)

- Abrir **http://127.0.0.1:3000** (nunca `localhost`), zoom **100%**, **Ctrl+F5** após qualquer atualização do container web.
- Login `admin@admin.com` (credencial demonstrativa — ver `docs/DEMO.md`).
- OpenStreetMap e Visão de satélite precisam de internet; o mapa de MT funciona offline.
- Em rede compartilhada as portas ficam expostas a outras máquinas → `.\scripts\demo-down.ps1` quando não estiver usando.
- Antes de voltar a usar sigfauna/sigatic, pare o SEMA NEXUS com `demo-down` (Oracle usa ~2 GB).

## Nunca

`docker compose down -v`, `docker volume rm`, `docker system prune`, parar containers de outros projetos, encerrar processos que você não iniciou, exibir o `.env`.

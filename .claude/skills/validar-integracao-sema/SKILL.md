---
name: validar-integracao-sema
description: Valida a integração API + Web do SEMA ALERTA no mesmo par de commits, com mocks desligados — SHAs dos dois repositórios, contrato OpenAPI real × cópia dos mocks da Web, perfis (401/403), modo real/demonstrativo dos agentes e, com autorização, o fluxo completo login → demanda → sugestão de triagem → aplicação humana → documento → resposta citada. Use antes de ensaio, tag ou apresentação, depois de mudar contrato/autorização/agentes, ou quando o proprietário pedir "prova de integração".
---

# Validar a integração (API + Web, mesmo par de commits)

Origem: relatório técnico do professor (apontamento P1 — "provar integração no mesmo par de snapshots"). Registro da
primeira execução: `docs/VALIDACAO_INTEGRADA.md`.

## Pré-requisitos
- Ambiente do Compose no ar (`docker compose ps`: oracle, api, web e, para IA local, ollama **healthy**), com a Web
  construída a partir de `../Sema_Nexus_Web` (a imagem usa `VITE_USE_MOCKS=false`).
- Sempre `127.0.0.1` e as portas do `.env` (`API_PORT=18081`, `WEB_PORT=3000`). Nunca `localhost`.
- Usa só os perfis de demonstração (`POST /api/auth/demo/{perfil}`, disponível com `demo=true`): nenhuma senha é lida,
  digitada ou impressa; tokens só em memória.

## 1. Somente leitura (pode rodar sempre)

```
node .claude/skills/validar-integracao-sema/validar-integracao.mjs
```

Confere: branch/SHA e arquivos não commitados dos dois repos; Web (Nginx) no ar e proxy `/api` chegando à API real;
modo demonstrativo; OpenAPI publicado com os campos de procedência (`modo`, `agente`, `aderenciaTextual`); **cópia do
contrato usada pelos mocks da Web (`Sema_Nexus_Web/src/mocks/openapi.json`) igual ao contrato real**; modo dos agentes
(`LOCAL`/`CLAUDE`/`DEMONSTRATIVO`); 401 sem token e com token adulterado; 403 de CONSULTA em `/api/equipes`.

Divergência de contrato → atualizar a cópia da Web a partir do artefato `openapi` do CI da API (ou de
`GET /v3/api-docs` do ambiente validado) e rodar `npm test` na Web.

## 2. Fluxo completo (grava dados fictícios — só com autorização do proprietário)

```
node .claude/skills/validar-integracao-sema/validar-integracao.mjs --fluxo --saida %TEMP%\validacao.json
```

ANALISTA cadastra uma demanda sintética, inicia a triagem, pede a sugestão ao Agente de Triagem (mede latência e
modo; confere que a sugestão **não grava**), aplica a sugestão como decisão humana, envia um documento sintético
vinculado, pergunta à DocIA (resposta com o fato, citação do documento enviado, trecho real e aderência textual) e
faz uma pergunta sem documento pertinente (abstenção). Depois percorre a **mesma** demanda e o **mesmo** documento
com CONSULTA e FISCAL (200/403 esperados) e confere a auditoria (chamadas de ferramenta e latência).

Depois do fluxo, os dados sintéticos ficam no banco: rodar o `demo-reset` (exige autorização; ver skill
`preparar-demo`) antes de ensaio ou apresentação.

## 3. Cenário de falha (opcional, com autorização)
Parar só o serviço `ollama` deste projeto (`docker compose stop ollama`), pedir uma sugestão e uma resposta da DocIA
(devem vir em modo **DEMONSTRATIVO**, identificado) e religar (`docker compose start ollama`). Nunca parar containers
de outros projetos.

## Regras
- Resultado vale para **SHA + diff**: o script informa arquivos não commitados; registre os dois SHAs no relatório.
- Agente de desenvolvimento (esta skill) só **verifica**; quem decide sobre dados operacionais é a pessoa. A skill não
  autoriza reset, tag, merge nem ações destrutivas.
- Não colar tokens, cookies ou cabeçalhos de autorização em relatórios.

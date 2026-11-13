---
name: verificar-seguranca
description: Checagem de segurança do SEMA NEXUS (backend, compose, scripts e docs) antes de abrir PR, mesclar ou preparar tag. Use ao revisar um diff, antes de qualquer PR/tag, ou quando o assunto for segredo, credencial, chave Esri, .env, usuário de teste admin@admin.com, dados pessoais ou dados reais.
---

# Verificar segurança — Sema_Nexus_API

Procedimento de checagem. **Nunca imprima o valor de um segredo** (nem parcial): informe só arquivo, linha e tipo.

## 1. Varredura automática do diff

Na raiz do repositório (ou no worktree do branch):

```
node .claude/skills/verificar-seguranca/verificar-diff.mjs            # base origin/main
node .claude/skills/verificar-seguranca/verificar-diff.mjs <base>     # outra base (ex.: a tag anterior)
```

- Analisa as linhas **adicionadas** (commits do branch + não commitadas + arquivos novos não rastreados).
- Compara com os valores reais de segredos do `.env` (`ESRI_API_KEY`, senhas, `JWT_SECRET`…) sem exibi-los.
- `BLOQUEANTE` → corrigir antes do PR. `AVISO` → conferir manualmente e justificar no PR.

## 2. Conferência manual (o que o script não cobre)

| Item | Como conferir |
|---|---|
| Usuário de teste só em `demo`/`dev` | `TestUserProfileGuard` intacto; `application-prod.yml` sem seed; `admin@admin.com` só como hash BCrypt no seed (`src/main/resources/demo/usuarios.json`) |
| Senha em texto puro | só em `README.md`, `docs/DEMO.md`, `docs/DOCKER.md` e testes |
| Validação de senha | `@Size(min = 8, ...)` em `UsuarioCriacaoRequest`/`UsuarioAtualizacaoRequest` não foi relaxada |
| Compose / Dockerfile | nenhum valor de segredo; só `${VAR}` / `${VAR:-}`; `.env.example` sem valores reais |
| Autorização | endpoint novo com `@PreAuthorize` + escopo; nada liberado em `SecurityConfig` além do necessário |
| SQL | só parametrizado (sem concatenar entrada do usuário) |
| Dados | só fictícios; exceções aprovadas: municípios/biomas IBGE, dados públicos do INPE (identificados) e imagens Esri |
| Logs | nenhum token, senha ou chave em log (inclusive `error_log` do Nginx: a localização Esri usa `crit`) |
| Textos oficiais | nada que pareça emitido pela SEMA-MT ou pelo Governo de MT |

## 3. Proibições (mesmo durante a verificação)

- Não rodar `docker compose down -v`, `docker volume rm`, `docker system prune`; não mexer em containers de outros projetos (sigfauna, sigatic).
- Não adicionar colaboradores, mudar permissões/visibilidade, criar tokens em nome do proprietário.
- Não colar a chave Esri ou o `.env` em Issue, PR, commit, log ou conversa.

## 4. Resultado

Registrar no PR: `Segurança: verificar-diff OK (0 bloqueantes, N avisos — justificativa)`. Achado em código já mesclado → abrir Issue `type:bug` P0 e avisar o proprietário.

Para o frontend (bundle, `VITE_*`, `nginx.conf`, imagem web) use a skill de mesmo nome no `Sema_Nexus_Web`.

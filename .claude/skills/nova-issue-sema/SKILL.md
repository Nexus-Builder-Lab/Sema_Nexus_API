---
name: nova-issue-sema
description: Cria Issues do SEMA NEXUS no padrão do projeto (título, seções, labels, campos do GitHub Project #1) usando os scripts de C:\SEMANEXUS\planejamento. Use quando o proprietário pedir uma correção, ajuste ou funcionalidade nova, ou quando for preciso registrar um bug, dependência ou mudança de Status no Project.
---

# Nova Issue no padrão SEMA NEXUS

Todo trabalho começa por uma Issue (uma por repositório; nunca misturar API e Web). Escopo novo que o proprietário **não pediu** é proposta, não Issue para implementar direto.

## 1. Repositório e título

| Mudança em | Repo (`repo` no JSON) | Título |
|---|---|---|
| backend, banco, compose, `scripts/`, docs transversais | `API` → `Nexus-Builder-Lab/Sema_Nexus_API` | `[SEMA_NEXUS_API][MÓDULO] ...` |
| telas, mapa, Nginx, imagem web | `WEB` → `Nexus-Builder-Lab/Sema_Nexus_Web` | `[SEMA_NEXUS_WEB][MÓDULO] ...` |

Módulos: `[CORE]` `[INTELLIGENCE]` `[FLUX]` `[DOCIA]` `[DATABASE]` (só API) `[INFRA]` `[TEST]` `[DOCS]`.

## 2. Corpo (seções obrigatórias, em português)

```
## Repositório
## Problema        (quem pediu e quando; para bug: o que acontece)
## Causa           (só bug, quando já conhecida)
## Escopo          (lista objetiva de arquivos/comportamentos)
## Critérios de aceitação   (- [ ] verificáveis; sempre "Lint, testes e build verdes" / "CI verde")
## Dependências    (API#n / Web#n ou "Nenhuma.")
## Estimativa      (horas)
```

## 3. Labels válidas

- Prioridade MVP: `P0` (obrigatória) · `P1` (desejável) · `P2` (futura).
- Tipo: `type:feature` · `type:bug` · `type:task` (**não existe `type:docs`** → use `type:task`).
- Módulo: `module:core` · `module:intelligence` · `module:flux` · `module:docia`.
- Fila/estado: `entrega-13-11`, `paridade-prototipo`, `aguardando-dependencia`, `autopilot-falhou`, `blocked`.

## 4. Criar (Issue + Project + campos)

Escreva um `.mjs` no scratchpad que grave o JSON e chame o script — **não** use `node -e` com o corpo inline (as crases e quebras de linha quebram o escape no PowerShell):

```js
import { writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
const f = '<scratchpad>/minha-issue.json';
const body = `## Repositório\n\`Nexus-Builder-Lab/Sema_Nexus_Web\`.\n\n## Problema\n...`;
writeFileSync(f, JSON.stringify({
  repo: 'WEB', key: 'CHAVE-UNICA', title: '[SEMA_NEXUS_WEB][CORE] ...',
  labels: ['P1', 'type:bug', 'module:core'], sprint: 2, mvp: 'P1',
  prioridade: 'Alta', modulo: 'Core', est: 1, limite: 'AAAA-MM-DD', status: 'Em andamento', body }));
console.log(execFileSync('node', ['nova-issue.mjs', f], { encoding: 'utf8', cwd: 'C:/SEMANEXUS/planejamento' }).trim());
```

Valores aceitos pelo Project:
- `prioridade`: `Crítica` · `Alta` · `Média` · `Baixa`
- `modulo`: `Core` · `SEMA Intelligence` · `SEMA Flux` · `SEMA DocIA` · `Infraestrutura` · `Qualidade` · `Documentação`
- `mvp`: `P0` · `P1` · `P2` · `sprint`: 1, 2 ou 3 · `status`: `Pendente` · `A fazer` · `Em andamento` · `PR solicitado` · `Finalizado`
- `key`: identificador único (fica em `planejamento/state.json`; é a chave usada pelos outros scripts).

## 5. Depois

- Dependência entre Issues: `node add-deps.mjs WEB#<n> API#<dep>` (em `C:\SEMANEXUS\planejamento`).
- Status no Project: `node status.mjs <KEY> "PR solicitado"` / `"Finalizado"`.
- Branch `feature/GH-<n>` (n da Issue **deste** repo), commits `GH-<n>: ...` (mensagem com aspas → `git commit -F arquivo`), PR `GH-<n> — <título>` com `Closes #<n>` e entrada em `docs/DIARIO_DESENVOLVIMENTO.md` no mesmo PR.
- Trabalho isolado: `git worktree add -B feature/GH-<n> C:\SEMANEXUS\_wt\<nome> origin/main` (no Web, criar junção para `node_modules`: `cmd /c mklink /J`; remover a junção com `rmdir` antes de remover o worktree).

Os scripts de `C:\SEMANEXUS\planejamento` ficam só na máquina do proprietário e usam o `gh` autenticado dele. Nunca criar tokens, convidar contas ou alterar permissões.

---
name: preparar-tag
description: Prepara uma nova versão (tag vX.Y.Z-demo) do SEMA NEXUS nos dois repositórios — checklist, mudanças desde a última tag, commits e notas. Use quando o proprietário falar em tag, versão, release ou "fechar uma versão". Só prepara; criar e enviar a tag exige autorização explícita do proprietário para aquela tag.
---

# Preparar tag de versão

**Regra:** tag e release só com autorização explícita do proprietário **na conversa**, citando a versão (ex.: "autorizo a tag v0.1.3-demo"). Autorização de uma tag não vale para a próxima. Sem autorização, entregue o checklist e pare.

Histórico: `v0.1.0-demo` (MVP), `v0.1.1-demo`, `v0.1.2-demo` — tags **anotadas**, mesmo nome nos dois repositórios, sem GitHub Release.

## 1. Levantar o que mudou (somente leitura)

Para cada repo (`Sema_Nexus_API` e `Sema_Nexus_Web`):

```
git fetch --tags -q
git describe --tags --abbrev=0 origin/main          # última tag
git log --oneline <ultima-tag>..origin/main         # commits novos (GH-<n>: ...)
gh pr list -R Nexus-Builder-Lab/<repo> --state open # PRs pendentes
gh run list -R Nexus-Builder-Lab/<repo> --branch main --limit 1   # CI da main
```

Nenhum commit novo nos dois → não há o que versionar.

## 2. Checklist (tudo precisa estar OK)

- [ ] CI verde no último commit da `main` dos dois repos; nenhum PR aberto que deveria entrar.
- [ ] Issues dos commits fechadas e com Status `Finalizado` no Project.
- [ ] Skill `verificar-seguranca` sem bloqueantes, usando a última tag como base: `node .claude/skills/verificar-seguranca/verificar-diff.mjs <ultima-tag>` (nos dois repos).
- [ ] Ambiente Docker atualizado com a `main` e skill `preparar-demo` OK (`verificar-ambiente.ps1` saída 0).
- [ ] Mudança visível conferida no navegador (Ctrl+F5) quando houver mudança de tela.
- [ ] Docs atualizados (README, `docs/DEMO.md`, diário).
- [ ] Mudou seed → lembrar o proprietário de rodar `demo-reset` antes da apresentação.

## 3. Proposta para o proprietário

Apresente: versão sugerida (incremento do patch, `v0.1.N-demo`, salvo pedido diferente), commit de cada repo (hash curto) e a mensagem:

```
SEMA NEXUS v0.1.N-demo — MVP demonstrativo (dados fictícios)

Sobre a v0.1.(N-1)-demo: <mudanças em uma frase cada, com API #n / Web #n>.
```

## 4. Somente após a autorização

Em cada repo, no commit aprovado (não necessariamente o checkout local):

```
git tag -a v0.1.N-demo <hash> -F <arquivo-com-a-mensagem>
git push origin v0.1.N-demo
```

Conferir com `git ls-remote --tags origin v0.1.N-demo`. Atualizar a memória/diário com a tag e os hashes. Nunca mover, apagar ou recriar uma tag já publicada sem nova autorização.

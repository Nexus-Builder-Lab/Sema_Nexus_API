# SEMA NEXUS — Autopilot (desenvolvimento autônomo com Claude Code)

> **Status:** Implementado em 10/11/2026. Autorizado pelo proprietário: merge automático condicionado, duas sessões em paralelo, auto mode.

## O que é

Duas sessões do Claude Code rodando sem supervisão, uma em `Sema_Nexus_API` e outra em `Sema_Nexus_Web`. Cada iteração executa o comando `/next-issue`, que:

1. retoma trabalho pendente (branch em andamento, PRs com `aguardando-dependencia` cujas dependências fecharam);
2. escolhe a próxima Issue **P0** desbloqueada (Sprint 1 → 2 → 3); sem P0 disponível, a próxima **P1** na ordem de `docs/SPRINTS.md`; **nunca P2** nem a Issue de tag (#22);
3. cria `feature/GH-<n>`, implementa, escreve testes, atualiza docs e o `docs/DIARIO_DESENVOLVIMENTO.md`;
4. valida: build/testes locais, critérios de aceitação com evidência e, quando o compose já existir, subida via Docker com login do usuário de teste;
5. abre o PR, espera o CI e faz **merge automático** só se as 5 condições forem verdadeiras (abaixo);
6. atualiza o Status no Project (Em andamento → PR solicitado → Finalizado).

## Condições para o merge automático

| # | Condição |
|---|---|
| 1 | Todos os checks do CI do PR verdes |
| 2 | Testes locais executados e aprovados na mesma execução |
| 3 | Todos os critérios de aceitação comprovados e marcados no PR |
| 4 | Docker Compose validado, quando já existir e a Issue afetar a execução |
| 5 | Todas as dependências ("Blocked by") fechadas, inclusive no outro repositório |

Faltando só a 5 → PR fica aberto com a label **`aguardando-dependencia`** e é mesclado numa iteração futura.

## Quando algo dá errado

Após **3 tentativas de correção** sem sucesso:
- nenhum merge; PR fica aberto (com diagnóstico) e a Issue recebe a label **`autopilot-falhou`**;
- uma entrada detalhada vai para **`C:\SEMANEXUS\autopilot\RELATORIO_ERROS.md`** (data, repo, Issue/PR, etapa, erro literal, tentativas, hipótese, reprodução, sugestão);
- o script para depois de **3 falhas seguidas** na mesma sessão, para revisão humana.

Para devolver uma Issue ao autopilot depois de corrigir: remova a label `autopilot-falhou` e marque a entrada do relatório como **Resolvido**.

## Como iniciar

Pré-requisitos: Docker Desktop aberto; `gh` autenticado (`gh auth status`); estar logado no Claude Code (`claude` já usado nesta máquina).

```powershell
# As duas sessões, cada uma em uma janela
powershell -ExecutionPolicy Bypass -File C:\SEMANEXUS\Sema_Nexus_API\scripts\autopilot\iniciar-duas-sessoes.ps1

# Ou uma de cada vez
powershell -ExecutionPolicy Bypass -File C:\SEMANEXUS\Sema_Nexus_API\scripts\autopilot\autopilot.ps1 -Repo API
powershell -ExecutionPolicy Bypass -File C:\SEMANEXUS\Sema_Nexus_API\scripts\autopilot\autopilot.ps1 -Repo WEB
```

## Como acompanhar

| Onde | O quê |
|---|---|
| [Project](https://github.com/orgs/Nexus-Builder-Lab/projects/1) — views "Sprint 1/2/3" | Status de cada Issue em tempo real |
| `C:\SEMANEXUS\autopilot\autopilot-API.log` / `autopilot-WEB.log` | Uma linha por iteração com o resultado |
| `C:\SEMANEXUS\autopilot\logs-API\` / `logs-WEB\` | Saída JSON completa de cada iteração |
| `C:\SEMANEXUS\autopilot\RELATORIO_ERROS.md` | Erros não corrigidos |
| `docs/DIARIO_DESENVOLVIMENTO.md` (em cada repo) | Histórico do que foi feito, decidido e testado |
| PRs mesclados | Resumo, testes e evidências de cada Issue |

## Como parar

```powershell
New-Item C:\SEMANEXUS\autopilot\STOP        # para as duas sessões ao fim da iteração atual
New-Item C:\SEMANEXUS\autopilot\STOP-WEB    # para só uma
```
Fechar a janela interrompe na hora (o branch em andamento é retomado na próxima execução).

## Limite de uso

As duas sessões compartilham o limite da conta. O script define `CLAUDE_CODE_RETRY_WATCHDOG=1` (o Claude Code aguarda a renovação, verificando a cada 10 min). Se a execução terminar com erro mesmo assim, o script espera 20 min e tenta de novo; o `/next-issue` retoma o branch em andamento.

## Segurança

- Modo `auto` com `--permission-prompts none`: ações que precisariam de aprovação são **negadas**, não ficam esperando.
- Regras `deny` do `.claude/settings.json` valem em qualquer modo: force push, apagar/editar repositório, mexer em colaboradores/permissões/visibilidade, `rm -rf` fora do projeto, apagar volumes Docker.
- Regras `ask` (tags, releases, push direto na `main`, `demo-reset`) viram **recusa** no autopilot.
- Ambiente desta máquina tratado pelo script: `JAVA_HOME` apontado para o JDK 21 (o do sistema é JDK 8); porta 8080 ocupada por outro processo (`dllhost`) — a API local usa 18080 e o processo existente nunca é encerrado.
- Docker: uma trava (`C:\SEMANEXUS\autopilot\docker.lock`) impede que as duas sessões subam o compose ao mesmo tempo.

## Arquivos

| Arquivo | Função |
|---|---|
| `.claude/commands/next-issue.md` (nos dois repos) | Passo a passo de uma iteração |
| `scripts/autopilot/autopilot.ps1` | Loop, ambiente, limites, logs, paradas |
| `scripts/autopilot/iniciar-duas-sessoes.ps1` | Abre as duas janelas |
| `CLAUDE.md` § 8.1 (nos dois repos) | Regra permanente do modo autopilot |

# SEMA NEXUS — Monitoramento diário (project-health)

> **Status:** Implementado (workflow e script versionados em 10/11/2026). A cobertura completa depende do secret `PROJECT_HEALTH_TOKEN` — **pendente de autorização do proprietário**.

## O que faz

`.github/workflows/project-health.yml` executa `.github/scripts/project-health.mjs` e publica um resumo em Markdown no **resumo da execução** do workflow (aba Actions → Project health → execução → Summary). É somente leitura: não altera status, não fecha Issues, não comenta.

| Verificação | Fonte |
|---|---|
| Issues atrasadas (P0/P1) | campo "Data limite" da tabela de classificação no corpo da Issue |
| Issues de sprint sem responsável | assignees |
| Issues bloqueadas (sprint atual ou anterior) | recurso nativo "Blocked by" |
| P0 ainda abertas | label `P0` |
| Em andamento há mais de 2 dias úteis | campo Status do Project (requer token) |
| PRs aguardando revisão | PRs abertos |
| Issues sem critérios de aceitação | seção "Critérios de aceitação" sem checklist |
| Branches fora de `feature/GH-<n>` | branches dos repositórios |
| Risco para o MVP | horas P0 abertas × capacidade restante (dias úteis até o limite de implementação 20/11 — meta 15/11 —, sem fins de semana e sem o feriado de 20/11, × 2 pessoas × 7 h); CI da `main` com falha |

## Quando roda

- Diariamente às **12:00 UTC (~08:00 em Cuiabá)**. Execuções agendadas do GitHub podem atrasar ou, em períodos de alta carga, ser puladas; o horário não é garantido.
- Manualmente: Actions → **Project health** → **Run workflow**, ou `gh workflow run project-health.yml -R Nexus-Builder-Lab/Sema_Nexus_API`.
- Workflows agendados só rodam a partir da branch padrão (`main`). O GitHub desativa agendamentos após 60 dias sem atividade no repositório.

## Permissões e o secret `PROJECT_HEALTH_TOKEN`

O `GITHUB_TOKEN` padrão só enxerga **este** repositório e **não lê Projects V2 de organização**. Sem configuração adicional, o resumo cobre apenas a API e informa a limitação.

Para cobertura completa (API + Web + campo Status do Project), o proprietário deve, **se autorizar**:

1. Criar um **fine-grained personal access token** (ou GitHub App) com acesso **somente leitura**:
   - Repositórios: `Sema_Nexus_API` e `Sema_Nexus_Web` — *Issues: Read*, *Pull requests: Read*, *Contents: Read*, *Actions: Read*, *Metadata: Read*.
   - Organização: *Projects: Read*.
   - Validade até 30/11/2026.
2. Salvar em **Settings → Secrets and variables → Actions → New repository secret** do `Sema_Nexus_API` com o nome `PROJECT_HEALTH_TOKEN`.

O Claude Code **não** cria tokens nem secrets.

O mesmo token (ou outro só com *Contents: Read* no Web) atende o secret `CROSS_REPO_TOKEN` do smoke test do Compose (Issue #20).

## Publicação em Issue

Avaliada e **não ativada**: publicar o resumo em uma Issue exigiria permissão de escrita (`issues: write`) e geraria notificações diárias. O resumo do workflow já atende sem efeitos colaterais. Se desejar, crie uma Issue "Acompanhamento diário" e adicione um passo com `gh issue comment`.

## Desativação após a apresentação

A partir de 28/11/2026 o script apenas informa que o projeto terminou. Para desligar:
```bash
gh workflow disable project-health.yml -R Nexus-Builder-Lab/Sema_Nexus_API
```

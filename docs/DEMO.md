# SEMA NEXUS — Demonstração de 27/11/2026

> **Status:** Planejado. O roteiro definitivo, com os registros exatos do seed, é entregue pela Issue API#21 e ensaiado em 26/11. Mostre na apresentação **somente** o que estiver implementado.
> **AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.** Nenhum dado oficial, real ou pessoal.

## Credenciais demonstrativas

| Perfil | E-mail | Senha | Situação |
|---|---|---|---|
| Administrador | `admin@admin.com` | `12345678` | Ativo — **usuário de teste padrão** |
| Gestor | `gestor@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Fiscal | `fiscal1@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Fiscal | `fiscal2@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Analista | `analista@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |
| Consulta | `consulta@demo.semanexus.local` | `Demo@Sema2026` | Ativo (seed — Issue #5) |

Setores fictícios: `DEMO-GAB` (Gabinete), `DEMO-FISC` (Fiscalização), `DEMO-DOC` (Análise e Documentação). Equipes fictícias (setor `DEMO-FISC`): Alfa (responsável Fiscal 1), Bravo (Fiscal 2), Charlie (Fiscal 1; membros Fiscal 1 e 2), Delta (Gestor; membros Fiscal 2 e Analista). Todos os nomes e e-mails são simulados.

Regras: essas contas existem **apenas** nos profiles `demo`/`dev`, são gravadas só com hash BCrypt e a API se recusa a subir em outro profile se `admin@admin.com` existir. Não reutilize essas senhas em nenhum outro sistema.

## Preparação (antes da apresentação)

1. Subir o ambiente: `.\scripts\demo-up.ps1` (ou `./scripts/demo-up.sh`).
2. Conferir `docker compose ps`: `oracle`, `api` e `web` **healthy**.
3. Restaurar a massa demonstrativa: `.\scripts\demo-reset.ps1` (confirmação explícita).
4. Abrir http://localhost:3000 e entrar com `admin@admin.com` / `12345678`.
5. Desconectar a internet e confirmar que mapa e telas continuam funcionando.

## Roteiro (~10 minutos) — esqueleto

| Tempo | Bloco | Mostrar |
|---|---|---|
| 0–1 | Apresentação | Problema, objetivo do SEMA NEXUS, faixa "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS" |
| 1–3 | Intelligence | Dashboard, indicadores, distribuição territorial por bioma, mapa de MT |
| 3–6 | Flux | Cadastrar demanda fictícia → triagem → distribuição → início → registro de fiscalização → conclusão |
| 6–8 | DocIA | Upload de documento, pesquisa, vínculo à demanda; comparação ou assistente **só se implementados** |
| 8–9 | Integração | Demanda com documento vinculado; indicadores do Intelligence atualizados |
| 9–10 | Arquitetura | React + Spring Boot + Oracle, Docker Compose, CI, próximos passos |

Use os **mesmos registros** do começo ao fim (cenário definido na Issue API#18).

## Limitações e futuro (a confirmar em 26/11)

- Dados exclusivamente fictícios; sem integração com sistemas institucionais.
- Busca documental textual (semântica é futura); sem OCR.
- Assistente DocIA, se presente, em modo demonstrativo identificado (sem IA generativa) salvo provider externo configurado.
- Sem geração de autos de infração.
- Futuro: busca semântica, OCR, ocorrências ambientais georreferenciadas, Kanban, integrações institucionais autorizadas.

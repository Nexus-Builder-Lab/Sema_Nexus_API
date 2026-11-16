---
name: dados-ficticios
description: Regras e estrutura do seed demonstrativo do SEMA ALERTA (usuários, setores, núcleos regionais, 120 demandas, 12 documentos, cenários C1–C6, datas relativas ao reset). Use ao criar ou alterar dados de seed, massa de teste, usuários demonstrativos, documentos fictícios, ou quando surgir a dúvida "posso usar este dado?".
---

# Dados fictícios do seed

## Princípio

**Só dados fictícios.** Exceções aprovadas, sempre identificadas: municípios, códigos e biomas/regiões do **IBGE** (reais); dados públicos do **INPE** (focos de calor e DETER, camada separada); imagens **Esri** (mapa de fundo). Nada de nomes de pessoas reais, CPF, e-mails reais, números de processos reais, textos que pareçam emitidos pela SEMA-MT/Governo de MT ou do protótipo como se fossem oficiais. Todo documento/texto gerado traz **"AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS"**.

## Onde fica

| O quê | Arquivo |
|---|---|
| Usuários, setores, equipes (senhas **só como hash BCrypt**) | `src/main/resources/demo/usuarios.json` → `DemoSeedDados`, `DemoSeedService` |
| 120 demandas (determinístico, semente 18) | `demo/DemoMassaGerador.java` (puro, sem banco) |
| 12 documentos PDF/TXT e vínculos | `demo/DemoMassaDocumentos.java` |
| Gravação idempotente | `DemoMassaService`, `DemoMassaRunner`, `DemoSeedRunner` (só profiles `demo`/`dev`) |
| INPE (público) | `src/main/resources/referencia/*.csv.gz` + `.properties` (`scripts/referencia/baixar-inpe.*`) |
| Documentação do cenário | `docs/DEMO.md` (credenciais, núcleos, tabela C1–C6) |

## Convenções

- E-mails: `<papel>@demo.semaalerta.local` (ex.: `fiscal.cba1@demo.semaalerta.local`); senha demonstrativa só em `docs/DEMO.md`/`docs/DOCKER.md`/README/testes. O `admin@admin.com` é o único fora do domínio e existe só em `demo`/`dev` (`TestUserProfileGuard`).
- Setores `DEMO-GAB`, `DEMO-FISC`, `DEMO-DOC` e núcleos regionais `NR-CBA` (Cuiabá), `NR-CAC` (Cáceres), `NR-ROO` (Rondonópolis), `NR-BAG` (Barra do Garças), `NR-SNO` (Sinop) = regiões intermediárias do IBGE (`MUNICIPIO.REGIAO`). Nos gráficos, rótulo curto "NR X".
- Demandas: marca `[DEMO-SEED]`; títulos dos cenários terminam com "(cenário de demonstração)"; o roteiro localiza pelo **título** (o número `DEM-AAAA/NNNN` varia com o dia).
- Contagem por status: 10 Pendente · 10 Em triagem · 12 Aguardando distribuição · 16 Distribuída · 26 Em andamento · 16 Aguardando conclusão · 30 Finalizada (6 por núcleo, tempos médios distintos).
- **Datas relativas ao dia do reset** (últimos 12 meses; queimadas em jul–out) → mudou o seed ou passou muito tempo: `demo-reset` (skill `preparar-demo`, com confirmação do proprietário).

## Ao alterar o seed

1. Manter determinismo (mesma semente → mesma massa) e idempotência (rodar de novo não duplica).
2. Não mudar os cenários C1–C6 sem atualizar a tabela e o roteiro de `docs/DEMO.md` (ensaiado pelo proprietário).
3. Não editar migration já mesclada; mudança de schema = nova migration Flyway.
4. Testes: `DemoMassaGeradorTest`, `DemoMassaDocumentosTest`, `DemoSeedServiceTest`, `DemoSeedRecursoTest`, `DemoSeedProfileTest` (sem Oracle); `DemoSeedOracleTest` (tag `oracle`).
5. Nova senha/usuário: gerar hash BCrypt; nunca gravar texto puro no JSON.
6. Rodar a skill `verificar-seguranca` antes do PR e avisar o proprietário que precisa de `demo-reset`.

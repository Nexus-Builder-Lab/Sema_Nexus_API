# SEMA NEXUS — Sprints e backlog

> Gerado a partir do backlog aprovado em 10/11/2026. Fonte da verdade do andamento: o [GitHub Project](https://github.com/orgs/Nexus-Builder-Lab/projects/1).
> **Status do documento:** Planejado. Nenhuma funcionalidade está implementada ainda.

## Calendário

| Marco | Data |
|---|---|
| Sprint 1 — Fundação | 10/11 a 14/11 (dias úteis: 10, 11, 12, 13) |
| Sprint 2 — Módulos | 15/11 a 20/11 (dias úteis: 16, 17, 18, 19 — **20/11 é feriado nacional**) |
| Sprint 3 — Integração e qualidade | 21/11 a 26/11 (dias úteis: 23, 24, 25) |
| **Fim da implementação (feature freeze)** | **25/11/2026** |
| Revisão, testes, ensaio e tag `v0.1.0-demo` (com autorização) | 26/11/2026 |
| Apresentação | 27/11/2026 |

Sem trabalho em fins de semana. Capacidade: 11 dias úteis × 7 h × 2 pessoas = **154 h**.

## Resumo

| | P0 | P1 | P2 | Total |
|---|:-:|:-:|:-:|:-:|
| Sema_Nexus_API | 22 (90 h) | 6 (26 h) | 2 (20 h) | 30 |
| Sema_Nexus_Web | 12 (64 h) | 6 (27 h) | 1 (6 h) | 19 |
| **Total** | **34 (154 h)** | **12 (53 h)** | **3 (26 h)** | **49** |

| Sprint | Dev Backend (P0) | Dev Frontend (P0) | Capacidade por pessoa |
|---|:-:|:-:|:-:|
| Sprint 1 | 29 h | 28 h | 28 h |
| Sprint 2 | 28 h | 28 h | 28 h |
| Sprint 3 | 20 h | 21 h | 21 h |

**Risco principal:** o P0 ocupa ~100% da capacidade. **P1 só entra com folga real**, na ordem: Assistente DocIA → Comparação documental → Indicadores/Painel → Notificações/Auditoria → Usuários/Equipes → Relatórios. "Executor sugerido" é um papel, não uma atribuição; atribua as Issues no GitHub.

## Sprint 1 — 10/11/2026 · limite 13/11/2026

**Objetivo:** Fundação: esqueletos, Oracle, autenticação, seed com usuário de teste, Docker ponta a ponta, layout e login.

| Issue | Título | MVP | Prioridade | Est. | Executor | Depende de |
|---|---|:-:|---|:-:|---|---|
| [API#1](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/1) | [INFRA][BACKEND] Esqueleto Spring Boot com padrões REST, tratamento de erros e OpenAPI | P0 | Crítica | 5 h | Dev Backend | — |
| [API#2](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/2) | [INFRA][BACKEND] Dockerfile multi-stage, .dockerignore e healthcheck da API | P0 | Crítica | 2 h | Dev Backend | API#1 |
| [API#3](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/3) | [DATABASE][BACKEND] Conexão Oracle, Flyway e schema do Core (municípios, setores, usuários, equipes, auditoria) | P0 | Crítica | 6 h | Dev Backend | API#1 |
| [API#4](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/4) | [CORE][BACKEND] Autenticação JWT com BCrypt, autorização por perfil e CORS | P0 | Crítica | 6 h | Dev Backend | API#3 |
| [API#5](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/5) | [DATABASE][BACKEND] Seed demonstrativo base com usuário de teste admin@admin.com (somente profiles demo/dev) | P0 | Crítica | 4 h | Dev Backend | API#3 |
| [API#6](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/6) | [CORE][BACKEND] Trava de inicialização que impede o usuário de teste fora dos profiles demo/dev | P0 | Crítica | 2 h | Dev Backend | API#5 |
| [API#7](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/7) | [CORE][BACKEND] Contrato OpenAPI dos endpoints P0 e endpoints de catálogos e estrutura | P0 | Alta | 4 h | Dev Backend | API#4, API#5 |
| [API#8](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/8) | [INFRA] docker-compose.yml com Oracle Free + API + Web, .env.example, healthchecks e volumes | P0 | Crítica | 4 h | Dev Frontend | API#2, API#5, Web#2 |
| [API#9](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/9) | [INFRA] Scripts demo-up, demo-down e demo-reset para Linux/macOS e Windows | P0 | Alta | 2 h | Dev Frontend | API#8 |
| [API#10](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/10) | [DOCS] docs/DOCKER.md e seção de execução no README da API | P0 | Alta | 2 h | Dev Frontend | API#8 |
| [Web#1](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/1) | [INFRA][FRONTEND] Esqueleto Vite + React + Bootstrap 5 + Router + Axios com ESLint, Prettier, Vitest e README | P0 | Crítica | 3 h | Dev Frontend | — |
| [Web#2](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/2) | [INFRA][FRONTEND] Dockerfile multi-stage, .dockerignore e proxy /api do Web (Nginx) | P0 | Crítica | 2 h | Dev Frontend | Web#1 |
| [Web#3](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/3) | [CORE][FRONTEND] Tema visual, layout principal com faixa demonstrativa e componentes reutilizáveis | P0 | Crítica | 8 h | Dev Frontend | Web#1 |
| [Web#4](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/4) | [CORE][FRONTEND] Cliente Axios, mocks MSW do contrato OpenAPI e login com rotas protegidas | P0 | Crítica | 7 h | Dev Frontend | Web#3, API#4, API#7 |

## Sprint 2 — 15/11/2026 · limite 19/11/2026

**Objetivo:** Módulos: fluxo completo do Flux, auditoria, indicadores, dashboards e mapa.

| Issue | Título | MVP | Prioridade | Est. | Executor | Depende de |
|---|---|:-:|---|:-:|---|---|
| [API#11](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/11) | [FLUX][BACKEND] Schema do Flux e endpoints de cadastro, edição, listagem e detalhes de demanda | P0 | Crítica | 9 h | Dev Backend | API#7 |
| [API#12](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/12) | [FLUX][BACKEND] Máquina de estados, triagem e distribuição de demandas | P0 | Crítica | 6 h | Dev Backend | API#11 |
| [API#13](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/13) | [FLUX][BACKEND] Acompanhamento, registro de fiscalização, conclusão e devolução de demandas | P0 | Alta | 5 h | Dev Backend | API#12 |
| [API#14](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/14) | [CORE][BACKEND] Auditoria centralizada das operações e consulta GET /api/auditoria | P0 | Alta | 3 h | Dev Backend | API#11 |
| [API#17](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/17) | [INTELLIGENCE][BACKEND] Indicadores agregados, pontos do mapa e dashboard inicial | P0 | Alta | 5 h | Dev Backend | API#11, API#14 |
| [Web#5](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/5) | [FLUX][FRONTEND] Telas de listagem e de cadastro/edição de demanda | P0 | Crítica | 7 h | Dev Frontend | Web#4, API#11 |
| [Web#6](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/6) | [FLUX][FRONTEND] Tela de detalhes da demanda com histórico, triagem e distribuição | P0 | Crítica | 7 h | Dev Frontend | Web#5, API#12 |
| [Web#7](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/7) | [FLUX][FRONTEND] Acompanhamento, registro de fiscalização e conclusão na tela da demanda | P0 | Alta | 4 h | Dev Frontend | Web#6, API#13 |
| [Web#8](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/8) | [INTELLIGENCE][FRONTEND] Dashboard inicial e dashboard ambiental com cards e gráficos | P0 | Alta | 5 h | Dev Frontend | Web#4, API#17 |
| [Web#9](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/9) | [INTELLIGENCE][FRONTEND] Mapa ambiental de Mato Grosso com Leaflet, filtros e legenda (offline) | P0 | Alta | 5 h | Dev Frontend | Web#4, API#17 |

## Sprint 3 — 21/11/2026 · limite 25/11/2026

**Objetivo:** DocIA, seed demonstrativo completo, integração final, testes, segurança, roteiro. 26/11: revisão, testes e tag.

| Issue | Título | MVP | Prioridade | Est. | Executor | Depende de |
|---|---|:-:|---|:-:|---|---|
| [API#15](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/15) | [DOCIA][BACKEND] Schema de documentos, armazenamento em volume, upload, download e extração de texto | P0 | Crítica | 6 h | Dev Backend | API#3, API#7 |
| [API#16](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/16) | [DOCIA][BACKEND] Biblioteca documental: pesquisa textual, filtros, detalhes e associação a demandas | P0 | Alta | 4 h | Dev Backend | API#15, API#11 |
| [API#18](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/18) | [DATABASE][BACKEND] Seed de demandas e documentos fictícios coerentes para a demonstração | P0 | Alta | 5 h | Dev Backend | API#13, API#16, API#5 |
| [API#19](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/19) | [TEST][BACKEND] Cobertura de testes dos serviços críticos e revisão de segurança da API | P0 | Alta | 4 h | Dev Backend | API#13, API#16 |
| [API#20](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/20) | [TEST][INFRA] Smoke test do Docker Compose em workflow manual e noturno | P0 | Alta | 3 h | Dev Frontend | API#8, API#5, Web#2 |
| [API#21](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/21) | [DOCS] Roteiro de demonstração de 10 minutos e cenário integrado em docs/DEMO.md | P0 | Alta | 2 h | Dev Frontend | API#18 |
| [API#22](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/22) | [INFRA] Checklist de entrega do MVP e tag v0.1.0-demo (somente após autorização) | P0 | Alta | 1 h | Dev Backend | API#19, API#20, API#21, Web#11, Web#12 |
| [Web#10](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/10) | [DOCIA][FRONTEND] Biblioteca documental, upload, detalhes, download e associação à demanda | P0 | Crítica | 8 h | Dev Frontend | Web#6, API#15, API#16 |
| [Web#11](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/11) | [CORE][FRONTEND] Integração final com a API real e revisão visual, responsiva e de acessibilidade | P0 | Crítica | 5 h | Dev Frontend | Web#7, Web#8, Web#9, Web#10 |
| [Web#12](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/12) | [TEST][FRONTEND] Testes de componentes e fluxos principais do frontend | P0 | Alta | 3 h | Dev Frontend | Web#7, Web#10 |
| [API#23](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/23) | [TEST][BACKEND] Testes de integração com Oracle Free via Testcontainers em job separado | P1 | Média | 4 h | Dev Backend | API#13, API#16 |
| [API#24](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/24) | [CORE][BACKEND] Gestão de usuários, setores e equipes (cadastro, edição, ativação, membros) | P1 | Média | 6 h | Dev Backend | API#7, API#14 |
| [API#25](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/25) | [CORE][BACKEND] Notificações de novas demandas, prazos, atrasos, distribuição e documentos | P1 | Média | 4 h | Dev Backend | API#12, API#16 |
| [API#26](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/26) | [DOCIA][BACKEND] Comparação documental de metadados e conteúdo textual | P1 | Média | 4 h | Dev Backend | API#16 |
| [API#27](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/27) | [DOCIA][BACKEND] Assistente DocIA com provider desacoplado, modo demonstrativo e citações | P1 | Média | 5 h | Dev Backend | API#16 |
| [API#28](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/28) | [INTELLIGENCE][BACKEND] Relatórios de demandas com filtros e exportação CSV | P1 | Baixa | 3 h | Dev Backend | API#17 |
| [Web#13](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/13) | [CORE][FRONTEND] Gestão de usuários, setores e equipes | P1 | Média | 6 h | Dev Frontend | Web#4, API#24 |
| [Web#14](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/14) | [CORE][FRONTEND] Central de notificações e consulta de auditoria | P1 | Média | 4 h | Dev Frontend | Web#3, API#25, API#14 |
| [Web#15](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/15) | [DOCIA][FRONTEND] Tela de comparação documental | P1 | Média | 4 h | Dev Frontend | Web#10, API#26 |
| [Web#16](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/16) | [DOCIA][FRONTEND] Assistente DocIA com referências e identificação do modo demonstrativo | P1 | Média | 4 h | Dev Frontend | Web#10, API#27 |
| [Web#17](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/17) | [INTELLIGENCE][FRONTEND] Tela de indicadores explicados e painel de fiscalização do Flux | P1 | Média | 5 h | Dev Frontend | Web#8, API#17 |
| [Web#18](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/18) | [INTELLIGENCE][FRONTEND] Relatórios com filtros e exportação CSV/PDF | P1 | Baixa | 4 h | Dev Frontend | Web#4, API#28 |

## Backlog futuro (P2 — fora do MVP)

| Issue | Título | Est. |
|---|---|:-:|
| [API#29](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/29) | [DOCIA][BACKEND] Busca semântica e OCR de documentos (futuro) | 12 h |
| [API#30](https://github.com/Nexus-Builder-Lab/Sema_Nexus_API/issues/30) | [INTELLIGENCE][BACKEND] Registro de ocorrências ambientais georreferenciadas (futuro) | 8 h |
| [Web#19](https://github.com/Nexus-Builder-Lab/Sema_Nexus_Web/issues/19) | [FLUX][FRONTEND] Quadro Kanban de demandas por status (futuro) | 6 h |

## Caminho crítico

- Login ponta a ponta: API#1 → API#3 → API#4 → API#5/API#7 → Web#4 (até 13/11).
- Docker ponta a ponta: API#2 + Web#2 → API#8 → API#9/API#10 (até 13/11); smoke API#20 (S3).
- Fluxo Flux: API#11 → API#12 → API#13 → Web#5 → Web#6 → Web#7 (até 19/11).
- Dashboards e mapa: API#17 → Web#8/Web#9 (até 19/11).
- DocIA: API#15 → API#16 → Web#10 (até 25/11) → seed completo API#18 → roteiro API#21.
- Entrega: API#19, API#20, API#21, Web#11, Web#12 → API#22 (26/11).

## Cortes pré-aprovados se o prazo apertar (em ordem)

1. Todos os P1.
2. Seed de documentos dentro de API#18 → fazer o upload ao vivo na demonstração.
3. Consulta `GET /api/auditoria` em API#14 → manter só a gravação.
4. Smoke test API#20 → smoke manual com `demo-up` documentado (**exige aprovação do proprietário**, pois a especificação o lista como obrigatório).

## Divergências do protótipo resolvidas a favor da especificação

7 status (não 6), 5 perfis (não 3), escopo só de fiscalização, credencial `admin@admin.com`, Oracle + BCrypt (não LocalStorage + FNV). Kanban, produtividade, backup JSON, matriz de permissões editável e ocorrências ambientais ficaram como P2/fora do MVP.

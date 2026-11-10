# SEMA NEXUS — Requisitos

> **Legenda de status:** Implementado · Em desenvolvimento · Planejado · Futuro.
> Em 10/11/2026 **nenhuma funcionalidade está implementada**: todo o MVP está **Planejado**. Atualize a coluna Status ao mesclar cada Issue.
> Sistema demonstrativo, com dados exclusivamente fictícios, restrito ao contexto ambiental de Mato Grosso (142 municípios; biomas Amazônia, Cerrado e Pantanal). Sem integração com SEMA-MT, SIGA, SISREV ou outros sistemas institucionais.

## Core

| Requisito | MVP | Status | Issues |
|---|:-:|---|---|
| Login por e-mail e senha com validação, mensagens de erro, sessão, logout, rotas protegidas e redirecionamento | P0 | Planejado | API#4, Web#4 |
| Usuário de teste `admin@admin.com` (somente demo/dev) e trava de inicialização | P0 | Implementado | API#5, API#6 |
| Aviso de ambiente demonstrativo na tela de login | P0 | Planejado | API#4, Web#4 |
| Dashboard inicial: demandas, em andamento, finalizadas, documentos, indicadores, atividades recentes, atalhos | P0 | Planejado | API#17, Web#8 |
| Layout com menu lateral, breadcrumbs, faixa "AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS" | P0 | Planejado | Web#3 |
| Auditoria de login/logout, cadastro, edição, exclusão lógica, status, distribuição e upload | P0 | Planejado | API#4, API#14, API#15 |
| Consulta de setores, equipes e fiscais (`GET /api/setores`, `/api/equipes`, `/api/equipes/{id}/membros`) e catálogos (municípios de MT, biomas, categorias, origens, prioridades, status) | P0 | Implementado | API#7 |
| Usuários: listagem, pesquisa, filtro por perfil, cadastro, edição, ativação, equipes | P1 | Planejado | API#24, Web#13 |
| Setores e equipes: cadastro, membros, responsáveis | P1 | Planejado | API#24, Web#13 |
| Notificações: novas demandas, prazos, atrasos, distribuição, documentos | P1 | Planejado | API#25, Web#14 |
| Tela de consulta de auditoria | P1 | Planejado | Web#14 |

## SEMA Intelligence

| Requisito | MVP | Status | Issues |
|---|:-:|---|---|
| Dashboard ambiental: cards e gráficos por mês, situação, município, bioma, categoria; filtros | P0 | Planejado | API#17, Web#8 |
| Mapa de MT (Leaflet) offline com marcadores fictícios, filtros, popups e legenda | P0 | Planejado | API#17, Web#9 |
| Indicadores com descrição e explicação | P1 | Planejado | Web#17 |
| Relatórios com filtros e exportação CSV/PDF | P1 | Planejado | API#28, Web#18 |
| Ocorrências ambientais georreferenciadas | P2 | Futuro | API#30 |

## SEMA Flux (somente demandas de fiscalização ambiental)

| Requisito | MVP | Status | Issues |
|---|:-:|---|---|
| Cadastro e edição de demanda com número automático `DEM-AAAA/NNNN` e validação territorial | P0 | Planejado | API#11, Web#5 |
| Listagem com paginação, pesquisa e filtros | P0 | Planejado | API#11, Web#5 |
| Detalhes com histórico, equipe, fiscal, prazo, documentos | P0 | Planejado | API#11, Web#6 |
| Triagem (classificar, priorizar, complementar, justificar) | P0 | Planejado | API#12, Web#6 |
| Distribuição (equipe, fiscal, prazo, observações) e consulta por equipe | P0 | Planejado | API#12, Web#6 |
| Acompanhamento, registro de fiscalização, conclusão e devolução | P0 | Planejado | API#13, Web#7 |
| Fluxo de 7 status com regras de transição e permissões | P0 | Planejado | API#12 |
| Painel de fiscalização com contadores e gráficos | P1 | Planejado | Web#17 |
| Kanban de demandas | P2 | Futuro | Web#19 |

## SEMA DocIA

| Requisito | MVP | Status | Issues |
|---|:-:|---|---|
| Upload validado (extensão, tamanho, tipo) com armazenamento fora do banco | P0 | Planejado | API#15, Web#10 |
| Biblioteca com pesquisa textual (título, descrição, palavras-chave, conteúdo) e filtros | P0 | Planejado | API#16, Web#10 |
| Detalhes, texto extraído e download | P0 | Planejado | API#15, API#16, Web#10 |
| Associação documento ↔ demanda | P0 | Planejado | API#16, Web#10 |
| Comparação documental (metadados, diff, trechos semelhantes) | P1 | Planejado | API#26, Web#15 |
| Assistente DocIA com citações, modo demonstrativo identificado e provider configurável | P1 | Planejado | API#27, Web#16 |
| Busca semântica e OCR | P2 | Futuro | API#29 |

Busca semântica **não** será simulada: enquanto não houver implementação real, a interface informa que a busca é textual.

## Integração entre módulos

1. Demandas do Flux possuem documentos do DocIA associados (API#16, Web#10).
2. Demandas cadastradas e finalizadas alimentam os indicadores do Intelligence (API#17).
3. Dados do Flux aparecem nos dashboards (API#17, Web#8).
4. O DocIA localiza documentos associados às demandas (API#16).
5. O Core controla usuários, permissões e auditoria (API#4, API#14).

## Requisitos não funcionais

| Tema | Requisito | Onde |
|---|---|---|
| Segurança | Autenticação JWT, autorização por perfil no backend, BCrypt, CORS restrito, validação no backend, upload seguro, segredos só em variáveis de ambiente, containers não-root, imagens sem segredos | API#4, API#15, API#19, API#2, Web#2 |
| Usabilidade | Responsivo, menu lateral, breadcrumbs, feedback de sucesso/erro, carregamento, estados vazios, confirmação de ações destrutivas, paginação e filtros | Web#3, Web#11 |
| Manutenibilidade | Camadas separadas, componentes reutilizáveis, erros centralizados, OpenAPI, testes automatizados | API#1, Web#3, API#19, Web#12 |
| Integridade | FKs, CHECKs, transações, exclusão lógica, seed identificado como fictício | API#3, API#11, API#18 |
| Execução | Subida completa com `docker compose up -d --build` a partir de ambiente limpo, sem internet | API#8, API#20 |

## Critérios de conclusão do MVP

Ver a Issue API#22 (checklist de entrega) e [DEFINITION_OF_DONE.md](DEFINITION_OF_DONE.md).

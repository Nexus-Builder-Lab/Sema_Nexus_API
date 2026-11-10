# SEMA NEXUS — Definition of Done

Uma Issue somente pode ir para **Finalizado** quando os critérios aplicáveis forem atendidos:

- [ ] Implementação dentro do escopo da Issue.
- [ ] Critérios de aceitação da Issue atendidos (todos marcados, com evidência).
- [ ] Código organizado, seguindo a arquitetura (ver [ARCHITECTURE.md](ARCHITECTURE.md)).
- [ ] Validações implementadas (no backend sempre; no frontend para usabilidade).
- [ ] Testes pertinentes **executados**.
- [ ] Testes **aprovados**.
- [ ] CI aprovada no PR.
- [ ] Funciona via Docker Compose (`docker compose up -d --build`), quando aplicável.
- [ ] Documentação atualizada, quando necessária (status Implementado / Planejado correto).
- [ ] Nenhum erro crítico conhecido.
- [ ] PR revisado e integrado (merge somente com autorização do proprietário).

## Regras

- Código escrito não é código concluído.
- Teste não executado não é teste aprovado. Nunca declare CI ou testes aprovados sem a execução real.
- Dados exibidos são sempre fictícios e identificados como tal.
- Nenhuma credencial no código, em imagens Docker ou no `docker-compose.yml`.
- Se a alteração quebrar a subida via Docker, a Issue não está concluída.

## MVP pronto (Issue API#22)

Login (incluindo usuário de teste), navegação, Core básico, dashboards do Intelligence, mapa, cadastro e acompanhamento de demandas, triagem e distribuição, cadastro e consulta de documentos, integrações essenciais, Oracle como banco principal, dados fictícios carregados, **subida completa com um único `docker compose up` a partir de ambiente limpo**, testes essenciais passando, CI rodando nos dois repositórios, documentação atualizada, roteiro de apresentação pronto e tag `v0.1.0-demo` criada após autorização. Recursos incompletos são registrados como limitações, nunca escondidos.

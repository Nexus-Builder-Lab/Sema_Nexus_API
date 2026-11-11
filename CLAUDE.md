# CLAUDE.md — SEMA NEXUS · Sema_Nexus_API

Referência permanente para sessões de desenvolvimento com Claude Code neste repositório.
Leia este arquivo inteiro antes de qualquer alteração.

---

## 1. Identidade do projeto

- **SEMA NEXUS**: plataforma **demonstrativa** de inteligência ambiental, gestão de demandas de fiscalização e gestão documental, no contexto de **Mato Grosso**.
- **Somente dados fictícios.** Municípios e biomas são referências geográficas reais (IBGE), mas tudo associado a eles é simulado.
- **Nenhuma integração institucional** (SEMA-MT, SIGA, SISREV ou outros). Não há autorização para acessar infraestrutura institucional.
- Nunca apresentar simulação como dado oficial. Telas e respostas com dados simulados exibem **"AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS"**.
- Prazo: implementação até **25/11/2026**; **26/11** revisão, testes e tag; apresentação **27/11/2026**. Máximo de 3 sprints.

## 2. Repositórios

| Repositório | Conteúdo |
|---|---|
| `Nexus-Builder-Lab/Sema_Nexus_API` (este) | Backend Spring Boot **e** infraestrutura/documentação transversal: `docker-compose.yml`, `scripts/demo-*`, `docs/`, `project-health.yml`, roteiro de demonstração. (Não há repositório hub — decisão de 10/11/2026.) |
| `Nexus-Builder-Lab/Sema_Nexus_Web` | Frontend React. |
| `FabricioJorge0/prototipo_SEMA_NEXUS` | Protótipo HTML/CSS/JS de referência visual e funcional. **Somente leitura — nunca alterar.** |

Backend → este repositório. Frontend → Sema_Nexus_Web. **Nunca misturar código dos dois.** Uma mudança que exija os dois repositórios = uma Issue por repositório, ligadas por dependência, e um PR por repositório.

## 3. Tecnologias

Java 21 · Spring Boot 3.5 (Web, Data JPA/Hibernate, Security, Validation, Actuator) · springdoc-openapi · **Oracle Database (23ai Free em Docker)** · ojdbc11 · **Flyway** · JWT + BCrypt · PDFBox · JUnit 5 · Mockito · Testcontainers · Maven · **Docker e Docker Compose** · GitHub Actions.
Frontend (outro repo): React (JavaScript), Bootstrap 5, React Router, Axios, Chart.js, Leaflet.
Não trocar tecnologia, não adicionar TypeScript nem outro banco sem aprovação do proprietário.

## 4. Arquitetura da API

```
src/main/java/br/com/nexusbuilderlab/semanexus/
  config/  security/  common/(exception, dto, audit)
  core/  flux/  intelligence/  docia/  demo/
src/main/resources/  application*.yml   db/migration/V*.sql   demo/*.json
```
- Fluxo por feature: `Controller → Service → Repository → Entity`, com `dto/`. Regra de negócio fica no Service, nunca no Controller.
- Prefixo `/api`; erros em `application/problem+json` (RFC 9457) pelo `GlobalExceptionHandler`; paginação `PageResponse` com `?page&size&sort`.
- Autorização **sempre no backend** (`@PreAuthorize` + escopo no service). Perfis: ADMIN, GESTOR, FISCAL, ANALISTA, CONSULTA.
- Máquina de estados da demanda (Pendente → Em triagem → Aguardando distribuição → Distribuída → Em andamento → Aguardando conclusão → Finalizada) — ver `docs/ARCHITECTURE.md`. "Atrasada" é derivada, não é status.
- Arquivos de documentos ficam no volume (`STORAGE_DIR`), **nunca no banco**; o Oracle guarda metadados e texto extraído.
- Contrato: OpenAPI gerado pelo springdoc; o CI publica `openapi.json` (consumido pelo Web para mocks). Mudou endpoint → atualize anotações e `docs/API.md`.

## 5. Regras de desenvolvimento

- Analise o código existente antes de alterar; reutilize; evite duplicação; respeite a arquitetura.
- Não insira credenciais no código. Configuração só por variáveis de ambiente.
- Crie testes para o que implementar. Testes unitários **não** dependem de Oracle; testes com Oracle usam Testcontainers e a tag `oracle` (job separado).
- Atualize a documentação afetada (distinguindo **Implementado / Em desenvolvimento / Planejado / Futuro**).
- Evite dependências desnecessárias. Não remova funcionalidades nem amplie escopo sem aprovação.
- Escopo novo = proposta: descreva, benefício, esforço, impacto no prazo, P0/P1/P2, recomendação. Só implemente após aprovação.

## 6. Banco de dados

- **Oracle é obrigatório.** Nunca usar PostgreSQL, MySQL, H2 ou SQLite, nem em testes.
- Schema só via **Flyway** (`db/migration/V<n>__descricao.sql`). Nunca editar migration já aplicada na `main`; crie uma nova.
- Integridade referencial, `CHECK` para enums, índices nas colunas filtradas, exclusão lógica (`EXCLUIDO_EM`) quando apropriado.
- SQL seguro (parâmetros, nunca concatenação). Senhas apenas com hash BCrypt.

## 7. Docker e credencial de teste (regra permanente)

- Toda funcionalidade deve continuar funcionando via `docker compose up -d --build` (compose na raiz deste repo, com `../Sema_Nexus_Web` ao lado). **Se quebrar a subida via Docker, não está concluído.**
- Nunca colocar segredos em Dockerfile, imagem ou `docker-compose.yml`. Use `.env` (ignorado pelo Git) e mantenha `.env.example` atualizado.
- **Nunca** executar `docker compose down -v`, `docker volume rm`, `docker system prune` ou equivalente sem confirmação explícita do proprietário.
- Credencial `admin@admin.com` / `12345678` é **exclusivamente demonstrativa**: criada só nos profiles `demo`/`dev`, gravada só como hash BCrypt, com trava (`security/TestUserProfileGuard`, um `ApplicationRunner` carregado em todos os profiles exceto `openapi`, que roda sem banco) que impede a API de subir fora de `demo`/`dev` se o usuário existir — o log de erro explica o motivo e nunca traz a senha. O texto puro só pode aparecer em README, `docs/DEMO.md`, `docs/DOCKER.md` e testes — nunca em código de produção ou logs. Não replicar essa senha em outro lugar. Não relaxar validações (senha mínima 8) para acomodá-la.

## 8. GitHub

- **Nunca** se adicionar como colaborador, convidar contas, alterar permissões de membros, alterar visibilidade ou excluir repositórios/histórico.
- Trabalhe sempre a partir de uma Issue. Não marque Issue como finalizada sem os critérios atendidos.
- Não invente resultados de CI ou de testes. Não faça merge, tag ou release sem autorização do proprietário — exceto o merge no **modo autopilot** (seção 8.1). Tag e release continuam exigindo autorização.

### Títulos de Issue (regra permanente)

- Toda Issue deste repositório (backend **e** infraestrutura/docs transversais) começa com **`[SEMA_NEXUS_API]`**, seguido do módulo ou tipo e do título: `[SEMA_NEXUS_API][FLUX] Endpoint de cadastro de demanda`.
- Issues do frontend começam com **`[SEMA_NEXUS_WEB]`** e ficam em `Sema_Nexus_Web`.
- Tags após o prefixo: `[CORE]` `[INTELLIGENCE]` `[FLUX]` `[DOCIA]` `[DATABASE]` `[INFRA]` `[TEST]` `[DOCS]`. Não usar `[BACKEND]`/`[FRONTEND]` (o prefixo já indica o repositório).

### Branches, commits e PRs (regra permanente)

- **Todo branch se chama `feature/GH-<número da Issue>`** — para qualquer tipo de trabalho (feature, bug, docs, tarefa). Nunca `fix/`, `chore/`, `docs/` ou sufixos.
- O número é o da Issue **deste** repositório. Um branch por Issue, criado da `main` atualizada, só ao iniciar a Issue.
- Issue já mesclada que precise de mais trabalho → nova Issue, novo branch.
- Commits: `GH-<n>: descrição curta`. PR: título `GH-<n> — <título da Issue>`, corpo com `Closes #<n>` (ou `Closes Nexus-Builder-Lab/Sema_Nexus_Web#<n>` para o outro repo), testes executados e evidências.
- O CI valida o nome do branch com `^feature/GH-[0-9]+$`.

## 8.1 Modo autopilot (autorizado pelo proprietário em 10/11/2026)

Quando executado pelo `/next-issue` (ou pelo script `scripts/autopilot/autopilot.ps1`), o Claude Code **pode fazer merge sem pedir autorização**, desde que **todas** as condições sejam verdadeiras:

1. todos os checks do CI do PR verdes;
2. testes locais executados e aprovados na mesma execução;
3. todos os critérios de aceitação da Issue comprovados com evidência e marcados no PR;
4. subida via Docker Compose validada, quando o `docker-compose.yml` já existir e a Issue afetar a execução;
5. todas as dependências ("Blocked by") da Issue fechadas, inclusive as do outro repositório.

Se faltar só a condição 5, o PR fica aberto com a label `aguardando-dependencia`. Se um problema persistir após **3 tentativas de correção**, não há merge: a Issue recebe a label `autopilot-falhou` e o erro é registrado em `C:\SEMANEXUS\autopilot\RELATORIO_ERROS.md`.
Toda Issue concluída acrescenta uma entrada em `docs/DIARIO_DESENVOLVIMENTO.md` (no mesmo PR).
Continuam proibidos no autopilot: tags/releases (incluindo `v0.1.0-demo`), push direto na `main`, force push, apagar volumes Docker, Issues P2, ampliar escopo e encerrar processos que o agente não iniciou. Detalhes: `docs/AUTOPILOT.md`.

Ambiente desta máquina: o `JAVA_HOME` do sistema é JDK 8 — use `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'`. A porta 8080 do host está ocupada por outro serviço — use 18080 (`--server.port=18080` ou `API_PORT=18080`).

## 8.2 Agentes e fluxo multiagente (definido pelo proprietário em 11/11/2026)

O desenvolvimento usa **subagentes especializados** em `.claude/agents/`, coordenados por um **orquestrador** (a sessão que executa `/next-issue` ou `/start-issue`):

| Agente | Papel | Edita código? | Modelo |
|---|---|:-:|---|
| `planejador-issue` | Plano da Issue: arquivos, passos, testes, critérios, riscos | não | Sonnet 5.5 · alto |
| `implementador-backend` | Implementa e corrige o escopo, com testes | sim | Sonnet 5.5 · alto |
| `validador-qa` | Build, testes, Docker e evidência de cada critério | não | Sonnet 5.5 · alto |
| `revisor-codigo` | Revisão independente do diff | não | Sonnet 5.5 · alto |
| `revisor-seguranca` | Auth, autorização, upload, segredos, logs, CORS | não | Sonnet 5.5 · alto |
| `documentador` | Diário, status nos docs, rascunho do PR | só docs | Sonnet 5.5 · alto |
| `diagnosticador` | Causa raiz de falha persistente (esporádico) | não | **Opus 5.5 · médio** |

Fluxo: planejar → implementar → **validar ∥ revisar código ∥ revisar segurança (quando aplicável)** → até 3 ciclos de correção (diagnosticador após o 2º) → documentar → PR → merge.
Regras: **somente o orquestrador** faz push, abre PR, faz merge, altera labels/Status e escreve no `RELATORIO_ERROS.md`. Subagentes de revisão, validação e diagnóstico são somente leitura. As regras deste `CLAUDE.md` e do `.claude/settings.json` valem para todos os agentes. Detalhes: `docs/AGENTES.md`.

## 9. Como executar uma Issue (use `/start-issue <n>`, ou `/next-issue` no autopilot)

1. Ler a Issue (`gh issue view <n>`).
2. Verificar dependências ("Blocked by") — não começar se houver bloqueio aberto, salvo autorização.
3. Verificar Sprint, prioridade e categoria MVP (P0 antes de P1; P2 não entra no MVP).
4. Analisar os arquivos envolvidos.
5. Planejar a implementação.
6. Implementar **apenas** o escopo.
7. Executar testes (`mvn -B verify`) e, quando aplicável, a subida via Docker.
8. Atualizar documentação.
9. Informar resultado, evidências e pendências.

## 10. Definition of Done

Uma Issue só está concluída quando, no que se aplicar:

- [ ] Implementação dentro do escopo.
- [ ] Critérios de aceitação atendidos.
- [ ] Código organizado.
- [ ] Validações implementadas.
- [ ] Testes pertinentes executados.
- [ ] Testes aprovados.
- [ ] CI aprovada.
- [ ] Funciona via Docker Compose.
- [ ] Documentação atualizada.
- [ ] Nenhum erro crítico conhecido.
- [ ] PR revisado e integrado (merge só com autorização).

Código escrito ≠ concluído. Teste não executado ≠ teste aprovado.

## 11. Comandos úteis

```bash
mvn -B verify                                   # build + testes unitários
mvn spring-boot:run -Dspring-boot.run.profiles=dev
docker compose up -d --build                    # ambiente completo (Oracle + API + Web)
docker compose ps && docker compose logs -f api
gh issue view <n> -R Nexus-Builder-Lab/Sema_Nexus_API
```

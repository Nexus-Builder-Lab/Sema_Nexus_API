# Revisão de segurança da API (Issue #19)

Checklist com evidência automatizada em `security/RevisaoSegurancaApiTest` (sem Oracle) e demais testes citados.

| Item | Resultado | Evidência |
|---|---|---|
| `@PreAuthorize` em todos os endpoints | OK. Leituras usam `Autorizacao.AUTENTICADO` (classe); escritas, regra por perfil no método. Só `AuthController` é exceção (login/config públicos, logout/me autenticados) | `todoEndpointTemPreAuthorize...` |
| Escopo do FISCAL | OK | `DemandaServiceTest` (detalhe, histórico, listagem por equipe) |
| Autorização por perfil | OK | `EndpointsContratoWebMvcTest` |
| CORS só em dev | OK | `CorsProfileTest`, `semCorsForaDoProfileDev` |
| Actuator só health, sem detalhes | OK | `actuatorSoExpoeHealth...`, `actuatorNaoPublicoAlemDoHealth` |
| Erros sem stacktrace | OK | `server.error.include-stacktrace=never`, `GlobalExceptionHandlerTest` |
| Sem senha/token/segredo em logs | OK (varredura de `log.*(...)` em `src/main`) | `logsNaoRegistramSenhaTokenOuSegredo` |
| Cabeçalhos de segurança (inclusive em 401) | OK | `respostaTrazCabecalhosDeSegurancaMesmoNoErro401` |
| Upload: extensão, tamanho, bytes, path traversal | OK | `DocumentoServiceTest`, `LocalFileStorageServiceTest` |
| Trava do usuário de teste | OK | `TestUserProfileGuardTest` |
| Máquina de estados, numeração concorrente, validações territoriais | OK | `DemandaStateMachineTest`, `DemandaOracleTest` (concorrência), `DemandaServiceTest` |
| Dependências sem CVE crítica | Não verificado nesta etapa (sem scanner configurado); recomenda-se OWASP Dependency-Check/Dependabot no CI | pendência |

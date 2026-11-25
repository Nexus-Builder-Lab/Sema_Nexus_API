# mcp-sema-alerta — servidor MCP local SOMENTE LEITURA

Servidor MCP (stdio, `@modelcontextprotocol/sdk` 1.32.2, MIT) que consulta a **API real** do SEMA ALERTA por HTTP com o JWT de um usuario. Toda a autorizacao (perfil e escopo) e a do backend; nao ha acesso ao Oracle nem ferramentas de escrita. Dados fictícios (ambiente demonstrativo).

## Ferramentas (todas GET, entradas validadas, saida limitada a 20.000 caracteres)
`listar_demandas` (`/api/demandas`, filtros) · `obter_demanda` (`/api/demandas/{id}`) · `prazos_criticos` (`/api/flux/prazos`) · `resumo_indicadores` (`/api/dashboard`) · `buscar_documentos` (`/api/documentos/busca`) · `alertas_inpe_municipio` (`/api/intelligence/referencia/por-municipio`, filtrado por `municipioId` e periodo).

O cliente so emite `GET` em uma lista fixa de caminhos; o unico `POST` e o login (`/api/auth/login` ou `/api/auth/demo/{perfil}`). 401 refaz o login uma vez; 403/404 viram erro claro; 429 respeita `Retry-After` (ate 10 s); timeout de 15 s. Nao ha opcao para desligar TLS. Token e senha nunca sao registrados.

## Variaveis de ambiente
| Variavel | Uso |
|---|---|
| `SEMA_API_URL` | base da API (padrao `http://127.0.0.1:3000/api`; direto na API: `http://127.0.0.1:<API_PORT>/api`) |
| `SEMA_TOKEN` | JWT ja obtido (nao e renovado) |
| `SEMA_USUARIO` / `SEMA_SENHA` | login normal |
| `SEMA_PERFIL_DEMO` | ex.: `GESTOR`; usa `/api/auth/demo/{perfil}`; tem precedencia sobre `SEMA_USUARIO`/`SEMA_SENHA` e so funciona com a API no profile demo (fora dele, 404) |

## Uso e teste
```
npm ci
node --test        # servidor HTTP falso; sem rede externa
```

## Ativar no Claude Code (NAO esta ativado neste repositorio)
Copie `mcp.json.exemplo` para o `.mcp.json` do seu projeto (ou use `claude mcp add`), exporte as variaveis no seu shell (nunca no Git) e aprove o servidor no Claude Code.

`municipioId` e o codigo IBGE. Respostas acima de 1 MB geram erro explicito.

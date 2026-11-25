#!/usr/bin/env node
import { fileURLToPath } from 'node:url';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { criarCliente } from './cliente.mjs';
import { ferramentas, limitar } from './ferramentas.mjs';

export function criarServidor(cliente) {
  const server = new McpServer({ name: 'mcp-sema-alerta', version: '1.0.0' });
  for (const f of ferramentas) {
    server.registerTool(
      f.nome,
      { description: f.descricao, inputSchema: f.schema, annotations: { readOnlyHint: true, openWorldHint: false } },
      async (args) => {
        try { return { content: [{ type: 'text', text: limitar(await f.chamar(cliente, args)) }] }; }
        catch (e) { return { isError: true, content: [{ type: 'text', text: `ERRO: ${e.message}` }] }; }
      });
  }
  return server;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  await criarServidor(criarCliente()).connect(new StdioServerTransport());
}

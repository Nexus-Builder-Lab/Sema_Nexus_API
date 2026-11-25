import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { criarCliente } from '../src/cliente.mjs';
import { criarServidor } from '../src/index.mjs';

const TOKEN = 'jwt-secreto-de-teste';
const reqs = [];
let srv, base, modo403 = false, n429 = 0, expirar = false;

before(async () => {
  srv = http.createServer((req, res) => {
    const auth = req.headers.authorization || null;
    reqs.push({ metodo: req.method, url: req.url, auth });
    res.setHeader('Content-Type', 'application/json');
    if (req.url === '/api/auth/login') { res.end(JSON.stringify({ token: TOKEN })); return; }
    if (expirar) { expirar = false; res.statusCode = 401; res.end('{}'); return; }
    if (auth !== `Bearer ${TOKEN}`) { res.statusCode = 401; res.end('{}'); return; }
    if (n429 > 0) { n429--; res.statusCode = 429; res.setHeader('Retry-After', '0'); res.end('{}'); return; }
    if (modo403) { res.statusCode = 403; res.end(JSON.stringify({ title: 'Forbidden', detail: 'perfil sem acesso' })); return; }
    if (req.url.startsWith('/api/intelligence/referencia/por-municipio')) {
      res.end(JSON.stringify([{ municipioId: 1, total: 3 }, { municipioId: 2, total: 9 }])); return;
    }
    res.end(JSON.stringify({ ok: true, url: req.url }));
  });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  base = `http://127.0.0.1:${srv.address().port}/api`;
});
after(() => srv.close());

async function par(extra = {}) {
  const cliente = criarCliente({ SEMA_API_URL: base, SEMA_USUARIO: 'u@x.gov', SEMA_SENHA: 'senha-secreta', ...extra });
  const server = criarServidor(cliente);
  const [a, b] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: 't', version: '1' });
  await Promise.all([server.connect(a), client.connect(b)]);
  return client;
}

test('ferramentas chamam os endpoints certos com Bearer e somente leitura', async () => {
  reqs.length = 0; modo403 = false;
  const c = await par();
  const nomes = (await c.listTools()).tools.map((t) => t.name).sort();
  assert.deepEqual(nomes, ['alertas_inpe_municipio', 'buscar_documentos', 'listar_demandas', 'obter_demanda', 'prazos_criticos', 'resumo_indicadores']);
  const esperado = [
    ['listar_demandas', { status: 'ABERTA' }, '/api/demandas?status=ABERTA'],
    ['obter_demanda', { id: 7 }, '/api/demandas/7'],
    ['prazos_criticos', {}, '/api/flux/prazos'],
    ['resumo_indicadores', {}, '/api/dashboard'],
    ['buscar_documentos', { q: 'queimada' }, '/api/documentos/busca?q=queimada'],
    ['alertas_inpe_municipio', { municipioId: 1, de: '2026-01-01' }, '/api/intelligence/referencia/por-municipio?de=2026-01-01'],
  ];
  for (const [nome, args, prefixo] of esperado) {
    const r = await c.callTool({ name: nome, arguments: args });
    assert.ok(!r.isError, nome);
    const ultima = reqs.at(-1);
    assert.ok(ultima.url.startsWith(prefixo), `${nome}: ${ultima.url}`);
    assert.equal(ultima.auth, `Bearer ${TOKEN}`);
  }
  const inpe = await c.callTool({ name: 'alertas_inpe_municipio', arguments: { municipioId: 1 } });
  assert.match(inpe.content[0].text, /"total": 3/);
  assert.doesNotMatch(inpe.content[0].text, /"total": 9/);
  const permitidos = [
    /^GET \/api\/(demandas|flux\/prazos|dashboard|documentos\/busca|intelligence\/referencia\/por-municipio)/,
    /^POST \/api\/auth\/login$/,
  ];
  for (const r of reqs) assert.ok(permitidos.some((re) => re.test(`${r.metodo} ${r.url}`)), `${r.metodo} ${r.url}`);
  assert.deepEqual([...new Set(reqs.map((r) => r.metodo))].sort(), ['GET', 'POST']);
  assert.equal(reqs.filter((r) => r.metodo === 'POST').length, 1); // so o login
});

test('entrada invalida e rejeitada e o cliente recusa caminhos fora da lista de leitura', async () => {
  reqs.length = 0;
  const c = await par();
  const r = await c.callTool({ name: 'obter_demanda', arguments: { id: '1; DROP' } }).catch(() => ({ isError: true }));
  assert.ok(r.isError);
  assert.equal(reqs.length, 0);
  const cli = criarCliente({ SEMA_API_URL: base, SEMA_TOKEN: TOKEN });
  await assert.rejects(() => cli.get('/usuarios'), /nao permitida/);
  await assert.rejects(() => cli.get('/demandas/1/historico'), /nao permitida/);
  assert.equal(reqs.length, 0);
});

test('403 da API e exibido como erro claro', async () => {
  modo403 = true;
  const c = await par();
  const r = await c.callTool({ name: 'resumo_indicadores', arguments: {} });
  assert.equal(r.isError, true);
  assert.match(r.content[0].text, /403/);
  modo403 = false;
});

test('401 refaz o login uma vez; 429 respeita Retry-After', async () => {
  reqs.length = 0; expirar = true;
  const c = await par();
  const r = await c.callTool({ name: 'resumo_indicadores', arguments: {} });
  assert.ok(!r.isError);
  assert.equal(reqs.filter((x) => x.metodo === 'POST').length, 2);
  n429 = 1;
  const r2 = await c.callTool({ name: 'resumo_indicadores', arguments: {} });
  assert.ok(!r2.isError);
});

test('token e senha nunca aparecem na saida', async () => {
  modo403 = true;
  const c = await par();
  const r = await c.callTool({ name: 'prazos_criticos', arguments: {} });
  assert.doesNotMatch(JSON.stringify(r), /jwt-secreto|senha-secreta/);
  modo403 = false;
});

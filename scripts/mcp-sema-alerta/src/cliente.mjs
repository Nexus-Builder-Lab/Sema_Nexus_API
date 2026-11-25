// Cliente HTTP SOMENTE LEITURA da API do SEMA ALERTA. Nao registra token nem senha.
const LEITURA = [
  /^GET \/(demandas|flux\/prazos|dashboard|documentos\/busca|intelligence\/referencia\/por-municipio)(\/\d+)?$/,
  /^GET \/auth\/(me|config)$/,
];
const LOGIN = [/^POST \/auth\/login$/, /^POST \/auth\/demo\/[A-Za-z_]+$/];

export class ErroApi extends Error {
  constructor(mensagem, status) { super(mensagem); this.status = status; }
}

async function lerLimitado(resp, max, chave) {
  const partes = [];
  let total = 0;
  if (!resp.body) return '';
  for await (const parte of resp.body) {
    total += parte.length;
    if (total > max) { try { await resp.body.cancel(); } catch { /* ignora */ } throw new ErroApi(`Resposta acima do limite de ${max} bytes em ${chave}; refine os filtros.`, 0); }
    partes.push(parte);
  }
  return Buffer.concat(partes).toString('utf8');
}

const dormir = (ms) => new Promise((r) => setTimeout(r, ms));

export function criarCliente(env = process.env, opcoes = {}) {
  const base = (env.SEMA_API_URL || 'http://127.0.0.1:3000/api').replace(/\/+$/, '');
  const basePath = new URL(base).pathname.replace(/\/+$/, '');
  const timeoutMs = opcoes.timeoutMs ?? 15000;
  const maxEspera = opcoes.maxEsperaRetryMs ?? 10000;
  const maxBytes = opcoes.maxBytes ?? 1_000_000;
  let token = env.SEMA_TOKEN || null;
  const tokenFixo = Boolean(env.SEMA_TOKEN);

  async function http(metodo, caminho, { query, corpo, publico } = {}) {
    const chave = `${metodo} ${caminho}`;
    const permitido = publico ? LOGIN : LEITURA;
    if (!permitido.some((re) => re.test(chave))) throw new ErroApi(`Requisicao nao permitida (somente leitura): ${chave}`, 0);
    const url = new URL(base + caminho);
    for (const [k, v] of Object.entries(query || {})) if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
    const headers = { Accept: 'application/json, application/problem+json' };
    if (corpo) headers['Content-Type'] = 'application/json';
    if (!publico && token) headers.Authorization = `Bearer ${token}`;
    let resp;
    try {
      resp = await fetch(url, { method: metodo, headers, body: corpo ? JSON.stringify(corpo) : undefined, signal: AbortSignal.timeout(timeoutMs), redirect: 'error' });
    } catch (e) {
      if (e.name === 'TimeoutError') throw new ErroApi(`Tempo esgotado (${timeoutMs} ms) em ${chave}`, 0);
      throw new ErroApi(`Falha de conexao com a API em ${chave}`, 0);
    }
    const texto = await lerLimitado(resp, maxBytes, chave);
    let json = null;
    try { json = texto ? JSON.parse(texto) : null; } catch {
      if (resp.status >= 200 && resp.status < 300) throw new ErroApi(`Resposta invalida (nao JSON) em ${chave}.`, 0);
    }
    return { status: resp.status, json, retryAfter: resp.headers.get('retry-after') };
  }

  async function login() {
    if (tokenFixo) throw new ErroApi('Token (SEMA_TOKEN) rejeitado pela API (401): gere outro token.', 401);
    let r;
    if (env.SEMA_PERFIL_DEMO) {
      r = await http('POST', `/auth/demo/${encodeURIComponent(env.SEMA_PERFIL_DEMO)}`, { publico: true });
    } else if (env.SEMA_USUARIO && env.SEMA_SENHA) {
      r = await http('POST', '/auth/login', { publico: true, corpo: { email: env.SEMA_USUARIO, senha: env.SEMA_SENHA } });
    } else {
      throw new ErroApi('Credenciais ausentes: defina SEMA_TOKEN, ou SEMA_USUARIO e SEMA_SENHA, ou SEMA_PERFIL_DEMO (API em modo demo).', 0);
    }
    if (r.status !== 200 || !r.json?.token) throw new ErroApi(`Login recusado pela API (HTTP ${r.status}).`, r.status);
    token = r.json.token;
  }

  async function get(caminho, query) {
    if (!token) await login();
    let relogou = false;
    let tentou429 = false;
    for (;;) {
      const r = await http('GET', caminho, { query });
      if (r.status === 401 && !relogou) { relogou = true; token = null; await login(); continue; }
      if (r.status === 429 && !tentou429) {
        tentou429 = true;
        const s = Number(r.retryAfter);
        const ms = Number.isFinite(s) && s >= 0 ? s * 1000 : 1000;
        if (ms > maxEspera) throw new ErroApi(`Limite de requisicoes (429): tente novamente em ${Math.ceil(ms / 1000)} s.`, 429);
        await dormir(ms);
        continue;
      }
      if (r.status >= 200 && r.status < 300) return r.json;
      const detalhe = r.json?.detail || r.json?.title || '';
      const msg = {
        401: 'Nao autenticado (401).', 403: 'Acesso negado (403): o perfil do usuario nao permite este recurso.',
        404: 'Nao encontrado (404).', 429: 'Limite de requisicoes (429) persistente.',
      }[r.status] || `Erro da API (HTTP ${r.status}).`;
      throw new ErroApi(detalhe ? `${msg} ${String(detalhe).slice(0, 300)}` : msg, r.status);
    }
  }

  return { get, basePath };
}

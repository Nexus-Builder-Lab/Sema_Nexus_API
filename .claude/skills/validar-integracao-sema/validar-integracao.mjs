#!/usr/bin/env node
// Validação integrada API + Web no mesmo par de commits (relatório técnico, P1).
//
//   node .claude/skills/validar-integracao-sema/validar-integracao.mjs            # só leitura
//   node .claude/skills/validar-integracao-sema/validar-integracao.mjs --fluxo    # + fluxo que GRAVA dados fictícios
//
// Opções: --api http://127.0.0.1:18081  --web http://127.0.0.1:3000  --saida relatorio.json
// Usa apenas os perfis de demonstração (POST /api/auth/demo/{perfil}, só com demo=true): nenhuma senha é lida,
// impressa ou gravada. Tokens ficam só em memória e nunca aparecem na saída.
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const args = process.argv.slice(2);
const opt = (nome, padrao) => {
  const i = args.indexOf(nome);
  return i >= 0 && args[i + 1] ? args[i + 1] : padrao;
};
const API = opt('--api', 'http://127.0.0.1:18081').replace(/\/$/, '');
const WEB = opt('--web', 'http://127.0.0.1:3000').replace(/\/$/, '');
const SAIDA = opt('--saida', null);
const FLUXO = args.includes('--fluxo');

const raizApi = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const raizWeb = resolve(raizApi, '..', 'Sema_Nexus_Web');

const relatorio = { inicio: new Date().toISOString(), api: API, web: WEB, fluxo: FLUXO, shas: {}, checagens: [], medidas: {} };
let falhas = 0;

function checar(grupo, descricao, ok, detalhe = '') {
  relatorio.checagens.push({ grupo, descricao, ok: Boolean(ok), detalhe: String(detalhe).slice(0, 300) });
  if (!ok) falhas++;
  console.log(`${ok ? 'OK  ' : 'FALHA'} [${grupo}] ${descricao}${detalhe ? ` — ${String(detalhe).slice(0, 160)}` : ''}`);
}

function git(raiz, ...a) {
  try {
    return execFileSync('git', ['-C', raiz, ...a], { encoding: 'utf8' }).trim();
  } catch {
    return null;
  }
}

async function http(metodo, url, { token, json, form } = {}) {
  const headers = { Accept: 'application/json, application/problem+json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  let body;
  if (json !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(json);
  } else if (form) {
    body = form;
  }
  const t0 = performance.now();
  const r = await fetch(url, { method: metodo, headers, body });
  const ms = Math.round(performance.now() - t0);
  const texto = await r.text();
  let dados = null;
  try {
    dados = texto ? JSON.parse(texto) : null;
  } catch {
    dados = texto;
  }
  return { status: r.status, dados, ms };
}

const api = (m, p, o) => http(m, `${API}${p}`, o);

async function entrar(perfil) {
  const r = await api('POST', `/api/auth/demo/${perfil}`);
  if (r.status !== 200 || !r.dados?.token) throw new Error(`login demo ${perfil}: HTTP ${r.status}`);
  return r.dados.token;
}

// ---------------------------------------------------------------- 1. Par de commits
for (const [nome, raiz] of [['api', raizApi], ['web', raizWeb]]) {
  const sha = git(raiz, 'rev-parse', 'HEAD');
  const ramo = git(raiz, 'branch', '--show-current');
  const alterados = (git(raiz, 'status', '--porcelain') ?? '').split('\n').filter(Boolean).length;
  relatorio.shas[nome] = { ramo, sha, arquivosAlterados: alterados };
  checar('commits', `${nome}: ${ramo}@${sha?.slice(0, 12)}`, Boolean(sha),
    alterados ? `${alterados} arquivo(s) não commitados (resultado vale para SHA + diff)` : 'árvore limpa');
}

// ---------------------------------------------------------------- 2. Web real, sem mocks
const saude = await http('GET', `${WEB}/healthz`);
checar('web', 'Web (Nginx) no ar', saude.status === 200, `HTTP ${saude.status}`);
const cfgViaWeb = await http('GET', `${WEB}/api/auth/config`);
const cfgDireto = await api('GET', '/api/auth/config');
checar('web', 'Proxy /api do Nginx chega à API real (mesma resposta)',
  cfgViaWeb.status === 200 && JSON.stringify(cfgViaWeb.dados) === JSON.stringify(cfgDireto.dados),
  `web ${cfgViaWeb.status} · api ${cfgDireto.status}`);
checar('web', 'Modo demonstrativo identificado pela API', cfgDireto.dados?.demo === true);

// ---------------------------------------------------------------- 3. Contrato OpenAPI real × cópia dos mocks
const contrato = await api('GET', '/v3/api-docs');
checar('contrato', 'OpenAPI publicado pela API', contrato.status === 200, `HTTP ${contrato.status}`);
const esquemas = contrato.dados?.components?.schemas ?? {};
const props = (s) => Object.keys(esquemas[s]?.properties ?? {});
checar('contrato', 'ReferenciaDocumento traz aderenciaTextual', props('ReferenciaDocumento').includes('aderenciaTextual'));
checar('contrato', 'RespostaAssistente identifica modo/agente/modelo/passos',
  ['modo', 'agente', 'modelo', 'passos'].every((p) => props('RespostaAssistente').includes(p)));
checar('contrato', 'SugestaoTriagem identifica o modo', props('SugestaoTriagem').includes('modo'));
const mocks = resolve(raizWeb, 'src', 'mocks', 'openapi.json');
if (existsSync(mocks)) {
  const copia = JSON.parse(readFileSync(mocks, 'utf8')).components?.schemas ?? {};
  const divergentes = Object.keys(esquemas).filter((s) => {
    const a = Object.keys(esquemas[s]?.properties ?? {}).sort().join();
    const b = Object.keys(copia[s]?.properties ?? {}).sort().join();
    return a !== b;
  });
  relatorio.medidas.contratoDivergente = divergentes;
  checar('contrato', 'Cópia do contrato usada pelos mocks da Web igual ao contrato real', divergentes.length === 0,
    divergentes.length ? `esquemas divergentes: ${divergentes.join(', ')}` : 'sem divergência');
}

// ---------------------------------------------------------------- 4. Modo dos agentes
const admin = await entrar('ADMIN');
const status = await api('GET', '/api/agentes/status', { token: admin });
relatorio.medidas.modoAgentes = status.dados?.modo;
checar('agentes', `Modo dos agentes: ${status.dados?.modo}`, status.status === 200 && Boolean(status.dados?.modo));

// ---------------------------------------------------------------- 5. 401 e 403 (somente leitura)
const semToken = await api('GET', '/api/auth/me');
checar('acesso', 'Sem token → 401', semToken.status === 401, `HTTP ${semToken.status}`);
const adulterado = await api('GET', '/api/auth/me', { token: `${admin.slice(0, -4)}xxxx` });
checar('acesso', 'Token adulterado → 401 (a Web encerra a sessão)', adulterado.status === 401, `HTTP ${adulterado.status}`);
const consulta = await entrar('CONSULTA');
const equipesConsulta = await api('GET', '/api/equipes', { token: consulta });
checar('acesso', 'CONSULTA lista equipes → 403', equipesConsulta.status === 403, `HTTP ${equipesConsulta.status}`);
const equipesAdmin = await api('GET', '/api/equipes', { token: admin });
checar('acesso', 'ADMIN lista equipes → 200', equipesAdmin.status === 200, `HTTP ${equipesAdmin.status}`);

// ---------------------------------------------------------------- 6. Fluxo que grava dados fictícios
if (FLUXO) {
  const marca = `VALINT${Date.now().toString(36).toUpperCase()}`;
  const analista = await entrar('ANALISTA');
  const fiscal = await entrar('FISCAL');
  const municipios = await api('GET', '/api/catalogos/municipios', { token: analista });
  const sorriso = (municipios.dados ?? []).find((m) => /sorriso/i.test(m.nome ?? m.descricao ?? '')) ?? municipios.dados?.[0];
  const hoje = new Date().toISOString().slice(0, 10);

  const criada = await api('POST', '/api/demandas', {
    token: analista,
    json: {
      titulo: `Validação integrada ${marca} — desmatamento em área de reserva (fictício)`,
      descricao: 'Denúncia FICTÍCIA para a validação integrada: supressão de vegetação nativa com uso de trator '
        + 'em área de reserva legal, cerca de 37 hectares, próxima a curso d’água. Dados sintéticos.',
      origem: 'DENUNCIA', municipioId: sorriso?.id, dataRecebimento: hoje,
    },
  });
  const id = criada.dados?.id;
  checar('fluxo', `ANALISTA cadastra demanda → ${criada.dados?.numero ?? '?'} (${criada.dados?.status ?? criada.status})`,
    criada.status === 201 || criada.status === 200, `HTTP ${criada.status}`);
  relatorio.medidas.demanda = { id, numero: criada.dados?.numero };

  const iniciar = await api('POST', `/api/demandas/${id}/triagem/iniciar`, { token: analista });
  checar('fluxo', `Inicia a triagem → ${iniciar.dados?.status ?? iniciar.status}`, iniciar.status === 200, `HTTP ${iniciar.status}`);

  const sug = await api('POST', `/api/demandas/${id}/triagem/sugestao`, { token: analista });
  relatorio.medidas.triagem = { ms: sug.ms, modo: sug.dados?.modo, modelo: sug.dados?.modelo,
    categoria: sug.dados?.categoria, prioridade: sug.dados?.prioridade };
  checar('fluxo', `Agente de Triagem sugere ${sug.dados?.categoria}/${sug.dados?.prioridade} (modo ${sug.dados?.modo}, ${sug.ms} ms)`,
    sug.status === 200 && Boolean(sug.dados?.modo), `HTTP ${sug.status}`);
  const depoisSugestao = await api('GET', `/api/demandas/${id}`, { token: analista });
  checar('fluxo', 'A sugestão não grava nada (status continua EM_TRIAGEM)', depoisSugestao.dados?.status === 'EM_TRIAGEM',
    depoisSugestao.dados?.status);

  const aplicada = await api('POST', `/api/demandas/${id}/triagem`, {
    token: analista,
    json: {
      decisao: 'CLASSIFICAR',
      categoria: sug.dados?.categoria ?? 'DESMATAMENTO_ILEGAL',
      prioridade: sug.dados?.prioridade ?? 'ALTA',
      justificativa: `Sugestão do agente revisada e confirmada pela analista. ${sug.dados?.justificativa ?? ''}`.slice(0, 2000),
    },
  });
  checar('fluxo', `Aplicação humana da sugestão → ${aplicada.dados?.status ?? aplicada.status}`,
    aplicada.status === 200 && aplicada.dados?.status === 'AGUARDANDO_DISTRIBUICAO', `HTTP ${aplicada.status}`);

  const form = new FormData();
  const texto = `RELATÓRIO DE VISTORIA ${marca} (FICTÍCIO)\nAMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS\n`
    + `A vistoria sintética ${marca} registrou 37 hectares de supressão de vegetação nativa em reserva legal `
    + `no município de ${sorriso?.nome ?? 'Sorriso'}. Foram observados rastros de trator e leiras de material lenhoso.\n`;
  form.append('arquivo', new Blob([texto], { type: 'text/plain' }), `vistoria-${marca}.txt`);
  form.append('titulo', `Relatório de vistoria ${marca}`);
  form.append('categoria', 'Relatório técnico');
  form.append('tipo', 'Relatório de vistoria');
  form.append('dataDocumento', hoje);
  form.append('demandaId', String(id));
  const doc = await api('POST', '/api/documentos', { token: analista, form });
  const docId = doc.dados?.id;
  checar('fluxo', `Documento sintético enviado e vinculado (id ${docId})`, doc.status === 201 || doc.status === 200, `HTTP ${doc.status}`);
  const vinculos = await api('GET', `/api/demandas/${id}/documentos`, { token: analista });
  const lista = Array.isArray(vinculos.dados) ? vinculos.dados : vinculos.dados?.content ?? [];
  checar('fluxo', 'Documento aparece na demanda', lista.some((d) => (d.id ?? d.documentoId) === docId), `HTTP ${vinculos.status}`);

  const pergunta = await api('POST', '/api/docia/perguntas', {
    token: analista, json: { pergunta: `Quantos hectares a vistoria ${marca} registrou?` },
  });
  const refs = pergunta.dados?.referencias ?? [];
  relatorio.medidas.docia = { ms: pergunta.ms, agente: pergunta.dados?.agente, modelo: pergunta.dados?.modelo,
    resposta: pergunta.dados?.resposta, referencias: refs.map((r) => ({ id: r.documentoId, aderencia: r.aderenciaTextual })) };
  checar('fluxo', `DocIA responde (${pergunta.dados?.agente}, ${pergunta.ms} ms): "${pergunta.dados?.resposta}"`,
    pergunta.status === 200 && /37/.test(pergunta.dados?.resposta ?? ''), `HTTP ${pergunta.status}`);
  checar('fluxo', 'Citação aponta o documento enviado, com trecho real e aderência textual',
    refs.some((r) => r.documentoId === docId && texto.includes(r.trecho?.replace(/…/g, '').trim().slice(0, 40)) && r.aderenciaTextual),
    JSON.stringify(refs.map((r) => [r.documentoId, r.aderenciaTextual])));

  const abst = await api('POST', '/api/docia/perguntas', {
    token: analista, json: { pergunta: 'Qual é a cotação do iene no mercado de câmbio de Tóquio?' },
  });
  relatorio.medidas.abstencao = { ms: abst.ms, agente: abst.dados?.agente, resposta: abst.dados?.resposta };
  checar('fluxo', `Pergunta sem documento pertinente → abstenção (${abst.ms} ms)`,
    /^N[aã]o h[aá] informa/i.test(abst.dados?.resposta ?? '') && (abst.dados?.referencias ?? []).length === 0,
    abst.dados?.resposta);

  // Segundo e terceiro perfis sobre a MESMA demanda e o MESMO documento
  const matriz = [];
  const tentar = async (perfil, token, acao, metodo, caminho, opcoes, esperado) => {
    const r = await api(metodo, caminho, { token, ...opcoes });
    matriz.push({ perfil, acao, http: r.status, esperado });
    checar('perfis', `${perfil}: ${acao} → ${r.status}`, r.status === esperado, `esperado ${esperado}`);
  };
  await tentar('ANALISTA', analista, 'ver a demanda', 'GET', `/api/demandas/${id}`, {}, 200);
  await tentar('ANALISTA', analista, 'ver o documento', 'GET', `/api/documentos/${docId}`, {}, 200);
  await tentar('CONSULTA', consulta, 'ver a demanda', 'GET', `/api/demandas/${id}`, {}, 200);
  await tentar('CONSULTA', consulta, 'ver o documento', 'GET', `/api/documentos/${docId}`, {}, 200);
  await tentar('CONSULTA', consulta, 'pedir sugestão de triagem', 'POST', `/api/demandas/${id}/triagem/sugestao`, {}, 403);
  await tentar('CONSULTA', consulta, 'triar a demanda', 'POST', `/api/demandas/${id}/triagem`,
    { json: { decisao: 'CLASSIFICAR', categoria: 'DESMATAMENTO_ILEGAL', prioridade: 'BAIXA', justificativa: 'tentativa' } }, 403);
  const formConsulta = new FormData();
  formConsulta.append('arquivo', new Blob(['texto ficticio'], { type: 'text/plain' }), 'x.txt');
  ['titulo', 'categoria', 'tipo'].forEach((c) => formConsulta.append(c, 'Tentativa'));
  formConsulta.append('dataDocumento', hoje);
  await tentar('CONSULTA', consulta, 'enviar documento', 'POST', '/api/documentos', { form: formConsulta }, 403);
  await tentar('FISCAL', fiscal, 'ver demanda não designada a ele', 'GET', `/api/demandas/${id}`, {}, 403);
  await tentar('FISCAL', fiscal, 'pedir sugestão de triagem', 'POST', `/api/demandas/${id}/triagem/sugestao`, {}, 403);
  relatorio.medidas.matrizPerfis = matriz;

  const aud = await api('GET', '/api/auditoria?size=20&sort=criadoEm,desc', { token: admin });
  const itens = aud.dados?.content ?? [];
  const exec = itens.find((a) => /AGENTE_(EXECUCAO|FALHA)/.test(a.acao ?? ''));
  checar('auditoria', 'Execução do agente auditada com chamadas e latência',
    Boolean(exec && /chamadas de ferramenta \d+; latência \d+ ms/.test(exec.detalhe ?? exec.descricao ?? '')),
    exec ? (exec.detalhe ?? exec.descricao ?? '').slice(0, 200) : `HTTP ${aud.status}`);
}

relatorio.fim = new Date().toISOString();
relatorio.falhas = falhas;
if (SAIDA) writeFileSync(SAIDA, JSON.stringify(relatorio, null, 2));
console.log(`\n${falhas === 0 ? 'APROVADO' : `REPROVADO (${falhas} falha(s))`} — ${relatorio.checagens.length} checagens`);
process.exit(falhas === 0 ? 0 : 1);

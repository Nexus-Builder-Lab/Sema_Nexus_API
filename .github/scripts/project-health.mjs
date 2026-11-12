// SEMA NEXUS — project-health. Gera um resumo em Markdown (stdout) com dados REAIS do GitHub.
// Somente leitura. Não altera Issues, status ou PRs. Sem dependências externas (Node 18+).
const ORG = 'Nexus-Builder-Lab';
const REPOS = ['Sema_Nexus_API', 'Sema_Nexus_Web'];
const PROJECT_NUMBER = 1;
const META_IMPLEMENTACAO = '2026-11-15'; // meta redefinida pelo proprietário em 12/11/2026
const FIM_IMPLEMENTACAO = '2026-11-20'; // limite máximo
const ENTREGA = '2026-11-26';
const APRESENTACAO = '2026-11-27';
const FERIADOS = ['2026-11-20'];
const HORAS_DIA_POR_PESSOA = 7;
const PESSOAS = 2;
const DIAS_EM_ANDAMENTO_ALERTA = 2;

const extra = process.env.PROJECT_HEALTH_TOKEN || '';
const token = extra || process.env.GITHUB_TOKEN || '';
const out = [];
const p = s => out.push(s);

const hoje = new Date().toLocaleDateString('sv-SE', { timeZone: 'America/Cuiaba' }); // AAAA-MM-DD
const br = d => (d ? d.split('-').reverse().join('/') : '—');

async function api(path, { method = 'GET', body } = {}) {
  const r = await fetch(path.startsWith('http') ? path : `https://api.github.com${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'sema-nexus-project-health',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!r.ok) {
    const e = new Error(`${r.status} ${path}`);
    e.status = r.status;
    throw e;
  }
  return r.json();
}
async function paged(path) {
  let all = [];
  for (let page = 1; page < 20; page++) {
    const sep = path.includes('?') ? '&' : '?';
    const d = await api(`${path}${sep}per_page=100&page=${page}`);
    all = all.concat(d);
    if (d.length < 100) break;
  }
  return all;
}

function diasUteis(de, ate) {
  let n = 0;
  const d = new Date(de + 'T12:00:00Z');
  const fim = new Date(ate + 'T12:00:00Z');
  while (d <= fim) {
    const dow = d.getUTCDay();
    const iso = d.toISOString().slice(0, 10);
    if (dow !== 0 && dow !== 6 && !FERIADOS.includes(iso)) n++;
    d.setUTCDate(d.getUTCDate() + 1);
  }
  return n;
}

function parseIssue(i, repo) {
  const b = i.body || '';
  const lim = b.match(/\|\s*Data limite\s*\|\s*(\d{2})\/(\d{2})\/(\d{4})\s*\|/);
  const est = b.match(/##\s*Estimativa\s+(\d+(?:[.,]\d+)?)\s*h/i);
  const spr = b.match(/\|\s*Sprint\s*\|\s*Sprint\s+(\d)/);
  const labels = i.labels.map(l => (typeof l === 'string' ? l : l.name));
  const crit = b.split(/##\s*Crit[eé]rios de aceita[cç][aã]o/i)[1] || '';
  return {
    repo, number: i.number, title: i.title, url: i.html_url,
    assignees: i.assignees.map(a => a.login),
    labels,
    mvp: labels.find(l => /^P[012]$/.test(l)) || '—',
    limite: lim ? `${lim[3]}-${lim[2]}-${lim[1]}` : null,
    estimativa: est ? Number(est[1].replace(',', '.')) : 0,
    sprint: spr ? Number(spr[1]) : null,
    temCriterios: /-\s*\[[ xX]\]/.test(crit.split(/\n##\s/)[0] || ''),
  };
}
const link = i => `[${i.repo.replace('Sema_Nexus_', '')}#${i.number}](${i.url})`;
const linha = i => `| ${link(i)} | ${i.title.replace(/\|/g, '/')} | ${i.mvp} | ${i.sprint ? 'S' + i.sprint : '—'} | ${br(i.limite)} | ${i.assignees.join(', ') || '—'} |`;
const tabela = (lista, vazio) => {
  if (!lista.length) return p(`_${vazio}_\n`);
  p('| Issue | Título | MVP | Sprint | Limite | Responsável |');
  p('|---|---|:-:|:-:|:-:|---|');
  lista.forEach(i => p(linha(i)));
  p('');
};

async function statusDoProject() {
  if (!extra) return null;
  const q = `query($org:String!,$n:Int!,$after:String){ organization(login:$org){ projectV2(number:$n){ items(first:100, after:$after){
    pageInfo{ hasNextPage endCursor }
    nodes{ updatedAt fieldValueByName(name:"Status"){ ... on ProjectV2ItemFieldSingleSelectValue { name updatedAt } }
      content{ ... on Issue { number repository{ name } } } } } } } }`;
  const mapa = new Map();
  let after = null;
  for (let k = 0; k < 10; k++) {
    const r = await api('/graphql', { method: 'POST', body: { query: q, variables: { org: ORG, n: PROJECT_NUMBER, after } } });
    if (r.errors) throw new Error(JSON.stringify(r.errors));
    const items = r.data.organization.projectV2.items;
    for (const it of items.nodes) {
      if (!it.content?.repository) continue;
      mapa.set(`${it.content.repository.name}#${it.content.number}`, {
        status: it.fieldValueByName?.name || 'Sem status',
        desde: it.fieldValueByName?.updatedAt || it.updatedAt,
      });
    }
    if (!items.pageInfo.hasNextPage) break;
    after = items.pageInfo.endCursor;
  }
  return mapa;
}

async function main() {
  p(`# 🩺 SEMA NEXUS — Saúde do projeto em ${br(hoje)}`);
  p(`Meta de implementação **${br(META_IMPLEMENTACAO)}** · limite **${br(FIM_IMPLEMENTACAO)}** · revisão/testes/tag em **${br(ENTREGA)}** · apresentação em **${br(APRESENTACAO)}**.\n`);

  if (hoje > APRESENTACAO) {
    p('> ✅ A apresentação já ocorreu. Desative este workflow: `gh workflow disable project-health.yml -R Nexus-Builder-Lab/Sema_Nexus_API`.');
    return;
  }

  const avisos = [];
  if (!extra) avisos.push('Secret `PROJECT_HEALTH_TOKEN` não configurado: o relatório cobre apenas os repositórios acessíveis ao `GITHUB_TOKEN` (normalmente só a API) e não lê o campo Status do Project.');

  const abertas = [];
  const fechadas = [];
  const prs = [];
  const branchesFora = [];
  const ci = [];
  const acessiveis = [];

  for (const repo of REPOS) {
    try {
      const issues = (await paged(`/repos/${ORG}/${repo}/issues?state=all`)).filter(i => !i.pull_request);
      issues.filter(i => i.state === 'open').forEach(i => abertas.push(parseIssue(i, repo)));
      issues.filter(i => i.state === 'closed').forEach(i => fechadas.push(parseIssue(i, repo)));
      (await paged(`/repos/${ORG}/${repo}/pulls?state=open`)).forEach(pr => prs.push({ repo, pr }));
      (await paged(`/repos/${ORG}/${repo}/branches`))
        .map(b => b.name)
        .filter(n => n !== 'main' && !/^feature\/GH-[0-9]+$/.test(n) && !n.startsWith('dependabot/'))
        .forEach(n => branchesFora.push({ repo, n }));
      try {
        const runs = await api(`/repos/${ORG}/${repo}/actions/runs?branch=main&per_page=5`);
        const ultimo = runs.workflow_runs.find(r => /^CI/.test(r.name));
        ci.push({ repo, run: ultimo });
      } catch { ci.push({ repo, run: null }); }
      acessiveis.push(repo);
    } catch (e) {
      avisos.push(`Sem acesso a \`${repo}\` (${e.status || e.message}). Configure \`PROJECT_HEALTH_TOKEN\` para incluí-lo.`);
    }
  }

  // Bloqueios reais (recurso "Blocked by") — só Issues da sprint atual ou anteriores (acionáveis)
  const sprintAtual = hoje >= '2026-11-21' ? 3 : hoje >= '2026-11-15' ? 2 : 1;
  const bloqueadas = [];
  for (const i of abertas.filter(x => x.sprint && x.sprint <= sprintAtual)) {
    try {
      const deps = await api(`/repos/${ORG}/${i.repo}/issues/${i.number}/dependencies/blocked_by`);
      const abertos = deps.filter(d => d.state === 'open');
      if (abertos.length) bloqueadas.push({ ...i, por: abertos.map(d => `${d.repository_url.split('/').pop().replace('Sema_Nexus_', '')}#${d.number}`) });
    } catch { /* recurso indisponível para esta Issue */ }
  }

  let statusMap = null;
  try { statusMap = await statusDoProject(); } catch (e) { avisos.push(`Não foi possível ler o Project V2: ${e.message}`); }

  const doMvp = abertas.filter(i => i.mvp === 'P0' || i.mvp === 'P1');
  const atrasadas = doMvp.filter(i => i.limite && i.limite < hoje);
  const semResp = abertas.filter(i => i.sprint && i.assignees.length === 0);
  const p0 = abertas.filter(i => i.mvp === 'P0');
  const semCriterios = abertas.filter(i => !i.temCriterios);

  const horasP0 = p0.reduce((s, i) => s + i.estimativa, 0);
  const diasRestantes = hoje <= FIM_IMPLEMENTACAO ? diasUteis(hoje, FIM_IMPLEMENTACAO) : 0;
  const capacidade = diasRestantes * HORAS_DIA_POR_PESSOA * PESSOAS;
  const p0Total = p0.length + fechadas.filter(i => i.mvp === 'P0').length;
  const p0Feitas = fechadas.filter(i => i.mvp === 'P0').length;

  // ---- Indicadores
  p('## Visão geral');
  p('| Indicador | Valor |');
  p('|---|---|');
  p(`| Repositórios analisados | ${acessiveis.join(', ') || 'nenhum'} |`);
  p(`| P0 concluídas | ${p0Feitas} de ${p0Total} |`);
  p(`| Horas P0 em aberto (estimadas) | ${horasP0} h |`);
  p(`| Capacidade restante até ${br(FIM_IMPLEMENTACAO)} | ${capacidade} h (${diasRestantes} dias úteis × ${PESSOAS} pessoas × ${HORAS_DIA_POR_PESSOA} h) |`);
  p(`| Issues atrasadas (P0/P1) | ${atrasadas.length} |`);
  p(`| Issues bloqueadas | ${bloqueadas.length} |`);
  p(`| PRs abertos | ${prs.length} |`);
  p('');

  // ---- Riscos
  const riscos = [];
  if (capacidade && horasP0 > capacidade) riscos.push(`🔴 **P0 em aberto (${horasP0} h) excede a capacidade restante (${capacidade} h).** Aplique os cortes previstos em docs/SPRINTS.md.`);
  else if (capacidade && horasP0 >= capacidade * 0.85) riscos.push(`🟠 P0 em aberto (${horasP0} h) ocupa ${Math.round((horasP0 / capacidade) * 100)}% da capacidade restante (${capacidade} h) — sem folga para P1.`);
  if (!capacidade && p0.length) riscos.push(`🔴 Prazo de implementação encerrado com ${p0.length} Issues P0 abertas.`);
  const p0Atrasadas = atrasadas.filter(i => i.mvp === 'P0');
  if (p0Atrasadas.length) riscos.push(`🔴 ${p0Atrasadas.length} Issue(s) **P0** com data limite vencida.`);
  const p0Bloq = bloqueadas.filter(i => i.mvp === 'P0' && i.limite && i.limite <= hoje);
  if (p0Bloq.length) riscos.push(`🟠 ${p0Bloq.length} P0 bloqueada(s) com prazo hoje ou vencido.`);
  for (const c of ci) if (c.run && c.run.conclusion === 'failure') riscos.push(`🔴 Último CI da \`main\` em ${c.repo} falhou: ${c.run.html_url}`);
  p('## ⚠️ Riscos para a entrega');
  p(riscos.length ? riscos.map(r => `- ${r}`).join('\n') + '\n' : '_Nenhum risco crítico detectado hoje._\n');

  p('## Issues atrasadas (P0/P1)');
  tabela(atrasadas, 'Nenhuma.');

  p(`## Issues da sprint atual (S${sprintAtual}) ou anteriores bloqueadas por dependências abertas`);
  if (!bloqueadas.length) p('_Nenhuma._\n');
  else {
    p('| Issue | Título | MVP | Bloqueada por |');
    p('|---|---|:-:|---|');
    bloqueadas.forEach(i => p(`| ${link(i)} | ${i.title.replace(/\|/g, '/')} | ${i.mvp} | ${i.por.join(', ')} |`));
    p('');
  }

  p('## P0 ainda abertas');
  tabela(p0.sort((a, b) => (a.limite || '9') .localeCompare(b.limite || '9')), 'Todas as P0 foram concluídas. 🎉');

  p('## Issues de sprint sem responsável');
  tabela(semResp, 'Todas possuem responsável.');

  p(`## Em andamento há mais de ${DIAS_EM_ANDAMENTO_ALERTA} dias úteis`);
  if (!statusMap) p('_Requer `PROJECT_HEALTH_TOKEN` para ler o campo Status do Project._\n');
  else {
    const lentas = abertas.filter(i => {
      const s = statusMap.get(`${i.repo}#${i.number}`);
      return s && s.status === 'Em andamento' && diasUteis(s.desde.slice(0, 10), hoje) - 1 > DIAS_EM_ANDAMENTO_ALERTA;
    });
    tabela(lentas, 'Nenhuma.');
  }

  p('## PRs aguardando revisão');
  if (!prs.length) p('_Nenhum PR aberto._\n');
  else {
    p('| PR | Título | Aberto em | Rascunho |');
    p('|---|---|:-:|:-:|');
    prs.forEach(({ repo, pr }) => p(`| [${repo.replace('Sema_Nexus_', '')}#${pr.number}](${pr.html_url}) | ${pr.title.replace(/\|/g, '/')} | ${br(pr.created_at.slice(0, 10))} | ${pr.draft ? 'sim' : 'não'} |`));
    p('');
  }

  p('## Issues sem critérios de aceitação');
  tabela(semCriterios, 'Todas possuem checklist de critérios.');

  p('## Branches fora do padrão `feature/GH-<n>`');
  p(branchesFora.length ? branchesFora.map(b => `- \`${b.repo}\`: \`${b.n}\``).join('\n') + '\n' : '_Nenhum._\n');

  p('## Último CI na `main`');
  ci.forEach(c => p(`- ${c.repo}: ${c.run ? `${c.run.conclusion || c.run.status} — ${c.run.html_url}` : 'sem execução registrada'}`));
  p('');

  if (avisos.length) {
    p('## Limitações desta execução');
    avisos.forEach(a => p(`- ${a}`));
  }
}

main().then(() => console.log(out.join('\n'))).catch(e => {
  console.log(out.join('\n'));
  console.log(`\n> ❌ Erro ao gerar o resumo: ${e.message}`);
});

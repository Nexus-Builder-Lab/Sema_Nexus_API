// SEMA NEXUS — seleção da próxima tarefa do autopilot SEM gastar tokens (Node 18+ e gh autenticado).
// Uso: node proxima.mjs --repo API|WEB [--raiz C:\SEMANEXUS]
// Saída (uma linha JSON):
//   {"acao":"retomar","issue":11,"motivo":"..."}        trabalho em andamento no diretório da sessão
//   {"acao":"merge-pendente","pr":29,"issue":5,...}     PR com label aguardando-dependencia cujas dependências fecharam
//   {"acao":"issue","issue":12,"titulo":"...",...}      próxima Issue desbloqueada
//   {"acao":"nada","motivo":"..."}                      nada disponível agora (o script dorme sem chamar o Claude)
import { execFileSync } from 'node:child_process';
import { join } from 'node:path';

const arg = n => { const i = process.argv.indexOf(n); return i > 0 ? process.argv[i + 1] : undefined; };
const REPO = (arg('--repo') || '').toUpperCase();
const RAIZ = arg('--raiz') || 'C:\\SEMANEXUS';
const NOMES = { API: 'Sema_Nexus_API', WEB: 'Sema_Nexus_Web' };
if (!NOMES[REPO]) { console.log(JSON.stringify({ acao: 'erro', motivo: 'use --repo API ou --repo WEB' })); process.exit(2); }
const FULL = `Nexus-Builder-Lab/${NOMES[REPO]}`;
const DIR = join(RAIZ, NOMES[REPO]);

// Ordem dos P1 definida pelo proprietário (após todas as P0).
const ORDEM_P1 = { API: [27, 34, 26, 25, 24, 28, 23], WEB: [16, 22, 15, 17, 14, 13, 18] };
const NUNCA = { API: [22], WEB: [] };   // Issue de tag/release: só com autorização

const sh = (cmd, args, cwd) => execFileSync(cmd, args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
const gh = args => JSON.parse(sh('gh', args) || 'null');
const out = o => { console.log(JSON.stringify(o)); process.exit(0); };

try {
  // 1) Trabalho em andamento no diretório da sessão (branch feature/GH-n ou alterações não commitadas)
  const branch = sh('git', ['branch', '--show-current'], DIR);
  const sujo = sh('git', ['status', '--porcelain'], DIR) !== '';
  const m = branch.match(/^feature\/GH-(\d+)$/);
  if (m) {
    const n = Number(m[1]);
    const st = gh(['issue', 'view', String(n), '-R', FULL, '--json', 'state,labels']);
    const falhou = st.labels.some(l => l.name === 'autopilot-falhou');
    if (st.state === 'OPEN' && !falhou) out({ acao: 'retomar', issue: n, motivo: `branch ${branch} em andamento${sujo ? ' com alterações não commitadas' : ''}` });
  } else if (sujo) {
    out({ acao: 'nada', motivo: `diretório ${DIR} com alterações não commitadas fora de um branch feature/GH-n — revisão humana` });
  }

  const bloqueios = n => gh(['api', `repos/${FULL}/issues/${n}/dependencies/blocked_by`]) || [];
  const abertos = n => bloqueios(n).filter(d => d.state === 'open').map(d => `${d.repository_url.split('/').pop()}#${d.number}`);

  // 2) PRs aguardando dependência que agora podem ser mesclados
  const prs = gh(['pr', 'list', '-R', FULL, '--label', 'aguardando-dependencia', '--state', 'open', '--json', 'number,body,title']) || [];
  const comPr = new Set();
  for (const pr of prs) {
    const iss = (pr.body || '').match(/Closes #(\d+)/i);
    if (!iss) continue;
    comPr.add(Number(iss[1]));
    if (abertos(Number(iss[1])).length === 0) out({ acao: 'merge-pendente', pr: pr.number, issue: Number(iss[1]), titulo: pr.title });
  }

  // 3) Próxima Issue: entrega-13-11 → P0 → P1 (ordem fixa); nunca P2; todas as dependências fechadas
  const issues = gh(['issue', 'list', '-R', FULL, '--state', 'open', '--limit', '200', '--json', 'number,title,labels,body']) || [];
  const tem = (i, l) => i.labels.some(x => x.name === l);
  const sprint = i => Number(((i.body || '').match(/\|\s*Sprint\s*\|\s*Sprint\s+(\d)/) || [])[1] || 9);
  const elegivel = i => !tem(i, 'autopilot-falhou') && !tem(i, 'P2') && !NUNCA[REPO].includes(i.number) && !comPr.has(i.number);
  const porSprint = (a, b) => sprint(a) - sprint(b) || a.number - b.number;
  const filas = [
    issues.filter(i => elegivel(i) && tem(i, 'entrega-13-11')).sort(porSprint),
    issues.filter(i => elegivel(i) && tem(i, 'P0') && !tem(i, 'entrega-13-11')).sort(porSprint),
    ORDEM_P1[REPO].map(n => issues.find(i => i.number === n && elegivel(i) && tem(i, 'P1'))).filter(Boolean),
  ];
  const vistos = [];
  for (const fila of filas) {
    for (const i of fila) {
      const ab = abertos(i.number);
      if (ab.length === 0) out({ acao: 'issue', issue: i.number, titulo: i.title, sprint: sprint(i) });
      vistos.push(`#${i.number} (bloqueada por ${ab.join(', ')})`);
    }
  }
  out({ acao: 'nada', motivo: vistos.length ? `todas bloqueadas: ${vistos.slice(0, 6).join('; ')}` : 'nenhuma Issue elegível' });
} catch (e) {
  out({ acao: 'erro', motivo: String(e.stderr || e.message).slice(0, 300) });
}

#!/usr/bin/env node
// Hook PreToolUse do SEMA ALERTA (Issue #149). Lê o JSON do hook na entrada padrão.
// Bloqueia (exit 2, mensagem em português no stderr): volumes Docker, leitura do .env
// e edição de migration já existente na origin/main. Qualquer outro caso: exit 0.
import { execFileSync } from 'node:child_process';
import path from 'node:path';

export function ler(texto) {
  try {
    const o = JSON.parse(texto || '{}');
    return o && typeof o === 'object' ? o : {};
  } catch { return {}; }
}

const SUFIXOS_SEGUROS = /^(example|sample|template|dist)$/i;

function bloqueioDocker(cmd) {
  const c = cmd.replace(/\s+/g, ' ');
  if (/\bdocker(?:-compose|\s+compose)\b[^;&|\n]*\bdown\b[^;&|\n]*(\s--volumes\b|\s-[a-zA-Z]*v[a-zA-Z]*\b)/i.test(c))
    return '`docker compose down -v/--volumes` apaga os volumes (banco Oracle, uploads e modelo de IA). Use `docker compose down` sem -v; o reset da demo é feito por scripts/demo-reset.';
  if (/\bdocker(?:-compose|\s+compose)\b[^;&|\n]*\brm\b[^;&|\n]*(\s--volumes\b|\s-[a-zA-Z]*v[a-zA-Z]*\b)/i.test(c)
    || /\bdocker\s+(?:container\s+)?rm\b[^;&|\n]*(\s--volumes\b|\s-[a-zA-Z]*v[a-zA-Z]*\b)/i.test(c))
    return '`docker compose rm -v` / `docker rm -v` apaga volumes anônimos dos contêineres. Peça autorização explícita ao proprietário.';
  if (/\bdocker\s+volume\s+(rm|remove|prune)\b/i.test(c))
    return '`docker volume rm/prune` apaga volumes de dados do projeto. Peça autorização explícita ao proprietário.';
  if (/\bdocker\s+system\s+prune\b/i.test(c))
    return '`docker system prune` pode apagar volumes e imagens do ambiente. Peça autorização explícita ao proprietário.';
  return null;
}

const MSG_ENV = 'O arquivo .env contém segredos (senhas e chaves) e não pode ser lido nem impresso. Use `.env.example` como referência ou peça ao proprietário o nome da variável.';
const LEITORES = /\b(cat|type|get-content|gc|more|less|head|tail|bat|sed|awk|grep|egrep|rg|select-string|sls|cp|copy|copy-item|cpi|mv|move|base64|certutil|source|xxd|od|strings|import-csv|printenv|readalltext|readalllines|readallbytes|readfilesync|open|node|python|python3|php|ruby|perl)\b|(^|[\s;&|(])\.\s+\S*\.env|\$\(\s*<|<\s*\S*\.env/i;

function globParaRegex(g) {
  return new RegExp('^' + g.replace(/[.+^${}()|\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.') + '$', 'i');
}

// alvo pode ser caminho, nome ou glob. true se aponta para .env/.env.* sensível.
function ehEnvSensivel(alvo) {
  const base = path.basename(String(alvo).replace(/["'`]/g, '').trim().replace(/\\/g, '/'));
  if (/[*?]/.test(base)) {
    if (base.replace(/[*?]/g, '').length < 2) return false;
    const re = globParaRegex(base);
    return ['.env', '.env.local', '.env.production', '.env.prod', '.env.dev'].some((n) => re.test(n));
  }
  const m = /^\.env(?:\.([\w.-]+))?$/i.exec(base);
  if (!m) return false;
  if (!m[1]) return true;
  return !m[1].split('.').some((p) => SUFIXOS_SEGUROS.test(p));
}

function bloqueioEnvComando(cmd) {
  if (!LEITORES.test(cmd)) return null;
  const limpo = cmd.replace(/["'`]/g, '');
  for (const t of limpo.split(/[\s;|&()<>,=]+/)) {
    if (t && /e|\*|\?/i.test(t) && ehEnvSensivel(t)) return MSG_ENV;
  }
  return null;
}

const NOME_MIGRATION = /V\d[^\s'"\/\\]*__[^\s'"\/\\]*\.sql/gi;
const DIR_MIGRATION = 'src/main/resources/db/migration/';

function migrationsDaMain(cwd) {
  for (const ref of ['origin/main', 'main']) {
    try {
      const saida = execFileSync('git', ['ls-tree', '--name-only', ref, DIR_MIGRATION], { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
      const nomes = saida.split(/\r?\n/).filter(Boolean).map((f) => path.posix.basename(f).toLowerCase());
      if (nomes.length) return new Set(nomes);
    } catch { /* tenta a próxima; sem git = libera */ }
  }
  return new Set();
}

function migrationExistente(texto, cwd) {
  const nomes = [...String(texto).replace(/\\/g, '/').matchAll(NOME_MIGRATION)].map((m) => m[0].toLowerCase());
  if (!nomes.length) return null;
  const main = migrationsDaMain(cwd);
  return nomes.find((n) => main.has(n)) || null;
}

function msgMigration(nome) {
  return `A migration ${nome} já existe na origin/main e não pode ser editada (o Flyway acusaria checksum divergente; o NTFS ignora maiúsculas/minúsculas). Crie uma nova migration com o próximo número livre.`;
}

const VERBOS_ESCRITA = /(\bsed\s+-[a-z]*i|\bperl\s+-[a-z]*i|\bset-content\b|\bsc\b|\badd-content\b|\bac\b|\bout-file\b|\bclear-content\b|\bremove-item\b|\bri\b|\brm\b|\bdel\b|\berase\b|\bmv\b|\bmove(-item)?\b|\bmi\b|\bren\b|\brename(-item)?\b|\bcp\b|\bcopy(-item)?\b|\bcpi\b|\bni\b|\bnew-item\b|\btruncate\b|\btee\b|>|\bgit\s+(checkout|restore|reset|rm)\b|writealltext|writealllines|writeallbytes|appendalltext|\bwritefilesync\b|\bappendfilesync\b)/i;

export function avaliar(entrada, cwdPadrao = process.cwd()) {
  if (!entrada || typeof entrada !== 'object') return null;
  const ferramenta = entrada.tool_name || '';
  const ti = entrada.tool_input && typeof entrada.tool_input === 'object' ? entrada.tool_input : {};
  const cwd = entrada.cwd || cwdPadrao;
  if (ferramenta === 'Bash' || ferramenta === 'PowerShell') {
    const cmd = String(ti.command || '');
    const d = bloqueioDocker(cmd) || bloqueioEnvComando(cmd);
    if (d) return d;
    if (VERBOS_ESCRITA.test(cmd)) {
      const n = migrationExistente(cmd, cwd);
      if (n) return msgMigration(n);
    }
    return null;
  }
  if (ferramenta === 'Read' || ferramenta === 'Grep') {
    for (const alvo of [ti.file_path, ti.path, ferramenta === 'Grep' ? ti.glob : null]) {
      if (alvo && ehEnvSensivel(String(alvo))) return MSG_ENV;
    }
    return null;
  }
  if (['Edit', 'Write', 'MultiEdit', 'NotebookEdit'].includes(ferramenta)) {
    const alvo = String(ti.file_path || ti.notebook_path || '').trim();
    const n = migrationExistente(alvo, cwd);
    if (n) return msgMigration(n);
  }
  return null;
}

if (process.argv[1] && path.basename(process.argv[1]) === 'proteger.mjs') {
  let dados = '';
  process.stdin.setEncoding('utf8');
  process.stdin.on('data', (c) => (dados += c));
  process.stdin.on('end', () => {
    let motivo = null;
    try { motivo = avaliar(ler(dados)); } catch { motivo = null; }
    if (motivo) {
      process.stderr.write(`BLOQUEADO pelo hook de proteção do SEMA ALERTA: ${motivo}\n`);
      process.exit(2);
    }
    process.exit(0);
  });
}

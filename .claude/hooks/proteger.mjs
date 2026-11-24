#!/usr/bin/env node
// Hook PreToolUse do SEMA ALERTA (Issue #149). Lê o JSON do hook na entrada padrão.
// Bloqueia (exit 2, mensagem em português no stderr): volumes Docker, leitura do .env
// e edição de migration já existente na origin/main. Qualquer outro caso: exit 0.
import { execFileSync } from 'node:child_process';
import path from 'node:path';

export function ler(texto) {
  try { return JSON.parse(texto || '{}'); } catch { return {}; }
}

const SUFIXOS_SEGUROS = /^(example|sample|template|dist)$/i;

function bloqueioDocker(cmd) {
  const c = cmd.replace(/\s+/g, ' ');
  if (/\bdocker(?:-compose|\s+compose)\b[^;&|\n]*\bdown\b[^;&|\n]*(\s--volumes\b|\s-[a-zA-Z]*v[a-zA-Z]*\b)/i.test(c))
    return '`docker compose down -v/--volumes` apaga os volumes (banco Oracle, uploads e modelo de IA). Use `docker compose down` sem -v; o reset da demo é feito por scripts/demo-reset.';
  if (/\bdocker\s+volume\s+(rm|remove|prune)\b/i.test(c))
    return '`docker volume rm/prune` apaga volumes de dados do projeto. Peça autorização explícita ao proprietário.';
  if (/\bdocker\s+system\s+prune\b/i.test(c))
    return '`docker system prune` pode apagar volumes e imagens do ambiente. Peça autorização explícita ao proprietário.';
  return null;
}

const MSG_ENV = 'O arquivo .env contém segredos (senhas e chaves) e não pode ser lido nem impresso. Use `.env.example` como referência ou peça ao proprietário o nome da variável.';
const LEITORES = /\b(cat|type|get-content|gc|more|less|head|tail|bat|sed|awk|grep|egrep|rg|select-string|sls|cp|copy|copy-item|cpi|mv|move|base64|certutil|source|xxd|od|strings|import-csv|printenv)\b|(^|[\s;&|(])\.\s+\S*\.env|\$\(\s*<|<\s*\S*\.env/i;
const ALVO_ENV = /(^|[\s\/\\'"=(])\.env(\.[\w.-]+)?(?=$|[\s'"|;&)<>])/gi;

function ehEnvSensivel(alvo) {
  const m = /^\.env(?:\.([\w.-]+))?$/i.exec(path.basename(alvo.replace(/\\/g, '/')));
  if (!m) return false;
  if (!m[1]) return true;
  return !m[1].split('.').some((p) => SUFIXOS_SEGUROS.test(p));
}

function bloqueioEnvComando(cmd) {
  if (!LEITORES.test(cmd)) return null;
  for (const m of cmd.matchAll(ALVO_ENV)) {
    const alvo = m[0].replace(/^[\s\/\\'"=(]+(?=\.env)/, '').trim();
    if (ehEnvSensivel(alvo)) return MSG_ENV;
  }
  return null;
}

const MIGRATION = /(?:^|\/)src\/main\/resources\/db\/migration\/(V[^\/\s'"]*__[^\/\s'"]*\.sql)/i;

function existeNaMain(cwd, relativo) {
  for (const ref of ['origin/main', 'main']) {
    try {
      execFileSync('git', ['cat-file', '-e', `${ref}:${relativo}`], { cwd, stdio: 'ignore' });
      return true;
    } catch (e) {
      if (e.code === 'ENOENT') return false;
    }
  }
  return false;
}

function relativoMigration(arquivo) {
  const m = MIGRATION.exec(arquivo.replace(/\\/g, '/'));
  return m ? `src/main/resources/db/migration/${m[1]}` : null;
}

function msgMigration(rel) {
  return `A migration ${rel} já existe na origin/main e não pode ser editada (o Flyway acusaria checksum divergente). Crie uma nova migration com o próximo número livre.`;
}

export function avaliar(entrada, cwdPadrao = process.cwd()) {
  const ferramenta = entrada.tool_name || '';
  const ti = entrada.tool_input || {};
  const cwd = entrada.cwd || cwdPadrao;
  if (ferramenta === 'Bash' || ferramenta === 'PowerShell') {
    const cmd = String(ti.command || '');
    const d = bloqueioDocker(cmd) || bloqueioEnvComando(cmd);
    if (d) return d;
    if (/(\bsed\s+-i|\bperl\s+-p?i|set-content|add-content|out-file|clear-content|remove-item|\brm\b|\bdel\b|\bmv\b|\bmove\b|\btruncate\b|>|\btee\b|\bcp\b|\bcopy\b)/i.test(cmd)) {
      for (const m of cmd.matchAll(/[^\s'"]*db[\/\\]migration[\/\\]V[^\s'"]*__[^\s'"]*\.sql/gi)) {
        const rel = relativoMigration(m[0]);
        if (rel && existeNaMain(cwd, rel)) return msgMigration(rel);
      }
    }
    return null;
  }
  if (ferramenta === 'Read' || ferramenta === 'Grep') {
    const alvo = String(ti.file_path || ti.path || '');
    if (alvo && ehEnvSensivel(alvo)) return MSG_ENV;
    if (ferramenta === 'Grep' && ti.glob && ehEnvSensivel(String(ti.glob))) return MSG_ENV;
    return null;
  }
  if (['Edit', 'Write', 'MultiEdit', 'NotebookEdit'].includes(ferramenta)) {
    const alvo = String(ti.file_path || ti.notebook_path || '');
    const rel = relativoMigration(alvo);
    if (rel && existeNaMain(cwd, rel)) return msgMigration(rel);
  }
  return null;
}

if (process.argv[1] && path.basename(process.argv[1]) === 'proteger.mjs') {
  let dados = '';
  process.stdin.setEncoding('utf8');
  process.stdin.on('data', (c) => (dados += c));
  process.stdin.on('end', () => {
    const motivo = avaliar(ler(dados));
    if (motivo) {
      process.stderr.write(`BLOQUEADO pelo hook de proteção do SEMA ALERTA: ${motivo}\n`);
      process.exit(2);
    }
    process.exit(0);
  });
}

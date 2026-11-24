#!/usr/bin/env node
// Prova dos hooks: executa proteger.mjs como o Claude Code (JSON na entrada padrão) e confere o exit code.
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = path.dirname(fileURLToPath(import.meta.url));
const raiz = path.resolve(aqui, '..', '..');
const hook = path.join(aqui, 'proteger.mjs');
const MIG = 'src/main/resources/db/migration/';
const existente = spawnSync('git', ['ls-tree', '--name-only', 'origin/main', MIG], { cwd: raiz, encoding: 'utf8' })
  .stdout.split('\n').find((f) => /V\d+__.*\.sql$/.test(f)) || `${MIG}V1__base.sql`;

const B = (command) => ({ tool_name: 'Bash', tool_input: { command } });
const P = (command) => ({ tool_name: 'PowerShell', tool_input: { command } });
const casos = [
  ['volumes: down -v (Bash)', B('docker compose down -v'), 2],
  ['volumes: down --volumes (PowerShell)', P('docker compose down --volumes'), 2],
  ['volumes: docker-compose down -fv', B('docker-compose down -fv'), 2],
  ['volumes: volume rm', B('docker volume rm sema-nexus_oradata'), 2],
  ['volumes: volume prune', P('docker volume prune -f'), 2],
  ['volumes: system prune', B('docker system prune -a'), 2],
  ['volumes: down sem -v permitido', B('docker compose down'), 0],
  ['volumes: compose up permitido', P('docker compose up -d --no-build api'), 0],
  ['.env: cat (Bash)', B('cat .env'), 2],
  ['.env: Get-Content (PowerShell)', P('Get-Content .\\.env'), 2],
  ['.env: type com caminho', P('type C:\\SEMANEXUS\\Sema_Nexus_API\\.env'), 2],
  ['.env: ferramenta Read', { tool_name: 'Read', tool_input: { file_path: 'C:\\x\\Sema_Nexus_API\\.env' } }, 2],
  ['.env: .env.local', B('cat .env.local'), 2],
  ['.env.example: cat permitido', B('cat .env.example'), 0],
  ['.env.example: Read permitido', { tool_name: 'Read', tool_input: { file_path: 'C:\\x\\.env.example' } }, 0],
  ['.env.example: Get-Content permitido', P('Get-Content .env.example'), 0],
  ['migration: Edit em existente', { tool_name: 'Edit', cwd: raiz, tool_input: { file_path: path.join(raiz, existente) } }, 2],
  ['migration: Write em existente', { tool_name: 'Write', cwd: raiz, tool_input: { file_path: path.join(raiz, existente) } }, 2],
  ['migration: sed -i em existente', { ...B(`sed -i "s/a/b/" ${existente}`), cwd: raiz }, 2],
  ['migration: Write em nova permitido', { tool_name: 'Write', cwd: raiz, tool_input: { file_path: path.join(raiz, MIG, 'V9999__nova_migration.sql') } }, 0],
  ['migration: Read em existente permitido', { tool_name: 'Read', cwd: raiz, tool_input: { file_path: path.join(raiz, existente) } }, 0],
  ['migration: caixa diferente (Edit)', { tool_name: 'Edit', cwd: raiz, tool_input: { file_path: path.join(raiz, MIG, existente.split('/').pop().toLowerCase()) } }, 2],
  ['migration: nome solto apos cd (PowerShell)', { ...P('cd src/main/resources/db/migration; Set-Content ' + existente.split('/').pop().toLowerCase() + ' x'), cwd: raiz }, 2],
  ['migration: alias sc', { ...P('sc ' + existente + ' x'), cwd: raiz }, 2],
  ['migration: git checkout --', { ...B('git checkout -- ' + existente), cwd: raiz }, 2],
  ['migration: WriteAllText', { ...P('[IO.File]::WriteAllText("' + existente + '","x")'), cwd: raiz }, 2],
  ['migration: Move-Item', { ...P('Move-Item x.sql ' + existente), cwd: raiz }, 2],
  ['.env: glob *.env', B('cat *.env'), 2],
  ['.env: glob .en*', B('cat .en*'), 2],
  ['.env: com aspas', P('Get-Content ".env"'), 2],
  ['.env: ReadAllText', P('[IO.File]::ReadAllText(".env")'), 2],
  ['.env: Grep com glob', { tool_name: 'Grep', tool_input: { pattern: 'KEY', glob: '**/.env' } }, 2],
  ['.env: Read com espaco final', { tool_name: 'Read', tool_input: { file_path: 'C:/x/.env ' } }, 2],
  ['.env: Grep sem path permitido', { tool_name: 'Grep', tool_input: { pattern: 'KEY' } }, 0],
  ['volumes: compose rm -v', B('docker compose rm -v api'), 2],
  ['volumes: compose rm -sv', P('docker compose rm -sv'), 2],
  ['volumes: compose rm -f permitido', B('docker compose rm -f api'), 0],
  ['entrada: JSON null nao quebra', null, 0],
  ['outro: Edit em arquivo comum permitido', { tool_name: 'Edit', cwd: raiz, tool_input: { file_path: path.join(raiz, 'README.md') } }, 0],
];

let falhas = 0;
for (const [nome, entrada, esperado] of casos) {
  const r = spawnSync(process.execPath, [hook], { input: entrada === null ? 'null' : JSON.stringify(entrada), encoding: 'utf8' });
  const ok = r.status === esperado && (esperado === 0 || /BLOQUEADO/.test(r.stderr));
  if (!ok) falhas++;
  console.log(`${ok ? 'OK    ' : 'FALHOU'} ${nome} (exit ${r.status}, esperado ${esperado})`);
}
console.log(`\n${casos.length - falhas}/${casos.length} casos`);
process.exit(falhas ? 1 : 0);

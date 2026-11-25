import { z } from 'zod';

const MAX_CHARS = 20000;
const data = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'use AAAA-MM-DD');
const ENUM = z.string().regex(/^[A-Z_]{2,40}$/);

export function limitar(valor) {
  let t = JSON.stringify(valor, null, 1) ?? 'null';
  if (t.length > MAX_CHARS) t = t.slice(0, MAX_CHARS) + '\n... [saida truncada]';
  return t;
}

export const ferramentas = [
  {
    nome: 'listar_demandas',
    descricao: 'Lista demandas (somente leitura, paginado) com filtros opcionais.',
    schema: {
      q: z.string().max(100).optional(), status: ENUM.optional(), municipioId: z.number().int().positive().optional(),
      categoria: ENUM.optional(), prioridade: ENUM.optional(),
      equipeId: z.number().int().positive().optional(), atrasada: z.boolean().optional(), de: data.optional(), ate: data.optional(),
      pagina: z.number().int().min(0).max(1000).optional(), tamanho: z.number().int().min(1).max(50).optional(),
    },
    chamar: (c, a) => c.get('/demandas', {
      q: a.q, status: a.status, municipioId: a.municipioId, categoria: a.categoria, prioridade: a.prioridade,
      equipeId: a.equipeId, atrasada: a.atrasada, de: a.de, ate: a.ate, page: a.pagina ?? 0, size: a.tamanho ?? 20,
    }),
  },
  {
    nome: 'obter_demanda', descricao: 'Detalhes de uma demanda pelo id (somente leitura).',
    schema: { id: z.number().int().positive() },
    chamar: (c, a) => c.get(`/demandas/${a.id}`),
  },
  {
    nome: 'prazos_criticos', descricao: 'Demandas com prazo vencido ou proximo do vencimento (somente leitura).',
    schema: { estado: ENUM.optional(), diasAlerta: z.number().int().min(1).max(90).optional() },
    chamar: (c, a) => c.get('/flux/prazos', { estado: a.estado, diasAlerta: a.diasAlerta }),
  },
  {
    nome: 'resumo_indicadores', descricao: 'Indicadores do painel (dashboard) conforme o perfil do usuario.',
    schema: {}, chamar: (c) => c.get('/dashboard'),
  },
  {
    nome: 'buscar_documentos', descricao: 'Busca textual em documentos (somente leitura).',
    schema: { q: z.string().min(2).max(200), limite: z.number().int().min(1).max(20).optional() },
    chamar: (c, a) => c.get('/documentos/busca', { q: a.q, limite: a.limite ?? 10 }),
  },
  {
    nome: 'alertas_inpe_municipio',
    descricao: 'Pontos de referencia do INPE (focos/alertas) agregados por municipio, com periodo opcional.',
    schema: { municipioId: z.number().int().positive(), de: data.optional(), ate: data.optional(), tipo: ENUM.optional() },
    chamar: async (c, a) => {
      const r = await c.get('/intelligence/referencia/por-municipio', { tipo: a.tipo, de: a.de, ate: a.ate });
      const lista = Array.isArray(r) ? r : (r?.itens ?? r?.municipios ?? null);
      if (!Array.isArray(lista)) return r;
      return lista.filter((m) => Number(m.municipioId ?? m.codigoIbge ?? m.municipio?.id) === a.municipioId);
    },
  },
];

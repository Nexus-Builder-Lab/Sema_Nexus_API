-- GH-164: busca lexical sem ORA-64203 em textos longos e acentuados.
-- AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS. Este arquivo só cria estrutura; o conteúdo é gerado pela API.
-- TEXTO_BUSCA guarda o TEXTO_EXTRAIDO já normalizado pela aplicação (minúsculas, sem acentos — java.text.Normalizer),
-- e a busca faz "TEXTO_BUSCA LIKE ? ESCAPE '\'" direto no CLOB, sem TRANSLATE/LOWER (que convertem o CLOB para
-- VARCHAR2 e estouram acima de 4000 bytes). Coluna derivada: preenchida no upload e, para linhas antigas ou gravadas
-- por fora, pelo preenchimento idempotente na subida da API (IndiceBuscaDocumentos).
ALTER TABLE DOCUMENTO ADD (TEXTO_BUSCA CLOB);

COMMENT ON COLUMN DOCUMENTO.TEXTO_BUSCA IS 'Texto extraído normalizado (minúsculas, sem acentos) para a busca lexical; derivado, mantido pela API.';

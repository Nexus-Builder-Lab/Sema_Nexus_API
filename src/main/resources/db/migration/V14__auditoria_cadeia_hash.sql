-- GH-148: trilha de auditoria encadeada (SHA-256) e valores antes/depois nas edições.
--
-- Cada registro novo recebe, logo após o commit, uma posição na cadeia (SEQ_CADEIA), o hash do registro anterior
-- (HASH_ANTERIOR) e o próprio hash (HASH_REGISTRO). A selagem é serializada pela linha única de AUDITORIA_CADEIA
-- (SELECT ... FOR UPDATE), então inserções simultâneas nunca bifurcam a cadeia.
-- Registros anteriores a esta migration (ID <= INICIO_ID) ficam fora da cadeia: são reportados como "legados".
ALTER TABLE AUDITORIA ADD (
    VALOR_ANTERIOR  VARCHAR2(4000),
    VALOR_NOVO      VARCHAR2(4000),
    SEQ_CADEIA      NUMBER(19),
    HASH_ANTERIOR   VARCHAR2(64),
    HASH_REGISTRO   VARCHAR2(64)
);

CREATE UNIQUE INDEX UX_AUDITORIA_SEQ_CADEIA ON AUDITORIA (SEQ_CADEIA);
-- Só os registros ainda não selados entram neste índice (as chaves nulas não são indexadas).
CREATE INDEX IX_AUDITORIA_PENDENTE ON AUDITORIA (CASE WHEN HASH_REGISTRO IS NULL THEN ID END);

CREATE TABLE AUDITORIA_CADEIA (
    ID             NUMBER(1)     NOT NULL,
    INICIO_ID      NUMBER(19)    NOT NULL,
    ULTIMA_SEQ     NUMBER(19)    DEFAULT 0 NOT NULL,
    ULTIMO_HASH    VARCHAR2(64),
    ATUALIZADA_EM  TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_AUDITORIA_CADEIA PRIMARY KEY (ID),
    CONSTRAINT CK_AUDITORIA_CADEIA_UNICA CHECK (ID = 1)
);

INSERT INTO AUDITORIA_CADEIA (ID, INICIO_ID, ULTIMA_SEQ, ULTIMO_HASH)
SELECT 1, NVL(MAX(ID), 0), 0, NULL FROM AUDITORIA;

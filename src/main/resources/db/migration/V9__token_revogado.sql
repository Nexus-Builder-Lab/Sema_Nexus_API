-- GH-134: revogação de JWT no logout. Cada linha é um token (jti) invalidado antes do fim da validade.
-- Sem FK para USUARIO de propósito: o registro só vale até EXPIRA_EM e é removido pela limpeza diária.
CREATE TABLE TOKEN_REVOGADO (
    JTI          VARCHAR2(64 CHAR)  NOT NULL,
    USUARIO_ID   NUMBER(19)         NOT NULL,
    EXPIRA_EM    TIMESTAMP          NOT NULL,
    REVOGADO_EM  TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_TOKEN_REVOGADO PRIMARY KEY (JTI)
);

CREATE INDEX IX_TOKEN_REVOGADO_EXPIRA ON TOKEN_REVOGADO (EXPIRA_EM);

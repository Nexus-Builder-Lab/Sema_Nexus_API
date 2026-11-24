-- Área do alerta DETER = recorte por município (areamunkm), o mesmo polígono desenhado no mapa.
-- No Cerrado, areatotalkm é a área do alerta inteiro (repetida em cada recorte), e a V7 + carga inicial
-- chegaram a gravá-la em AREA_KM2. Corrige bancos que já carregaram o snapshot; em banco novo não há linhas.
UPDATE REFERENCIA_SATELITE
   SET AREA_KM2 = ROUND(JSON_VALUE(ATRIBUTOS, '$.areamunkm' RETURNING NUMBER), 4)
 WHERE TIPO = 'DESMATAMENTO'
   AND JSON_VALUE(ATRIBUTOS, '$.areamunkm' RETURNING NUMBER) IS NOT NULL;

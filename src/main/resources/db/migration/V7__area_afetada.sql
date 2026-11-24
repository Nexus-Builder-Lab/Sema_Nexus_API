-- Área afetada no mapa de Inteligência ambiental.
-- 1) REFERENCIA_SATELITE.GEOMETRIA: polígono GeoJSON do alerta DETER como a fonte (INPE/TerraBrasilis) o fornece.
--    Focos de calor ficam sem geometria: o INPE entrega o foco como PONTO, sem área queimada.
--    AREA_KM2 continua sendo o valor da fonte (km²); a conversão para hectares é feita na API (1 km² = 100 ha).
-- 2) DEMANDA.AREA_HA / AREA_GEOMETRIA: área opcional da demanda. Na demonstração só a massa do seed
--    ([DEMO-SEED]) recebe um polígono FICTÍCIO. AMBIENTE DEMONSTRATIVO — DADOS FICTÍCIOS.
ALTER TABLE REFERENCIA_SATELITE ADD (GEOMETRIA CLOB);

ALTER TABLE DEMANDA ADD (
    AREA_HA        NUMBER(12,2),
    AREA_GEOMETRIA CLOB,
    CONSTRAINT CK_DEMANDA_AREA_HA CHECK (AREA_HA IS NULL OR AREA_HA > 0)
);

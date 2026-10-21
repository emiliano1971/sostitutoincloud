-- ============================================
-- Migration 034: marca da bollo addebitata al proprietario come voce dello split
-- ============================================
--
-- La regola 'provvigione_proprietario' (rimanenza) può addebitare il bollo della ricevuta
-- al proprietario: se il netto proprietario supera tenant_settings.bollo_soglia, lo split
-- riceve una voce 'bollo' (bollo_importo) in fattura PM, fuori campo IVA art. 15
-- DPR 633/72 (Natura N1 nell'XML SDI), che riduce il netto proprietario.
--
-- Esecuzione:
--     psql -h localhost -U sostitutoincloud -d sostitutoincloud \
--          -f docs/db/migrations/034_bollo_split.sql

ALTER TABLE property_contract_rule
    ADD COLUMN IF NOT EXISTS addebita_bollo BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN property_contract_rule.addebita_bollo IS
    'Se true e tipo=provvigione_proprietario, aggiunge la voce bollo allo split economico '
    '(fuori campo art. 15) sottraendola al netto proprietario. '
    'Si applica solo se il netto proprietario supera tenant_settings.bollo_soglia.';

ALTER TABLE booking_split_economico
    ADD COLUMN IF NOT EXISTS fuori_campo BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN booking_split_economico.fuori_campo IS
    'Se true la voce è fuori campo IVA art. 15 DPR 633/72 (es. bollo): in fattura PM '
    'senza IVA, esclusa dall imponibile, Natura N1 nell XML SDI.';

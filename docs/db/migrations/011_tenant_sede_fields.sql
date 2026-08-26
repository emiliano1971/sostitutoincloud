-- ============================================================================
-- 011 — Sede legale del tenant scomposta (CAP / comune / provincia)
-- ============================================================================
-- Serve alla generazione dell'XML SDI: <Sede> del CedentePrestatore richiede
-- Indirizzo, CAP, Comune, Provincia e Nazione come elementi separati, mentre
-- finora esisteva solo legal_address come stringa unica (che SdiXmlService era
-- costretto a scomporre con un parsing fragile).
-- legal_address resta e continua a contenere l'indirizzo (via e civico).
-- ============================================================================

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS cap       VARCHAR(10),
    ADD COLUMN IF NOT EXISTS comune    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provincia CHAR(2);

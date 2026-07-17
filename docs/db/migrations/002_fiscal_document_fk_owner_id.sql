-- Migration 002: aggiunge fiscal_document.fk_owner_id.
-- Il proprietario e' denormalizzato direttamente sul documento (popolato alla
-- generazione risalendo booking -> property -> owner) per semplificare tutte le
-- query per owner senza dover ripercorrere la catena.

ALTER TABLE fiscal_document
    ADD COLUMN IF NOT EXISTS fk_owner_id INTEGER
    REFERENCES owner_profile(id) ON DELETE RESTRICT;

-- Popola i dati esistenti risalendo la catena booking -> property -> owner.
UPDATE fiscal_document fd
SET fk_owner_id = p.fk_owner_id
FROM booking b
JOIN property p ON p.id = b.fk_property_id
WHERE fd.fk_booking_id = b.id
  AND fd.fk_owner_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_fiscal_doc_fk_owner_id
    ON fiscal_document(fk_tenant_id, fk_owner_id);

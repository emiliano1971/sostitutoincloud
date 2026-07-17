-- 004_booking_fk_owner_id.sql
-- Aggiunge fk_owner_id direttamente su booking: proprietario denormalizzato
-- dalla catena booking→property per query dirette e per la lista.
-- Stesso pattern già applicato su fiscal_document (002_fiscal_document_fk_owner_id.sql).

ALTER TABLE booking
ADD COLUMN IF NOT EXISTS fk_owner_id INTEGER
REFERENCES owner_profile(id) ON DELETE RESTRICT;

-- Popola i booking esistenti risalendo la property
UPDATE booking b
SET fk_owner_id = p.fk_owner_id
FROM property p
WHERE b.fk_property_id = p.id
AND b.fk_owner_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_booking_fk_owner_id
ON booking(fk_tenant_id, fk_owner_id);

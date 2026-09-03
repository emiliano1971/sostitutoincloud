-- 014_booking_uq_external_tenant.sql
-- Il vincolo di univocità dell'id prenotazione esterno era (fk_canale_ota_id, external_booking_id):
-- mancava il tenant, quindi due tenant diversi non potevano avere lo stesso id sullo stesso canale.
-- NB: fk_canale_ota_id è nullable e in PostgreSQL i NULL non collidono mai in un UNIQUE,
-- quindi le prenotazioni senza canale restano fuori dal vincolo (comportamento preesistente).
ALTER TABLE booking
    DROP CONSTRAINT IF EXISTS uq_external_booking;

ALTER TABLE booking
    ADD CONSTRAINT uq_external_booking
        UNIQUE (fk_tenant_id, fk_canale_ota_id, external_booking_id);

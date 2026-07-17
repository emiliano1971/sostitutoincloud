Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Aggiungi fk_owner_id direttamente su booking,
popolato alla creazione/import e denormalizzato
nella lista. Stesso pattern già applicato su
fiscal_document in SETUP-FISCAL-DOCUMENT-FK-OWNER.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/db/migrations/
004_booking_fk_owner_id.sql:

ALTER TABLE booking
ADD COLUMN IF NOT EXISTS fk_owner_id INTEGER
REFERENCES owner_profile(id) ON DELETE RESTRICT;

UPDATE booking b
SET fk_owner_id = p.fk_owner_id
FROM property p
WHERE b.fk_property_id = p.id
AND b.fk_owner_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_booking_fk_owner_id
ON booking(fk_tenant_id, fk_owner_id);

Esegui sul DB:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost \
-f docs/db/migrations/004_booking_fk_owner_id.sql

Verifica:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
SELECT b.id, b.external_booking_id,
b.fk_owner_id,
o.first_name, o.last_name
FROM booking b
LEFT JOIN owner_profile o ON o.id = b.fk_owner_id
ORDER BY b.id
LIMIT 10;"

Tutti i booking devono avere fk_owner_id != null.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SCHEMA SQL
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiorna docs/db/schema-target.sql:
- Aggiungi dopo fk_property_id:
  fk_owner_id INTEGER REFERENCES
  owner_profile(id) ON DELETE RESTRICT
- Aggiungi indice idx_booking_fk_owner_id

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. MODEL + MAPPER + DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica model/Booking.java:
- aggiungi Integer fkOwnerId

Modifica dao/mapper/BookingRowMapper.java:
- aggiungi:
  rs.getObject("fk_owner_id", Integer.class)

Modifica dao/BookingDAO.java:
- aggiungi fk_owner_id in SELECT_ALL
- aggiungi fk_owner_id in INSERT
- aggiungi parametro fkOwnerId nell'insert
- aggiungi metodo:
  List<Booking> findByOwnerAndTenant(
  Integer tenantId, Integer ownerId)
    - SELECT * FROM booking
      WHERE fk_tenant_id = ?
      AND fk_owner_id = ?
      ORDER BY checkout_date DESC
    - Log DEBUG

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BookingService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/BookingService.java:

- ownerName nel BookingListDTO ora viene
  risolto direttamente da fkOwnerId del
  booking invece della catena
  booking→property→owner:
  ownersById.get(booking.getFkOwnerId())
  (stesso pattern di FiscalDocumentService
  dopo SETUP-FISCAL-DOCUMENT-FK-OWNER)

- Aggiungi fkOwnerId a BookingListDTO e
  BookingDetailDTO se non già presente

- Verifica che PropertyDAO sia ancora
  necessario per altri campi (propertyName
  ecc.) — se sì mantienilo, altrimenti
  rimuovilo dal costruttore

- Al momento della creazione/import di un
  nuovo booking popola fkOwnerId da
  property.getFkOwnerId() (già caricata
  nel service per altri campi)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. FRONTEND
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/api/bookingApi.ts:
- aggiungi fkOwnerId e ownerName a
  BookingListItem se non già presenti

Modifica frontend/src/pages/tenant/
BookingsList.tsx:
- aggiungi colonna "Proprietario" nella
  tabella dopo "Immobile"
- se ownerName è null mostra "—"
- ownerName cliccabile: naviga a
  /documents?ownerId={fkOwnerId}
  (stessa logica già usata in DocumentsList
  per il link verso F24)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/api/bookings" \
| python3 -m json.tool \
| grep -E '"id"|"ownerName"|"fkOwnerId"' \
| head -20

Verifica che:
- tutti i booking abbiano fkOwnerId != null
- ownerName sia coerente con la property
- nessun booking abbia ownerName null

Riporta output migration, build e curl.
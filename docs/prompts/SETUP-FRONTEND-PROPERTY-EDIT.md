Leggi il file CLAUDE.md e i file esistenti:
- frontend/src/pages/tenant/PropertyCreate.tsx
- frontend/src/pages/tenant/PropertyDetail.tsx
- frontend/src/api/propertyApi.ts
  prima di procedere.

Aggiungi la funzionalità di modifica immobile,
in particolare i codici OTA che possono essere
corretti dopo la creazione.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. BACKEND — verifica updateProperty
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che esista già
PUT /api/properties/{id} in
PropertyController (o PropertyWriteController).
Se esiste riporta la firma esatta.
Se non esiste crealo:

PUT /api/properties/{id}
- @RequestBody PropertyCreateRequest
  (stesso DTO della creazione)
- tenantId da SecurityUtils
- chiama propertyWriteService.update(
  tenantId, id, dto)
- ResponseEntity.ok(result)
- catch NoSuchElementException → 404
- catch IllegalArgumentException → 400

Aggiungi a service/PropertyWriteService.java
se non esiste:

PropertyDetailDTO update(Integer tenantId,
Integer propertyId,
PropertyCreateRequest dto)
- Verifica esistenza + appartenenza tenant
- UPDATE property SET display_name=?,
  internal_code=?, address=?, city=?,
  region=?, cin_code=?,
  fk_tipo_immobile_id=?,
  updated_at=NOW()
  WHERE id=? AND fk_tenant_id=?
- Per i codici OTA:
    * deleteByPropertyId(propertyId)
    * reinserisci quelli non vuoti
      (stessa logica del create)
- Rileggi e restituisci PropertyDetailDTO
- Log INFO "PropertyWriteService.update()
    - id={}"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FRONTEND — propertyApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a frontend/src/api/propertyApi.ts
se non già presente:

export async function updateProperty(
id: number,
data: PropertyCreateRequest
): Promise<PropertyDetail>
// PUT /api/properties/{id}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. FRONTEND — PropertyEdit.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/pages/tenant/PropertyEdit.tsx
copiando PropertyCreate.tsx come base e
adattandolo per la modifica:

- Legge l'id dalla URL: useParams()
- Al mount: carica l'immobile con
  getPropertyById(id) e precompila
  tutti i campi del form con i valori
  attuali (inclusi i codici OTA)
- Il titolo pagina diventa
  "Modifica Immobile — {displayName}"
- Il pulsante salva chiama
  updateProperty(id, formData)
  invece di createProperty()
- Successo → navigate(`/properties/${id}`)
  con toast "Immobile aggiornato"
- Errore 400 → mostra messaggio dal server
- Tutti i campi e validazioni identici
  a PropertyCreate

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — routing + link
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi la rotta in App.tsx (o router):
/properties/:id/edit → PropertyEdit

In PropertyDetail.tsx aggiungi un pulsante
"Modifica" nella card azioni esistente:
- icona Edit da lucide-react
- onClick → navigate(`/properties/${id}/edit`)
- Posizionalo accanto al pulsante
  Disattiva/Riattiva già presente

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
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

# Verifica codici OTA prima della modifica
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/properties/1" \
| python3 -m json.tool | grep -A 20 "otaCodes"

# Modifica codici OTA
curl -s -X PUT \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"displayName": "Appartamento Trastevere",
"internalCode": "ROM-001",
"city": "Roma",
"otaCodes": [
{"canaleCodiceName": "airbnb",
"externalId": "AIRBNB-TEST-123"},
{"canaleCodiceName": "booking",
"externalId": "BOOKING-TEST-456"}
]
}' \
"http://localhost:8081/sostitutoincloud/\
api/properties/1" \
| python3 -m json.tool | grep -A 20 "otaCodes"

Verifica che i codici OTA siano aggiornati
correttamente.
Riporta output build e curl.
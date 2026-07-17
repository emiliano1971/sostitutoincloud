Leggi il file CLAUDE.md, docs/db/schema-target.sql e
docs/analisi-frontend.md prima di procedere.

Aggiungi il nome del proprietario alla lista documenti,
per permettere di collegare visivamente un documento
fiscale agli F24 dello stesso proprietario.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica dto/document/DocumentListDTO.java:
Aggiungi i campi:
* Integer fkOwnerId
* String ownerName    ← first_name + ' ' + last_name

(Stessa logica già usata in CuListDTO/SettlementListDTO
per ownerName)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/FiscalDocumentService.java:

- Aggiungi OwnerProfileDAO al costruttore

- Modifica buildLookupMaps() per includere anche:
    * Map<Integer, OwnerProfile> ownersById
      Carica tutti gli owner del tenant una volta sola
      (no N+1)

- Modifica resolvePropertyName() o aggiungi nuovo
  metodo resolveOwnerName(Integer bookingId,
  Map<Integer,Booking> bookingsById,
  Map<Integer,Property> propertiesById,
  Map<Integer,OwnerProfile> ownersById) → String
    - recupera booking → fkPropertyId
    - recupera property → fkOwnerId
    - recupera owner → first_name + ' ' + last_name
    - se booking, property o owner non trovato:
      restituisci null (documento non collegato
      a nessun booking, es. nota credito manuale)

- Aggiorna findByTenantId():
    - popola fkOwnerId e ownerName su ogni
      DocumentListDTO usando resolveOwnerName()
    - se vuoi permettere il filtro lato frontend,
      aggiungi anche un parametro opzionale
      ownerId alla firma del metodo e filtra
      IN MEMORIA su fkOwnerId quando presente

- Log INFO aggiornato con eventuale filtro owner

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica controller/DocumentController.java:

GET /api/documents
- aggiungi query param opzionale: ownerId
- passa a fiscalDocumentService.findByTenantId()

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — DocumentsList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/DocumentsList.tsx:
- aggiungi colonna "Proprietario" nella tabella
  (usa ownerName dal DTO)
- se ownerName è null, mostra "—"
- aggiungi il nome proprietario come click-through:
  cliccando sul nome, naviga a
  /tenant/f24?ownerId={fkOwnerId}
  (filtro F24 list per quel proprietario —
  verifica se F24List.tsx supporta già un
  query param ownerId; se non lo supporta,
  aggiungilo seguendo lo stesso pattern già
  usato per altri filtri in quella pagina)

Aggiorna frontend/src/api/documentApi.ts:
- aggiungi fkOwnerId e ownerName all'interfaccia
  DocumentListItem (o nome equivalente già presente)
- aggiungi parametro opzionale ownerId alla
  funzione che chiama GET /api/documents

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

Dopo riavvio Tomcat verifica con curl autenticato:

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it","password":"atena"}' \
| python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/api/documents" \
| python3 -m json.tool | head -40

Verifica che ogni documento collegato a un booking
riporti ownerName non null e coerente con
l'owner della relativa property.

Riporta output dei due build e del curl.
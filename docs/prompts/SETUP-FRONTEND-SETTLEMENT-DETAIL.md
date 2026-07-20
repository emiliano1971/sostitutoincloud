Leggi il file CLAUDE.md e i file esistenti:
- frontend/src/pages/tenant/SettlementsList.tsx
- frontend/src/api/settlementApi.ts
  prima di procedere.

Crea la pagina di dettaglio liquidazione
/settlements/{id} che mostra le prenotazioni
incluse con tutti i dati fiscali.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. settlementApi.ts — verifica/aggiungi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che esistano già le interfacce:

SettlementBookingItem:
- bookingId: number
- externalBookingId: string
- propertyName: string
- checkinDate: string
- checkoutDate: string
- grossAmount: number
- ownerNetAmount: number
- withholdingAmount: number

SettlementDetail (estende SettlementListItem):
- fkTenantId: number
- fkOwnerId: number
- updatedAt: string
- bookings: SettlementBookingItem[]

Se non esistono aggiungile.

Verifica che esista:
export async function getSettlementById(
id: number
): Promise<SettlementDetail>
// GET /api/settlements/{id}

Se non esiste aggiungila.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SettlementDetail.tsx — nuova pagina
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/pages/tenant/
SettlementDetail.tsx:

STRUTTURA PAGINA:

─── Header ───────────────────────────────
← Torna alle liquidazioni  (link)
Liquidazione — {ownerName}
Periodo: {period}  |  Stato: {badge}
──────────────────────────────────────────

─── Tabella prenotazioni ─────────────────
Colonne:
# | ID Prenotazione | Immobile | Check-in
| Check-out | Notti | Lordo € | Bollo €
| Ritenuta € | Netto €

Per ogni riga (da settlement.bookings):
- externalBookingId (monospace, piccolo)
- propertyName
- checkinDate / checkoutDate
- notti = differenza giorni checkout-checkin
- grossAmount → "Lordo €"
- bollo: ricavalo dai fiscal_document
  del booking (vedi nota sotto)
- withholdingAmount → "Ritenuta €"
  in rosso con segno negativo
- ownerNetAmount → "Netto €"
  in grassetto

NOTA sul bollo:
Il bollo non è in SettlementBookingItem.
Aggiungi bolloCents: number (0 o 200)
a SettlementBookingItem nel DTO backend
(SettlementBookingDTO.java) e popolalo
in SettlementService.findById() leggendo
bollo_amount dal fiscal_document di tipo
ricevuta associato al booking:
fiscalDocumentDAO.findByBookingId(bookingId)
.stream()
.filter(d -> d.getTipoDocumento()
.equals("ricevuta"))
.mapToDouble(d -> d.getBolloAmount()
.doubleValue())
.sum()
Se nessuna ricevuta trovata: 0

─── Totali ───────────────────────────────
Riga totali in fondo alla tabella
con sfondo grigio chiaro e testo bold:

TOTALE  |  |  |  |  | {sum lordo}
| {sum bollo} | {sum ritenuta}
| {sum netto}

─── Riepilogo card ───────────────────────
Sotto la tabella, 4 card affiancate:

Lordo totale     Bollo totale
{totalAmount}    {sumBollo}

Ritenute totali  Netto da pagare
{withholdingAmount} {netAmount}
(in rosso)          (in verde bold)

──────────────────────────────────────────

NAVIGAZIONE:
- La riga della tabella è cliccabile
  → naviga a /bookings/{bookingId}
- Pulsante "← Torna alle liquidazioni"
  → navigate('/settlements')

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. ROUTING
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in App.tsx (o router):
/settlements/:id → SettlementDetail

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. SettlementsList.tsx — riga cliccabile
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In SettlementsList.tsx rendi ogni riga
della tabella cliccabile:
onClick → navigate(`/settlements/${s.id}`)
cursor: pointer

Rimuovi o sostituisci il link
/settlements?id={settlementId} già
presente in BookingDetail.tsx con
/settlements/{settlementId}
(ora che esiste la pagina dedicata)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/settlements/3" \
| python3 -m json.tool

Verifica che:
- bookings[] contenga le prenotazioni
  con bolloAmount valorizzato
- la somma ownerNetAmount dei booking
  corrisponda a settlement.netAmount
- totali coerenti

Riporta output build e curl.
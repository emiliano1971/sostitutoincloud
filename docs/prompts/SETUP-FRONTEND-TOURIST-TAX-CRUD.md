Leggi il file CLAUDE.md e i file esistenti:
- frontend/src/pages/tenant/TouristTaxSettings.tsx
- frontend/src/api/touristTaxApi.ts
- frontend/src/components/ComuneAutocomplete.tsx
  prima di procedere.

Migliora la pagina Tassa di Soggiorno:
1. Sostituisci i mock con API reali
2. Aggiungi form creazione/modifica regola
   con ComuneAutocomplete per il comune

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. touristTaxApi.ts — verifica/completa
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che esistano tutte queste funzioni
in touristTaxApi.ts, aggiungile se mancano:

getTouristTaxRules(): Promise<RegolaTassaSoggiornoListDTO[]>
// GET /api/tourist-tax

getTouristTaxRule(id): Promise<RegolaTassaSoggiornoDetailDTO>
// GET /api/tourist-tax/{id}

createTouristTaxRule(dto): Promise<RegolaTassaSoggiornoDetailDTO>
// POST /api/tourist-tax

updateTouristTaxRule(id, dto): Promise<RegolaTassaSoggiornoDetailDTO>
// PUT /api/tourist-tax/{id}

updateTouristTaxStatus(id, attivo): Promise<void>
// PATCH /api/tourist-tax/{id}/status

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. TouristTaxSettings.tsx — API reali
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Sostituisci i mock con chiamate API reali:
- useEffect → getTouristTaxRules()
- Toggle attivo → updateTouristTaxStatus()
- Aggiungi loading/error state

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. TouristTaxRuleDialog.tsx — nuovo componente
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/components/
TouristTaxRuleDialog.tsx:

Props:
open: boolean
onClose: () => void
onSaved: () => void
regola?: RegolaTassaSoggiornoDetailDTO
← se presente = modifica, altrimenti = crea

SEZIONI DEL FORM:

─── Dati principali ──────────────────────
- Comune * → usa ComuneAutocomplete
  onChange → imposta comune (nome) e
  provincia (siglaProvincia dal comune)
- Provincia → readonly, popolata dal comune
- Regione → readonly, popolata dal comune
- Importo per notte * → input numerico (€)
- Max notti → input numerico
- Max importo per persona → input numerico (€)
- Valida dal * → date input
- Valida al → date input (opzionale)
- Esenzioni → textarea (una per riga)
- Note → textarea

─── Fasce età ────────────────────────────
Lista dinamica di fasce con:
+ Aggiungi fascia
  Per ogni fascia:
  Label | Età min | Età max | Riduzione %
  [X rimuovi]

─── Stagioni ─────────────────────────────
Lista dinamica:
+ Aggiungi stagione
  Per ogni stagione:
  Label | Dal (gg/mm) | Al (gg/mm) | Riduzione %
  [X rimuovi]

─── Zone ─────────────────────────────────
Lista dinamica:
+ Aggiungi zona
  Per ogni zona:
  Label | Riduzione %
  [X rimuovi]

Footer:
[Annulla] [Salva]

Al salvataggio:
- se regola presente → updateTouristTaxRule()
- altrimenti → createTouristTaxRule()
- successo → toast + onSaved()
- errore → messaggio inline

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. TouristTaxSettings.tsx — integra dialog
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi in TouristTaxSettings.tsx:

- Pulsante "+ Aggiungi regola" in cima
  → apre TouristTaxRuleDialog (crea)

- Per ogni regola nella lista aggiungi
  icona matita (Edit) → apre
  TouristTaxRuleDialog (modifica)
  caricando il dettaglio con
  getTouristTaxRule(id)

- Dopo onSaved → ricarica lista

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
api/tourist-tax" \
| python3 -m json.tool | head -30

Verifica che:
- La lista regole mostri i dati reali dal DB
- Il form crea una nuova regola con
  ComuneAutocomplete funzionante
- La modifica precarica i dati esistenti

Riporta output build e curl.
Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Collega le pagine di dettaglio al backend reale
sostituendo i dati mock.
Segui lo stesso pattern già usato per OwnersList.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima leggi e descrivi brevemente:
- frontend/src/pages/tenant/BookingDetail.tsx
- frontend/src/pages/tenant/OwnerDetail.tsx
- frontend/src/pages/tenant/PropertyDetail.tsx

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. AGGIORNA BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/BookingDetail.tsx:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mock con:
  const { id } = useParams()
  useEffect → getBookingById(Number(id))
- Aggiungi loading/error state
- Adatta i campi mock → API:
    * booking_id → id
    * channel → channelName
    * booking_status → statoPrenotazione
    * document_status → documentStatus
    * property_name → propertyName
    * owner_name → ownerName
    * fiscal_scenario_code → fiscalScenarioCode
    * Split economico: usa booking.splitEconomico
      (già calcolato dal backend)
    * Le 3 card status usano:
      statoPrenotazione, paymentStatus,
      documentStatus, settlementStatus

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA OwnerDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/OwnerDetail.tsx:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mock con:
  const { id } = useParams()
  useEffect → getOwnerById(Number(id))
- Aggiungi loading/error state
- Adatta i campi mock → API:
    * owner_id → id
    * first_name → firstName
    * last_name → lastName
    * tax_code → taxCode
    * fiscal_regime → fiscalRegime
    * properties_count → propertiesCount
    * status === 'active' → attivo === true
    * Riepilogo economico:
      bookingsCount, totalGrossAmount,
      totalOwnerNet, settlementsCount
      (già nel OwnerDetail dal backend)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA PropertyDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/PropertyDetail.tsx:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mock con:
  const { id } = useParams()
  useEffect → getPropertyById(Number(id))
- Aggiungi loading/error state
- Adatta i campi mock → API:
    * property_id → id
    * display_name → displayName
    * internal_code → internalCode
    * cin_code → cinCode
    * property_type → propertyType
    * status === 'active' → attivo === true
    * ota_codes (oggetto) → otaCodes (array)
    * Per le ultime prenotazioni chiama:
      getBookings({ page: 0, size: 5 })
      e filtra per propertyId lato frontend
      (non c'è endpoint dedicato per ora)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che il frontend compili:
cd frontend && npm run build

Riporta eventuali errori TypeScript e il risultato.
Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Collega PropertiesList e BookingsList al backend reale
sostituendo i dati mock. Segui lo stesso pattern
già usato per OwnersList.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima leggi e descrivi brevemente:
- frontend/src/pages/tenant/PropertiesList.tsx
- frontend/src/pages/tenant/BookingsList.tsx
- frontend/src/types/index.ts (tipi Property e Booking)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. CREA frontend/src/api/propertyApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

```ts
export interface OtaCode {
  canaleCodiceName: string;
  externalId: string;
}

export interface PropertyListItem {
  id: number;
  internalCode: string;
  displayName: string;
  address?: string;
  city: string;
  region?: string;
  propertyType: string;
  cinCode?: string;
  attivo: boolean;
  ownerName: string;
  listingsCount: number;
  bookingsCount: number;
  otaCodes: OtaCode[];
  createdAt: string;
}

export interface PropertyDetail extends PropertyListItem {
  fkTenantId: number;
  fkOwnerId: number;
  fkPmUserId?: number;
  updatedAt: string;
}
```

Funzioni:
```ts
export async function getProperties(
  attivo?: boolean
): Promise<PropertyListItem[]>

export async function getPropertyById(
  id: number
): Promise<PropertyDetail>
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CREA frontend/src/api/bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

```ts
export interface SplitEconomico {
  grossAmount: number;
  otaCommissionAmount: number;
  cleaningAmount: number;
  pmFeeAmount: number;
  ownerNetAmount: number;
  withholdingAmount: number;
  liquidazioneOwner: number;
  touristTaxAmount: number;
  touristTaxIncludedInGross: boolean;
}

export interface BookingListItem {
  id: number;
  externalBookingId: string;
  guestName: string;
  propertyName: string;
  ownerName: string;
  channelName: string;
  checkinDate: string;
  checkoutDate: string;
  nights: number;
  guests: number;
  grossAmount: number;
  ownerNetAmount: number;
  statoPrenotazione: string;
  paymentStatus: string;
  documentStatus: string;
  settlementStatus: string;
  createdAt: string;
}

export interface BookingDetail extends BookingListItem {
  fkTenantId: number;
  fkPropertyId: number;
  fkOwnerId: number;
  guestTaxCode?: string;
  fiscalScenarioCode?: string;
  otaCommissionAmount?: number;
  cleaningAmount?: number;
  pmFeeAmount?: number;
  withholdingAmount?: number;
  touristTaxAmount?: number;
  touristTaxIncludedInGross: boolean;
  touristTaxCollection?: string;
  updatedAt: string;
  splitEconomico: SplitEconomico;
}
```

Funzioni:
```ts
export async function getBookings(params?: {
  status?: string;
  channel?: string;
  q?: string;
  page?: number;
  size?: number;
}): Promise<BookingListItem[]>
// GET /api/bookings con query params

export async function getBookingById(
  id: number
): Promise<BookingDetail>
// GET /api/bookings/{id}
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA PropertiesList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/PropertiesList.tsx:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mock con useEffect + getProperties()
- Aggiungi loading/error state
- Adatta i campi mock → API:
    * property_id → id
    * display_name → displayName
    * internal_code → internalCode
    * cin_code → cinCode
    * property_type → propertyType
    * owner_id → ownerName (già denormalizzato)
    * status === 'active' → attivo === true
    * listings_count → listingsCount
    * bookings_count → bookingsCount
    * ota_codes (oggetto) → otaCodes (array OtaCode[])
      il tooltip OTA va adattato al nuovo formato array

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA BookingsList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/BookingsList.tsx:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mock con useEffect + getBookings()
- Al cambio filtro (status, channel) richiama
  getBookings() con i nuovi parametri
- Aggiungi loading/error state
- Adatta i campi mock → API:
    * booking_id → id
    * channel → channelName
    * booking_status → statoPrenotazione
    * document_status → documentStatus
    * property_name → propertyName (già denormalizzato)
    * owner_name → ownerName (già denormalizzato)
- Il filtro "da_completare" passa status='da_completare'
  all'API — il backend lo gestisce già
- La search locale filtra su guestName, propertyName,
  externalBookingId (in memoria dopo il fetch)
- I colori canale (airbnb/booking/vrbo) si basano
  su channelName.toLowerCase()
- Il row highlighting (penale/overdue) si basa su:
    * checkout_date < oggi - 12gg AND stato non finale
      → penale
    * checkout_date < oggi AND stato non finale
      → overdue

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che il frontend compili:
cd frontend && npm run build

Riporta eventuali errori TypeScript e il risultato.
# Analisi entità mock — fonte: frontend/src/data/mock-data.ts

Documento generato dall'analisi di `mock-data.ts` e `types/index.ts`.  
Ogni sezione descrive un'entità con i campi così come appaiono nel frontend React.  
Usare come base di partenza per lo schema PostgreSQL target.

---

## Tipi enumerati (type alias TypeScript)

| Tipo | Valori |
|---|---|
| `UserRole` | `super_admin` \| `tenant_admin` \| `pm_user` \| `owner_user` |
| `TenantStatus` | `draft` \| `active` \| `suspended` \| `closed` |
| `BookingStatus` | `imported` \| `enriched` \| `ready` \| `doc_issued` \| `settled` \| `cancelled` |
| `DocumentStatus` | `draft` \| `ready` \| `sent_sdi` \| `accepted` \| `rejected` \| `error` |
| `PaymentStatus` | `pending` \| `received` \| `failed` |
| `SettlementStatus` | `pending` \| `calculated` \| `approved` \| `paid` |
| `OwnerType` | `persona_fisica` \| `piva` \| `societa` |
| `FiscalRegime` | `cedolare_secca` \| `iva_10` \| `ordinario` |
| `F24Status` | `draft` \| `ready` \| `sent` \| `paid` \| `error` |

---

## UserContext

Utente autenticato nella sessione. Non è un'entità DB autonoma ma aggrega dati da più tabelle (utente + tenant + owner).

| Campo | Tipo TS | Note |
|---|---|---|
| `user_id` | `string` | PK |
| `email` | `string` | |
| `first_name` | `string` | |
| `last_name` | `string` | |
| `role` | `UserRole` | enum: super_admin, tenant_admin, pm_user, owner_user |
| `tenant_id` | `string?` | null per super_admin |
| `tenant_name` | `string?` | denormalizzato per UI |
| `owner_id` | `string?` | solo per role = owner_user |

---

## Tenant

Agenzia / Property Manager cliente della piattaforma.

| Campo | Tipo TS | Note |
|---|---|---|
| `tenant_id` | `string` | PK, es. `t1` |
| `legal_name` | `string` | ragione sociale |
| `display_name` | `string` | nome breve per UI |
| `tax_code` | `string` | codice fiscale |
| `vat_number` | `string` | partita IVA con prefisso IT |
| `tenant_status` | `TenantStatus` | enum: draft, active, suspended, closed |
| `administrative_email` | `string` | email contatto principale |
| `pec` | `string` | PEC |
| `phone` | `string` | telefono |
| `legal_address` | `string` | indirizzo completo in unico campo |
| `created_at` | `string` | data ISO 8601 (solo data) |
| `activated_at` | `string?` | data attivazione, null se non ancora attivo |
| `properties_count` | `number` | contatore denormalizzato |
| `owners_count` | `number` | contatore denormalizzato |
| `bookings_count` | `number` | contatore denormalizzato |

---

## OwnerProfile

Proprietario degli immobili gestiti dal tenant.

| Campo | Tipo TS | Note |
|---|---|---|
| `owner_id` | `string` | PK, es. `o1` |
| `tenant_id` | `string` | FK → Tenant |
| `owner_type` | `OwnerType` | enum: persona_fisica, piva, societa |
| `first_name` | `string` | per persona_fisica |
| `last_name` | `string` | per persona_fisica |
| `legal_name` | `string?` | per piva / societa |
| `tax_code` | `string` | codice fiscale (16 char per persona_fisica) |
| `vat_number` | `string?` | partita IVA, solo per piva / societa |
| `fiscal_regime` | `FiscalRegime` | enum: cedolare_secca, iva_10, ordinario |
| `email` | `string` | |
| `phone` | `string` | |
| `iban` | `string` | IBAN per accredito liquidazioni |
| `status` | `'active' \| 'inactive'` | |
| `properties_count` | `number` | contatore denormalizzato |
| `created_at` | `string` | data ISO 8601 |

---

## Property

Immobile gestito dal tenant per conto di un owner.

| Campo | Tipo TS | Note |
|---|---|---|
| `property_id` | `string` | PK, es. `p1` |
| `tenant_id` | `string` | FK → Tenant |
| `owner_id` | `string` | FK → OwnerProfile |
| `pm_id` | `string` | ID property manager assegnato |
| `internal_code` | `string` | codice interno es. `ROM-001` |
| `display_name` | `string` | nome descrittivo dell'immobile |
| `address` | `string` | via e numero civico |
| `city` | `string` | |
| `region` | `string` | es. `Lazio` |
| `property_type` | `string` | es. `LT` (Locazione Turistica) |
| `cin_code` | `string` | Codice Identificativo Nazionale (CIN) |
| `ota_codes` | `object` | codici IDs sulle singole OTA (vedi sotto) |
| `ota_codes.airbnb_id` | `string?` | |
| `ota_codes.booking_id` | `string?` | |
| `ota_codes.vrbo_id` | `string?` | |
| `ota_codes.tripadvisor_id` | `string?` | |
| `ota_codes.expedia_id` | `string?` | |
| `status` | `'active' \| 'inactive'` | |
| `listings_count` | `number` | contatore denormalizzato |
| `bookings_count` | `number` | contatore denormalizzato |
| `created_at` | `string` | data ISO 8601 |

> **Nota DB**: `ota_codes` è un oggetto nested — candidato a colonna JSONB in PostgreSQL
> oppure tabella separata `property_ota_codes(property_id, ota_name, ota_external_id)`.

---

## Booking

Prenotazione importata da canale OTA o diretta.

| Campo | Tipo TS | Note |
|---|---|---|
| `booking_id` | `string` | PK, es. `b1` |
| `tenant_id` | `string` | FK → Tenant |
| `property_id` | `string` | FK → Property |
| `property_name` | `string` | denormalizzato per UI |
| `owner_name` | `string` | denormalizzato per UI |
| `guest_name` | `string` | nome ospite |
| `guest_tax_code` | `string` | codice fiscale ospite (anche estero) |
| `external_booking_id` | `string` | ID prenotazione sul canale OTA |
| `channel_name` | `string` | es. `airbnb`, `booking`, `vrbo` |
| `checkin_date` | `string` | data ISO 8601 |
| `checkout_date` | `string` | data ISO 8601 |
| `nights` | `number` | numero notti |
| `guests` | `number` | numero ospiti |
| `gross_amount` | `number` | incasso lordo dal canale OTA (€) |
| `ota_commission_amount` | `number` | commissione trattenuta da OTA (€) |
| `cleaning_amount` | `number` | quota pulizie (€) |
| `pm_fee_amount` | `number` | provvigione property manager (€) |
| `owner_net_amount` | `number` | netto spettante al proprietario (€) |
| `withholding_amount` | `number` | ritenuta 21% sul netto (€) |
| `tourist_tax_amount` | `number` | tassa di soggiorno (€) |
| `tourist_tax_included_in_gross` | `boolean` | se già inclusa nel gross_amount |
| `tourist_tax_collection` | `'contanti' \| 'payment_link' \| 'altro'` | modalità riscossione tassa soggiorno |
| `booking_status` | `BookingStatus` | stato workflow prenotazione |
| `payment_status` | `PaymentStatus` | stato incasso |
| `document_status` | `DocumentStatus` | stato documento fiscale associato |
| `settlement_status` | `SettlementStatus` | stato liquidazione proprietario |
| `fiscal_scenario_code` | `string` | codice scenario fiscale applicato, es. `scenario_A` |
| `created_at` | `string` | data ISO 8601 |

---

## FiscalDocument

Documento fiscale emesso per una prenotazione (fattura o ricevuta). Ogni prenotazione genera due documenti: una fattura PM→ospite e una ricevuta owner→ospite.

| Campo | Tipo TS | Note |
|---|---|---|
| `document_id` | `string` | PK, es. `doc-ft-1` |
| `tenant_id` | `string` | FK → Tenant |
| `booking_id` | `string` | FK → Booking |
| `document_type` | `'fattura' \| 'ricevuta' \| 'nota_credito'` | tipo documento |
| `document_number` | `string` | numero progressivo es. `FT-2025-0001` |
| `issue_date` | `string` | data emissione ISO 8601 |
| `recipient_name` | `string` | nome destinatario (ospite) |
| `total_amount` | `number` | totale documento (€) |
| `vat_amount` | `number` | IVA (€), 0 per ricevute fuori campo |
| `status` | `DocumentStatus` | stato documento |
| `sdi_status` | `string?` | codice esito SDI es. `RC` (ricevuta consegna), `MC` (mancata consegna) |
| `sdi_identifier` | `string?` | identificativo SDI assegnato |
| `property_name` | `string` | denormalizzato per UI |
| `channel_name` | `string` | denormalizzato per UI |

---

## Settlement

Liquidazione periodica di un proprietario (rendiconto mensile).

| Campo | Tipo TS | Note |
|---|---|---|
| `settlement_id` | `string` | PK, es. `set1` |
| `tenant_id` | `string` | FK → Tenant |
| `owner_id` | `string` | FK → OwnerProfile |
| `owner_name` | `string` | denormalizzato per UI |
| `period` | `string` | mese di riferimento es. `2025-01` (YYYY-MM) |
| `total_amount` | `number` | totale netto prenotazioni del periodo (€) |
| `withholding_amount` | `number` | totale ritenute del periodo (€) |
| `net_amount` | `number` | netto da liquidare = total - withholding (€) |
| `bookings_count` | `number` | numero prenotazioni incluse |
| `status` | `SettlementStatus` | enum: pending, calculated, approved, paid |
| `payment_date` | `string?` | data accredito, null se non ancora pagato |
| `created_at` | `string` | data ISO 8601 |

---

## F24Record

Versamento periodico delle ritenute operate tramite modello F24.

| Campo | Tipo TS | Note |
|---|---|---|
| `f24_id` | `string` | PK, es. `f24-1` |
| `tenant_id` | `string` | FK → Tenant |
| `period` | `string` | mese di riferimento es. `2025-01` (YYYY-MM) |
| `tax_code` | `string` | codice tributo, es. `1919` (ritenute locazioni brevi) |
| `total_amount` | `number` | importo totale da versare (€) |
| `withholdings_count` | `number` | numero di ritenute aggregate |
| `status` | `F24Status` | enum: draft, ready, sent, paid, error |
| `deadline_date` | `string` | scadenza versamento ISO 8601 |
| `payment_date` | `string?` | data pagamento effettivo, null se non pagato |
| `created_at` | `string` | data ISO 8601 |

---

## CURecord

Certificazione Unica (CU) annuale per proprietario — attestazione ritenute operate nell'anno fiscale.

| Campo | Tipo TS | Note |
|---|---|---|
| `cu_id` | `string` | PK, es. `cu1` |
| `tenant_id` | `string` | FK → Tenant |
| `owner_id` | `string` | FK → OwnerProfile |
| `owner_name` | `string` | denormalizzato per UI |
| `tax_year` | `number` | anno fiscale di riferimento, es. `2024` |
| `total_compensi` | `number` | totale compensi dell'anno (€) |
| `total_ritenute` | `number` | totale ritenute operate nell'anno (€) |
| `status` | `'draft' \| 'generated' \| 'sent' \| 'delivered'` | stato CU |
| `generated_at` | `string?` | timestamp generazione, null se draft |
| `created_at` | `string` | data ISO 8601 |

---

## AuditLogEntry

Log di audit delle operazioni significative eseguite dagli utenti.

| Campo | Tipo TS | Note |
|---|---|---|
| `log_id` | `string` | PK, es. `al1` |
| `tenant_id` | `string?` | FK → Tenant, null per azioni super_admin cross-tenant |
| `user_email` | `string` | email utente che ha eseguito l'azione |
| `action` | `string` | codice azione es. `booking.import`, `document.issue`, `tenant.create` |
| `entity_type` | `string` | tipo entità coinvolta es. `Booking`, `FiscalDocument` |
| `entity_id` | `string` | ID entità coinvolta |
| `details` | `string` | descrizione testuale libera dell'operazione |
| `ip_address` | `string` | IP del chiamante |
| `created_at` | `string` | timestamp ISO 8601 con ora |

> **Valori `action` osservati nei mock**: `booking.import`, `document.issue`,
> `document.choose_ricevuta`, `settlement.approve`, `f24.generate`,
> `owner.create`, `tenant.create`, `tenant.suspend`.

---

## AlertItem

Avvisi e notifiche da mostrare in dashboard.

| Campo | Tipo TS | Note |
|---|---|---|
| `id` | `string` | PK |
| `type` | `'warning' \| 'error' \| 'info'` | severità |
| `message` | `string` | testo dell'avviso |
| `action` | `string?` | etichetta CTA opzionale |
| `created_at` | `string` | timestamp ISO 8601 |

> **Nota**: AlertItem probabilmente non è una tabella DB autonoma ma viene generata
> dinamicamente da query su Booking, FiscalDocument, F24 in base a regole di business
> (es. documenti in scadenza, F24 da generare, errori SDI).

---

## RevenueData (chart)

Dati aggregati per grafico ricavi mensili. Non è un'entità persistita ma una vista aggregata.

| Campo | Tipo TS | Note |
|---|---|---|
| `month` | `string` | abbreviazione mese es. `Apr`, `Mag` |
| `ricavi_pm` | `number` | ricavi del property manager (€) |
| `ricavi_ow` | `number` | ricavi lordi dei proprietari (€) |
| `commissioni` | `number` | commissioni OTA totali (€) |
| `ritenute` | `number` | ritenute totali operate (€) |

> **Nota**: questi dati verranno prodotti da una query di aggregazione su `Booking`
> raggruppando per mese di checkout.

---

## Riepilogo relazioni

```
Tenant (1) ──< OwnerProfile (N)
Tenant (1) ──< Property (N)
OwnerProfile (1) ──< Property (N)
Property (1) ──< Booking (N)
Booking (1) ──< FiscalDocument (N)   [tipicamente 2: fattura + ricevuta]
OwnerProfile (1) ──< Settlement (N)
Tenant (1) ──< F24Record (N)
OwnerProfile (1) ──< CURecord (N)    [1 per anno fiscale]
Tenant/User ──> AuditLogEntry        [log cross-entità]
```

## Note per lo schema PostgreSQL

| Aspetto | Osservazione |
|---|---|
| `ota_codes` in Property | Candidato a JSONB o tabella separata `property_ota_code` |
| Campi `*_count` denormalizzati | Da calcolare con COUNT() — non persistere come colonne |
| Campi `*_name` denormalizzati in Booking/Settlement/CU | Recuperabili via JOIN — valutare se materializzare |
| `period` in Settlement/F24 | Formato `YYYY-MM` — usare `DATE` (primo del mese) o `VARCHAR(7)` |
| `tax_year` in CURecord | `SMALLINT` |
| `created_at` / date | Usare `DATE` per sole date, `TIMESTAMP WITH TIME ZONE` per timestamp con ora |
| `sdi_status` in FiscalDocument | Valori SDI noti: `RC` (ricevuta consegna), `MC` (mancata consegna), `NS` (non consegnato), `DT` (decorrenza termini), `AT` (attestazione trasmissione) |

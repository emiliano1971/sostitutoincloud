# Tracciato CSV Import Prenotazioni

## Formato
- Encoding: UTF-8
- Separatore: virgola `,`
- Prima riga: header (obbligatoria)
- Date: formato `YYYY-MM-DD`
- Importi: decimali con punto `.` (es. `150.00`)
- Booleani: `true` / `false`

## Colonne (in ordine)

| Colonna | Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|---|
| 1 | `external_booking_id` | String | ✓ | ID prenotazione sul canale OTA |
| 2 | `channel_code` | String | ✓ | Codice canale (airbnb, booking, vrbo…) |
| 3 | `property_code` | String | ✓ | Codice interno immobile (es. ROM-001) |
| 4 | `guest_name` | String | ✓ | Nome e cognome ospite |
| 5 | `guest_email` | String | — | Email ospite |
| 6 | `guest_phone` | String | — | Telefono ospite |
| 7 | `guest_tax_code` | String | — | Codice fiscale ospite |
| 8 | `checkin_date` | Date | ✓ | Data check-in (YYYY-MM-DD) |
| 9 | `checkout_date` | Date | ✓ | Data check-out (YYYY-MM-DD) |
| 10 | `nights` | Integer | ✓ | Numero notti |
| 11 | `guests` | Integer | ✓ | Numero ospiti |
| 12 | `status` | String | ✓ | Stato prenotazione (confirmed, cancelled…) |
| 13 | `gross_amount` | Decimal | ✓ | Importo lordo |
| 14 | `ota_commission_amount` | Decimal | — | Commissione OTA (default 0) |
| 15 | `cleaning_amount` | Decimal | — | Costo pulizie (default 0) |
| 16 | `tourist_tax_amount` | Decimal | — | Tassa di soggiorno (default 0) |
| 17 | `tourist_tax_included` | Boolean | — | Tassa inclusa nel lordo (default false) |
| 18 | `currency` | String | — | Valuta (default EUR) |

## Esempio

```csv
external_booking_id,channel_code,property_code,guest_name,guest_email,guest_phone,guest_tax_code,checkin_date,checkout_date,nights,guests,status,gross_amount,ota_commission_amount,cleaning_amount,tourist_tax_amount,tourist_tax_included,currency
AIRBNB-TEST-0010,airbnb,ROM-001,Mario Rossi,mario@email.it,+39333000,RSSMRA80A01H501Z,2026-07-01,2026-07-05,4,2,confirmed,480.00,72.00,50.00,14.00,false,EUR
```

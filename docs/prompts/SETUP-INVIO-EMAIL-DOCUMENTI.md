Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- service/PasswordResetService.java
  ← per riusare il meccanismo email
- dao/BookingDAO.java
- model/Booking.java
- service/BookingImportService.java
  righe 119-200 (intestazioni
  riconosciute)
- frontend/src/pages/tenant/
  BookingDetail.tsx
  nella sezione card documenti
- frontend/src/pages/tenant/
  DocumentDetail.tsx
  prima di procedere.

Implementa l'invio email documenti
(fattura PM + ricevuta owner)
dal dettaglio prenotazione.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica l'ultimo numero migration
e crea il file successivo.

-- Email ospite (più indirizzi
-- separati da ;)
ALTER TABLE booking
ADD COLUMN IF NOT EXISTS
guest_email VARCHAR(500);

COMMENT ON COLUMN booking.guest_email
IS 'Email ospite, più indirizzi
separati da punto e virgola.
Importata dal file prenotazioni
o inserita manualmente.';

-- Testo predefinito email documenti
ALTER TABLE tenant_settings
ADD COLUMN IF NOT EXISTS
email_testo_predefinito TEXT;

COMMENT ON COLUMN
tenant_settings
.email_testo_predefinito IS
'Testo predefinito per l email
di invio documenti fiscali
(fattura PM e ricevuta owner).
Editabile prima dell invio.';

Aggiorna schema-target.sql.
Esegui sul DB locale.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BACKEND — model e DAO Booking
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a model/Booking.java:
String guestEmail

Aggiorna BookingRowMapper:
leggi guest_email

Aggiorna BookingDAO:
- SELECT: aggiungi guest_email
- INSERT: aggiungi guest_email
- UPDATE ospite (GuestUpdateDTO):
  aggiungi guest_email

Aggiungi a GuestUpdateDTO.java:
String guestEmail

Aggiungi a BookingDetailDTO.java:
String guestEmail

Aggiorna BookingService
.toDetailDTO() per esporre
guestEmail.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BACKEND — TenantSettings
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a model/TenantSettings.java:
String emailTestoPredefinito

Aggiorna mapper, DAO, DTO
per leggere e scrivere
email_testo_predefinito.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — Import prenotazioni
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In BookingImportService.java
aggiungi alle intestazioni
riconosciute del file prenotazioni:

"email" → "EMAIL"
"e-mail" → "EMAIL"
"mail" → "EMAIL"
"Email" → "EMAIL"
"telefono" → "TELEFONO"
← già presente per file ospiti
ma non per file prenotazioni

Nel flusso di conferma import
quando si salva il booking:

SE guest_email dal file prenotazioni
non è blank:
← normalizza: trim()
← se contiene più email
separate da spazio, virgola
o ; → normalizza con ;
booking.setGuestEmail(email)

SE guest_phone dal file prenotazioni
non è blank e booking.guestPhone
è null:
booking.setGuestPhone(phone)

Nel file ospiti se esiste colonna
EMAIL leggi e concatena con ;
al guest_email già presente
se diversa:

SE esistingEmail non contiene
nuovaEmail:
guestEmail = existingEmail
+ ";" + nuovaEmail

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BACKEND — EmailService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/EmailService.java:
- @Service @Log4j2
- Inietta JavaMailSender
  (già configurato nei yml)
- Inietta
  @Value("${app.mail.from}")
  String mailFrom

Metodo principale:

void inviaDocumenti(
Integer tenantId,
Integer bookingId,
String destinatari,
← separati da ;
String oggetto,
String testo,
Integer fatturaId,
Integer ricevutaId)

1. Genera PDF fattura:
   byte[] pdfFattura =
   documentPdfService
   .generaPdfBytes(fatturaId)
   String nomeFattura =
   "Fattura-{documentNumber}.pdf"

2. Genera PDF ricevuta:
   byte[] pdfRicevuta =
   documentPdfService
   .generaPdfBytes(ricevutaId)
   String nomeRicevuta =
   "Ricevuta-{documentNumber}.pdf"

3. Crea MimeMessage:
   MimeMessageHelper helper =
   new MimeMessageHelper(
   message, true, "UTF-8")
   helper.setFrom(mailFrom)

   ← Gestisci più destinatari:
   String[] indirizzi =
   destinatari.split(";")
   for (String addr : indirizzi):
   String trimmed = addr.trim()
   SE !trimmed.isEmpty():
   helper.addTo(trimmed)

   helper.setSubject(oggetto)
   helper.setText(testo, false)
   ← testo plain, non HTML

   helper.addAttachment(
   nomeFattura,
   new ByteArrayResource(
   pdfFattura))
   helper.addAttachment(
   nomeRicevuta,
   new ByteArrayResource(
   pdfRicevuta))

4. mailSender.send(message)

5. Log INFO "EmailService
   .inviaDocumenti() -
   tenant={} booking={}
   destinatari={} oggetto={}"

Aggiorna DocumentPdfService:
Aggiungi metodo
generaPdfBytes(Integer docId)
che restituisce byte[]
invece di ResponseEntity

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BACKEND — DTO e Controller
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/email/
InviaDocumentiDTO.java:
Integer bookingId
String destinatari
← separati da ;
String oggetto
String testo
Integer fatturaId
Integer ricevutaId

Aggiungi a DocumentController.java
o crea EmailController.java:

POST /api/bookings/{id}/invia-documenti
- @RequestBody InviaDocumentiDTO
- tenantId da SecurityUtils
- Validazioni:
  SE destinatari blank → 400
  "Destinatario obbligatorio"
  SE oggetto blank → 400
  "Oggetto obbligatorio"
  SE fatturaId null → 400
  "Fattura non trovata"
  SE ricevutaId null → 400
  "Ricevuta non trovata"
- chiama emailService
  .inviaDocumenti()
- 200 {"message":
  "Email inviata a {destinatari}"}
- 500 se invio fallisce
  con messaggio errore SMTP
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — bookingApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a BookingDetail:
guestEmail?: string

Aggiungi a GuestUpdate:
guestEmail?: string

Aggiungi funzione:
export async function
inviaDocumenti(
bookingId: number,
data: {
destinatari: string
oggetto: string
testo: string
fatturaId: number
ricevutaId: number
}
): Promise<{message: string}>
// POST /api/bookings/{id}
//   /invia-documenti

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. FRONTEND — GuestEditDialog.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi campo email ospite
nel dialog di modifica ospite:

<div className="space-y-1">
  <Label>Email ospite</Label>
  <Input
    value={form.guestEmail ?? ''}
    onChange={e => setForm({
      ...form,
      guestEmail: e.target.value
    })}
    placeholder=
      "email1@example.com;
       email2@example.com"
    type="text"
  />
  <p className="text-xs
    text-muted-foreground">
    Più indirizzi separati da ;
  </p>
</div>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
9. FRONTEND — BookingDetail.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

A) Mostra email ospite nella
card ospite (se presente):

{booking.guestEmail && (
  <div className="flex items-center
    gap-2 text-sm">
    <Mail className="h-3.5 w-3.5
      text-muted-foreground" />
    <span className="text-xs
      break-all">
      {booking.guestEmail}
    </span>
  </div>
)}

B) Mostra guest_phone nella
card ospite (se presente):

{booking.guestPhone && (
  <div className="flex items-center
    gap-2 text-sm">
    <Phone className="h-3.5 w-3.5
      text-muted-foreground" />
    <span className="text-xs">
      {booking.guestPhone}
    </span>
  </div>
)}

C) Box "Invia Documenti":

Mostra il box solo quando:
- fattura emessa e non stornata
- ricevuta emessa e non annullata
- entrambe presenti

const puoInviare =
fattura != null
&& fattura.stato !== 'annullata'
&& !booking.stornata
&& ricevuta != null
&& ricevuta.stato !== 'annullata'

Stato locale:
const [showInvioEmail,
setShowInvioEmail]
= useState(false)
const [emailDest, setEmailDest]
= useState('')
const [emailOggetto,
setEmailOggetto] = useState('')
const [emailTesto, setEmailTesto]
= useState('')
const [inviando, setInviando]
= useState(false)

All'apertura del box precompila:
emailDest = booking.guestEmail ?? ''
emailTesto = tenantSettings
?.emailTestoPredefinito ?? ''
emailOggetto = ''
← vuoto, obbligatorio

{puoInviare && (
<Card className="mt-4">
<CardHeader
className="pb-2 cursor-pointer"
onClick={() =>
setShowInvioEmail(
!showInvioEmail)}>
<CardTitle
className="text-sm
flex items-center gap-2">
<Send className="h-4 w-4" />
Invia Documenti
<ChevronDown
className={cn(
"h-4 w-4 ml-auto
transition-transform",
showInvioEmail &&
"rotate-180")}
/>
</CardTitle>
</CardHeader>

    {showInvioEmail && (
      <CardContent
        className="space-y-3">

        <div className="space-y-1">
          <Label className="text-xs">
            Destinatario *
          </Label>
          <Input
            value={emailDest}
            onChange={e =>
              setEmailDest(
                e.target.value)}
            placeholder=
              "email@example.com"
            className="text-sm"
          />
          <p className="text-xs
            text-muted-foreground">
            Più indirizzi separati da ;
          </p>
        </div>

        <div className="space-y-1">
          <Label className="text-xs">
            Oggetto *
          </Label>
          <Input
            value={emailOggetto}
            onChange={e =>
              setEmailOggetto(
                e.target.value)}
            placeholder=
              "Documenti prenotazione
               {booking
                .externalBookingId}"
            className="text-sm"
          />
        </div>

        <div className="space-y-1">
          <Label className="text-xs">
            Testo
          </Label>
          <Textarea
            value={emailTesto}
            onChange={e =>
              setEmailTesto(
                e.target.value)}
            rows={6}
            className="text-sm"
          />
        </div>

        <div className="text-xs
          text-muted-foreground">
          Allegati:
          📄 {fattura.documentNumber}
          🧾 {ricevuta.documentNumber}
        </div>

        <Button
          onClick={handleInviaEmail}
          disabled={
            inviando
            || !emailDest.trim()
            || !emailOggetto.trim()}
          className="w-full"
          size="sm">
          {inviando
            ? "Invio in corso..."
            : "Invia Email"}
        </Button>

      </CardContent>
    )}
  </Card>
)}

handleInviaEmail:
const handleInviaEmail = async () => {
setInviando(true)
try {
await inviaDocumenti(booking.id, {
destinatari: emailDest,
oggetto: emailOggetto,
testo: emailTesto,
fatturaId: fattura.id,
ricevutaId: ricevuta.id
})
toast({
title: "Email inviata",
description:
`Documenti inviati a
 ${emailDest}`
})
setShowInvioEmail(false)
} catch (e: any) {
toast({
title: "Errore invio email",
description: e.message,
variant: "destructive"
})
} finally {
setInviando(false)
}
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
10. FRONTEND — Impostazioni Tenant
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nella scheda "Configurazione"
aggiungi il campo testo predefinito:

<div className="space-y-2">
  <Label>Testo email documenti</Label>
  <p className="text-xs
    text-muted-foreground">
    Testo predefinito per l'invio
    della fattura PM e della ricevuta
    all'ospite. Editabile prima
    di ogni invio.
  </p>
  <Textarea
    value={settings
      .emailTestoPredefinito ?? ''}
    onChange={e => setSettings({
      ...settings,
      emailTestoPredefinito:
        e.target.value
    })}
    rows={8}
    placeholder=
      "Gentile ospite,
       in allegato trova i documenti
       relativi al suo soggiorno..."
  />
</div>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
11. BUILD E TEST
    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal -DskipTests clean package
cd frontend && npm run build
npm run typecheck

TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena2026"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

# Verifica guest_email nel dettaglio
curl -s -H "Authorization: Bearer $TOKEN" \
"http://localhost:8081/sostitutoincloud/\
api/bookings/322" \
| python3 -m json.tool \
| grep "guestEmail"

# Test invio email
# (usa una email reale di test)
curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"destinatari":
"test@example.com",
"oggetto":
"Test documenti prenotazione",
"testo":
"Test invio documenti",
"fatturaId": {FATTURA_ID},
"ricevutaId": {RICEVUTA_ID}
}' \
"http://localhost:8081/sostitutoincloud/\
api/bookings/{BOOKING_ID}/\
invia-documenti" \
| python3 -m json.tool

Verifica che:
- guest_email salvato e visibile
  nel dettaglio booking
- guest_phone visibile nella
  card ospite
- Import prenotazioni legge
  colonna Email e Telefono
- Box "Invia Documenti" appare
  solo con fattura + ricevuta
  entrambe emesse e non stornate
- Email inviata con 2 allegati PDF
- Testo predefinito caricato
  dalle impostazioni tenant

Riporta output build e curl.
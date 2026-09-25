Leggi il CLAUDE.md prima di procedere.
Leggi i file esistenti:
- frontend/src/pages/tenant/BookingDetail.tsx
- frontend/src/api/bookingApi.ts
  prima di procedere.

Aggiorna BookingDetail per mostrare
le righe di booking_split_economico
e aggiungere il pulsante Ricalcola.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. bookingApi.ts — aggiungi tipi
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi interfaccia:

export interface BookingSplitRiga {
id: number
fkBookingId: number
fkPropertyContractRuleId?: number
tipoVoce: string
descrizione: string
importo: number
aliquotaIva: number
includeInFatturaPm: boolean
ordinamento: number
source: string
createdAt?: string
updatedAt?: string
}

Aggiungi a BookingDetail:
righeSplit?: BookingSplitRiga[]

Aggiungi funzione API per ricalcolo:
export async function ricalcolaSplit(
id: number
): Promise<BookingDetail>
// PATCH /api/bookings/{id}/split
// con body {}
// ricalcola tutto dalle regole correnti

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. BookingDetail.tsx — pulsante Ricalcola
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nell'header della sezione
"Split Economico" aggiungi
il pulsante Ricalcola:

Visibile solo se !hasDocuments

<div className="flex items-center
  justify-between">
  <h3 className="font-medium
    flex items-center gap-2">
    <CreditCard className="h-4 w-4" />
    Split Economico
  </h3>
  {!hasDocuments && (
    <button
      onClick={handleRicalcola}
      disabled={isUpdatingSplit}
      className="flex items-center gap-1
        text-xs text-muted-foreground
        hover:text-foreground
        disabled:opacity-50"
      title="Ricalcola dalle regole
        contratto correnti"
    >
      <RefreshCw className={cn(
        "h-3 w-3",
        isUpdatingSplit && "animate-spin"
      )} />
      Ricalcola
    </button>
  )}
</div>

Handler:
const handleRicalcola = async () => {
setIsUpdatingSplit(true)
try {
const updated = await
ricalcolaSplit(booking.id)
setBooking(updated)
toast({
title: "Split ricalcolato",
description: "Importi aggiornati
dalle regole contratto correnti"
})
} catch (e: any) {
toast({
title: "Errore",
description: e.message,
variant: "destructive"
})
} finally {
setIsUpdatingSplit(false)
}
}

Importa RefreshCw da lucide-react
se non già importato.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. BookingDetail.tsx — righe split
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Nella sezione Split Economico
sostituisci le righe hardcodate
(OTA, pulizie, PM) con la lista
dinamica dalle righeSplit:

SE booking.righeSplit è presente
e non vuota:

Mostra le righe dinamiche:
{booking.righeSplit
?.filter(r =>
r.tipoVoce !== 'tassa_soggiorno')
.map(r => (
<div key={r.id}
className="flex items-start
justify-between text-sm">
<div className="flex flex-col">
<span className=
"text-muted-foreground">
{r.descrizione}
</span>
{r.source === 'manuale' && (
<span className="text-xs
text-amber-600">
importo modificato
</span>
)}
</div>
<div className="flex items-center
gap-2">
<span className="text-xs
text-muted-foreground">
({(r.importo /
(booking.touristTaxIncludedInGross
? booking.grossAmount
- (booking.touristTaxAmount
?? 0)
: booking.grossAmount)
* 100).toFixed(1)}%)
</span>
<span className=
"text-destructive">
-{formatAmount(r.importo)}
</span>
{/* icona matita solo per OTA */}
{r.tipoVoce ===
'commissione_ota'
&& !hasDocuments && (
<button
onClick={() => {
setEditingOta(true)
setOtaValue(String(
r.importo))
}}
>
<Pencil className=
"h-3 w-3
text-muted-foreground
hover:text-foreground" />
</button>
)}
</div>
</div>
))
}

La riga tassa soggiorno rimane
mostrata separatamente come oggi
(fuori dal loop delle righe split).

SE booking.righeSplit è vuota o assente:
→ mostra le righe attuali hardcodate
come fallback (booking pre-migrazione)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BookingDetail.tsx — editor OTA
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

L'editor OTA (matita, input %, €,
check, X) rimane invariato ma ora
viene triggerato dal click sulla
matita nella riga split OTA
invece che dalla matita nella
riga hardcodata.

Verifica che handleUpdateSplit
con otaCommissionOverride funzioni
ancora correttamente dopo
l'aggiornamento delle righe split.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. Badge source
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi un badge/indicatore visivo
per il campo source della riga:

- source = 'manuale' →
  testo "importo modificato" in amber
  (già nel punto 3)
- source = 'import' →
  testo "da file" in muted
- source = 'calcolato' →
  nessun badge (default)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. BUILD
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

cd frontend && npm run build
npm run typecheck
Riporta output build.
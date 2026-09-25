import { test, expect } from '@playwright/test';
import { USERS } from './helpers/auth';
import { apiGet, getToken } from './helpers/api';

/**
 * Verifica incrociata degli importi fiscali di una prenotazione lungo tutta la catena:
 * split booking → ricevuta owner / fattura PM → withholding ledger → F24 → liquidazione → CU.
 *
 * Test di sola lettura: non crea, non modifica e non cancella nulla.
 *
 * Uso:
 *   BOOKING_ID=275 npx playwright test tests/e2e/verifica-importi-booking.spec.ts
 *   npx playwright test tests/e2e/verifica-importi-booking.spec.ts   (sceglie il primo con documenti)
 *
 * NB sui nomi dei campi: il tipo documento a DB vale 'ricevuta'/'fattura' (lookup
 * tipo_documento), mentre 'ricevuta_owner'/'fattura_pm' sono i termini usati dall'API di
 * generazione. Qui si accettano entrambe le forme per non dipendere da quale delle due
 * viene esposta.
 */

const EPS = 0.02; // tolleranza: 2 centesimi

const approxEqual = (a: number, b: number, eps = EPS): boolean => Math.abs(a - b) <= eps;

const nz = (v: number | null | undefined): number => v ?? 0;

/** Formatta un delta con segno per i log. */
const fmtDelta = (d: number): string => `${d >= 0 ? '+' : ''}${d.toFixed(2)}`;

const bookingIdParam = process.env.BOOKING_ID ? parseInt(process.env.BOOKING_ID, 10) : 0;

// ── forme minime delle risposte API (solo i campi usati qui) ──────────────────

interface SplitEconomico {
  aliquotaRitenuta?: number;
  liquidazioneOwner?: number;
}

interface DocumentoBooking {
  id: number;
  tipoDocumento: string;
}

interface BookingDetail {
  id: number;
  externalBookingId: string;
  ownerName?: string;
  checkinDate: string;
  statoPrenotazione: string;
  grossAmount: number;
  otaCommissionAmount?: number;
  cleaningAmount?: number;
  pmFeeAmount?: number;
  ownerNetAmount: number;
  withholdingAmount?: number;
  touristTaxAmount?: number;
  touristTaxIncludedInGross?: boolean;
  settlementId?: number;
  splitEconomico: SplitEconomico;
  documenti: DocumentoBooking[];
  // Righe di booking_split_economico: assenti/vuote sulle prenotazioni pre-migrazione 018.
  righeSplit?: RigaSplit[];
  totalCostiPm?: number;
}

interface RigaSplit {
  tipoVoce: string;
  importo: number;
  includeInFatturaPm: boolean;
}

interface BookingListItem {
  id: number;
  statoPrenotazione: string;
}

interface DocumentoDetail {
  id: number;
  documentNumber: string;
  documentType: string;
  issueDate: string;
  totalAmount: number;
  imponibile?: number;
  ritenutaAmount: number;
  bolloAmount?: number;
  canoneLocazione?: number;
  fkBookingId: number;
}

interface LedgerRow {
  id: number;
  bookingId: number;
  canoneLocazione: number;
  ritenutaAmount: number;
  aliquotaRitenuta?: number;
  periodoMese: number;
  periodoAnno: number;
  fkF24RecordId?: number;
}

interface F24Detail {
  f24RecordId: number;
  periodoMese: number;
  periodoAnno: number;
  totaleRitenute: number;
  numeroRitenute: number;
  ritenute: LedgerRow[];
}

interface SettlementBooking {
  bookingId: number;
  externalBookingId: string;
  ownerNetAmount?: number;
  withholdingAmount?: number;
}

interface SettlementDetail {
  id: number;
  period: string;
  totalAmount: number;
  withholdingAmount: number;
  netAmount: number;
  bookings: SettlementBooking[];
}

interface CuRecord {
  id: number;
  ownerName: string;
  taxYear: number;
  totalCompensi: number;
  totalRitenute: number;
}

const isRicevuta = (tipo: string | undefined): boolean =>
  tipo === 'ricevuta' || tipo === 'ricevuta_owner';
const isFattura = (tipo: string | undefined): boolean =>
  tipo === 'fattura' || tipo === 'fattura_pm';

// ── dati caricati una volta sola ─────────────────────────────────────────────

let token = '';
let bookingId = 0;
let booking: BookingDetail | null = null;
let ricevutaDetail: DocumentoDetail | null = null;
let fatturaDetail: DocumentoDetail | null = null;
let ledgerRow: LedgerRow | null = null;
let f24: F24Detail | null = null;
let settlement: SettlementDetail | null = null;
let cu: CuRecord | null = null;

test.describe.serial(`Verifica importi booking ${bookingIdParam || '(auto)'}`, () => {
  test.beforeAll(async () => {
    token = await getToken(USERS.tenantAdmin.email, USERS.tenantAdmin.password);

    // 1. Prenotazione da verificare
    if (bookingIdParam > 0) {
      bookingId = bookingIdParam;
    } else {
      const lista = await apiGet<BookingListItem[]>(token, '/bookings');
      const candidato = (lista.body ?? []).find(
        b => b.statoPrenotazione === 'doc_issued' || b.statoPrenotazione === 'settled',
      );
      if (!candidato) {
        console.log('⏭  Nessun booking con documenti fiscali emessi trovato');
        return;
      }
      bookingId = candidato.id;
      console.log(`ℹ  Booking scelto automaticamente: ${bookingId} (${candidato.statoPrenotazione})`);
    }

    const bk = await apiGet<BookingDetail>(token, `/bookings/${bookingId}`);
    if (!bk.ok) {
      console.log(`⏭  Booking ${bookingId} non caricabile: HTTP ${bk.status}`);
      return;
    }
    booking = bk.body;

    // 2. Documenti fiscali della prenotazione: già elencati nel dettaglio booking,
    //    il dettaglio completo (canone, ritenuta) va letto per id.
    const docs = booking.documenti ?? [];
    const ricevutaRef = docs.find(d => isRicevuta(d.tipoDocumento));
    const fatturaRef = docs.find(d => isFattura(d.tipoDocumento));

    if (ricevutaRef) {
      const res = await apiGet<DocumentoDetail>(token, `/documents/${ricevutaRef.id}`);
      if (res.ok) ricevutaDetail = res.body;
    }
    if (fatturaRef) {
      const res = await apiGet<DocumentoDetail>(token, `/documents/${fatturaRef.id}`);
      if (res.ok) fatturaDetail = res.body;
    }

    // 3. Riga di withholding ledger. L'endpoint richiede anno e mese: il periodo è
    //    quello della data di emissione della ricevuta (è l'evento che genera la riga).
    const riferimento = ricevutaDetail?.issueDate ?? booking.checkinDate;
    if (riferimento) {
      const [anno, mese] = riferimento.split('-').map(Number);
      const res = await apiGet<LedgerRow[]>(token, `/withholding-ledger?anno=${anno}&mese=${mese}`);
      if (res.ok && Array.isArray(res.body)) {
        ledgerRow = res.body.find(l => l.bookingId === bookingId) ?? null;
      }
    }

    // 4. F24: collegato alla riga di ledger, non al documento.
    if (ledgerRow?.fkF24RecordId) {
      const res = await apiGet<F24Detail>(token, `/f24/${ledgerRow.fkF24RecordId}`);
      if (res.ok) f24 = res.body;
    }

    // 5. Liquidazione: l'id è sul booking, non sul documento.
    if (booking.settlementId) {
      const res = await apiGet<SettlementDetail>(token, `/settlements/${booking.settlementId}`);
      if (res.ok) settlement = res.body;
    }

    // 6. CU: nessun collegamento diretto al booking — si cerca per proprietario e anno.
    const annoCu = Number(booking.checkinDate?.slice(0, 4));
    const cuRes = await apiGet<CuRecord[]>(token, '/cu');
    if (cuRes.ok && Array.isArray(cuRes.body)) {
      cu = cuRes.body.find(c => c.ownerName === booking?.ownerName && c.taxYear === annoCu) ?? null;
    }

    console.log(
      `ℹ  Dati caricati — booking:${booking ? '✓' : '✗'} ricevuta:${ricevutaDetail ? '✓' : '—'} ` +
        `fattura:${fatturaDetail ? '✓' : '—'} ledger:${ledgerRow ? '✓' : '—'} ` +
        `f24:${f24 ? '✓' : '—'} settlement:${settlement ? '✓' : '—'} cu:${cu ? '✓' : '—'}`,
    );
  });

  test('V.1 — coerenza split booking', async () => {
    test.skip(!booking, 'Nessun booking con documenti fiscali emessi trovato');
    const b = booking!;

    const tassa = nz(b.touristTaxAmount);
    const tassaInclusa = b.touristTaxIncludedInGross ?? false;
    const baseCalcolo = tassaInclusa ? b.grossAmount - tassa : b.grossAmount;

    // Con righe split i costi PM sono total_costi_pm: comprende le voci extra in fattura, che
    // riducono il netto proprietario. Sui booking pre-migrazione 018 restano OTA+pulizie+PM.
    const conRigheSplit = (b.righeSplit?.length ?? 0) > 0;
    const costiPm = conRigheSplit
      ? nz(b.totalCostiPm)
      : nz(b.otaCommissionAmount) + nz(b.cleaningAmount) + nz(b.pmFeeAmount);
    const sommaVoci = costiPm + nz(b.ownerNetAmount);
    const atteso = tassaInclusa ? baseCalcolo : b.grossAmount;
    const delta = sommaVoci - atteso;

    console.log(
      `V.1 Split: gross=${b.grossAmount} tassa=${tassa} inclusa=${tassaInclusa} ` +
        `base=${baseCalcolo} ${conRigheSplit ? 'totalCostiPm' : 'OTA+pulizie+PM'}+netto=${sommaVoci.toFixed(2)} ` +
        `delta=${fmtDelta(delta)}`,
    );
    expect(approxEqual(sommaVoci, atteso)).toBeTruthy();

    // V.1b — righe booking_split_economico, solo se presenti (le prenotazioni create prima
    // della migration 018 non ne hanno): total_costi_pm deve coincidere con la somma delle
    // righe in fattura PM.
    if (b.righeSplit && b.righeSplit.length > 0) {
      const sommaRigheSplit = b.righeSplit
        .filter(r => r.includeInFatturaPm)
        .reduce((s, r) => s + r.importo, 0);
      const totalCostiPm = nz(b.totalCostiPm);
      console.log(
        `V.1b Split righe: ${b.righeSplit.length} righe, ` +
          `somma=${sommaRigheSplit.toFixed(2)}, totalCostiPm=${totalCostiPm} ` +
          `delta=${fmtDelta(totalCostiPm - sommaRigheSplit)}`,
      );
      expect(approxEqual(totalCostiPm, sommaRigheSplit)).toBeTruthy();
    } else {
      console.log('V.1b Split righe: nessuna riga split (booking pre-migrazione 018), verifica saltata');
    }
  });

  test('V.2 — coerenza ritenuta booking', async () => {
    test.skip(!booking, 'Nessun booking con documenti fiscali emessi trovato');
    const b = booking!;

    // L'aliquota è esposta solo dentro splitEconomico, non a livello di booking.
    const aliquota = b.splitEconomico?.aliquotaRitenuta;
    test.skip(aliquota == null, 'Aliquota ritenuta non disponibile sul booking');

    const ritenutaAttesa = (b.ownerNetAmount * aliquota!) / 100;
    const delta = nz(b.withholdingAmount) - ritenutaAttesa;

    console.log(
      `V.2 Ritenuta: netto=${b.ownerNetAmount} aliquota=${aliquota}% ` +
        `attesa=${ritenutaAttesa.toFixed(2)} db=${nz(b.withholdingAmount)} delta=${fmtDelta(delta)}`,
    );
    expect(approxEqual(nz(b.withholdingAmount), ritenutaAttesa)).toBeTruthy();
  });

  test('V.3 — ricevuta owner vs booking', async () => {
    test.skip(!booking || !ricevutaDetail, 'Nessuna ricevuta owner emessa per questo booking');
    const b = booking!;
    const r = ricevutaDetail!;

    const deltaCanone = nz(r.canoneLocazione) - b.ownerNetAmount;
    const deltaRitenuta = r.ritenutaAmount - nz(b.withholdingAmount);

    console.log(
      `V.3 Ricevuta ${r.documentNumber}: canone=${nz(r.canoneLocazione)} vs netto=${b.ownerNetAmount} ` +
        `delta=${fmtDelta(deltaCanone)} | ritenuta=${r.ritenutaAmount} vs ${nz(b.withholdingAmount)} ` +
        `delta=${fmtDelta(deltaRitenuta)}`,
    );
    expect(approxEqual(nz(r.canoneLocazione), b.ownerNetAmount)).toBeTruthy();
    expect(approxEqual(r.ritenutaAmount, nz(b.withholdingAmount))).toBeTruthy();
  });

  test('V.4 — fattura PM vs booking', async () => {
    test.skip(!booking || !fatturaDetail, 'Nessuna fattura PM emessa per questo booking');
    const b = booking!;
    const f = fatturaDetail!;

    // Con righe split la fattura è Σ righe in fattura PM = total_costi_pm (voci extra comprese);
    // sui booking pre-migrazione 018 restano OTA+pulizie+PM.
    const conRigheSplit = (b.righeSplit?.length ?? 0) > 0;
    const serviziPm = conRigheSplit
      ? nz(b.totalCostiPm)
      : nz(b.otaCommissionAmount) + nz(b.cleaningAmount) + nz(b.pmFeeAmount);
    const delta = f.totalAmount - serviziPm;

    console.log(
      `V.4 Fattura ${f.documentNumber}: totale=${f.totalAmount} vs servizi PM=${serviziPm.toFixed(2)} ` +
        (conRigheSplit
          ? `(totalCostiPm, ${b.righeSplit!.length} righe split) `
          : `(OTA ${nz(b.otaCommissionAmount)} + pulizie ${nz(b.cleaningAmount)} + PM ${nz(b.pmFeeAmount)}) `) +
        `delta=${fmtDelta(delta)}`,
    );
    expect(approxEqual(f.totalAmount, serviziPm)).toBeTruthy();
  });

  test('V.5 — withholding ledger vs ricevuta', async () => {
    test.skip(!ricevutaDetail, 'Nessuna ricevuta owner emessa per questo booking');
    test.skip(!ledgerRow, 'Nessuna riga ledger per questo booking');
    const r = ricevutaDetail!;
    const l = ledgerRow!;

    const deltaCanone = l.canoneLocazione - nz(r.canoneLocazione);
    const deltaRitenuta = l.ritenutaAmount - r.ritenutaAmount;

    console.log(
      `V.5 Ledger ${l.id} (periodo ${l.periodoMese}/${l.periodoAnno}): ` +
        `canone=${l.canoneLocazione} vs doc=${nz(r.canoneLocazione)} delta=${fmtDelta(deltaCanone)} | ` +
        `ritenuta=${l.ritenutaAmount} vs doc=${r.ritenutaAmount} delta=${fmtDelta(deltaRitenuta)}`,
    );
    expect(approxEqual(l.canoneLocazione, nz(r.canoneLocazione))).toBeTruthy();
    expect(approxEqual(l.ritenutaAmount, r.ritenutaAmount)).toBeTruthy();
  });

  test('V.6 — F24 vs somma ritenute del periodo', async () => {
    test.skip(!f24, 'Nessun F24 collegato a questo booking');
    const f = f24!;

    const sommaRitenute = (f.ritenute ?? []).reduce((s, r) => s + nz(r.ritenutaAmount), 0);
    const delta = f.totaleRitenute - sommaRitenute;
    const rigaF24 = (f.ritenute ?? []).find(r => r.bookingId === bookingId);

    console.log(
      `V.6 F24 ${f.f24RecordId} (${f.periodoMese}/${f.periodoAnno}): totale=${f.totaleRitenute} ` +
        `vs somma righe=${sommaRitenute.toFixed(2)} delta=${fmtDelta(delta)} | ` +
        `righe=${f.ritenute?.length ?? 0} | booking ${bookingId} presente=${rigaF24 ? 'sì' : 'NO'}`,
    );
    expect(approxEqual(f.totaleRitenute, sommaRitenute)).toBeTruthy();
    expect(rigaF24).toBeDefined();
  });

  test('V.7 — liquidazione vs somma canoni e ritenute', async () => {
    test.skip(!settlement, 'Nessuna liquidazione collegata');
    const s = settlement!;
    const b = booking!;

    const righe = s.bookings ?? [];
    const sommaCanoni = righe.reduce((acc, r) => acc + nz(r.ownerNetAmount), 0);
    const sommaRitenute = righe.reduce((acc, r) => acc + nz(r.withholdingAmount), 0);

    const deltaLordo = s.totalAmount - sommaCanoni;
    const deltaRitenuta = s.withholdingAmount - sommaRitenute;
    const deltaNetto = s.netAmount - (sommaCanoni - sommaRitenute);
    const bkInSettlement = righe.find(
      r => r.bookingId === bookingId || r.externalBookingId === b.externalBookingId,
    );

    console.log(
      `V.7 Liquidazione ${s.id} (${s.period}): lordo=${s.totalAmount} vs Σcanoni=${sommaCanoni.toFixed(2)} ` +
        `delta=${fmtDelta(deltaLordo)} | ritenuta=${s.withholdingAmount} vs Σ=${sommaRitenute.toFixed(2)} ` +
        `delta=${fmtDelta(deltaRitenuta)} | netto=${s.netAmount} delta=${fmtDelta(deltaNetto)} | ` +
        `booking ${bookingId} presente=${bkInSettlement ? 'sì' : 'NO'}`,
    );
    expect(approxEqual(s.totalAmount, sommaCanoni)).toBeTruthy();
    expect(approxEqual(s.withholdingAmount, sommaRitenute)).toBeTruthy();
    expect(approxEqual(s.netAmount, sommaCanoni - sommaRitenute)).toBeTruthy();
    expect(bkInSettlement).toBeDefined();
  });

  test('V.8 — CU vs compensi e ritenute dell anno', async () => {
    test.skip(!cu, 'Nessuna CU collegata a questo booking');
    const c = cu!;
    const b = booking!;

    // La CU aggrega tutti i booking dell'anno del proprietario: i suoi totali non
    // possono essere inferiori a quelli del singolo booking.
    console.log(
      `V.8 CU ${c.id} (${c.ownerName}, ${c.taxYear}): compensi=${c.totalCompensi} ` +
        `>= netto booking ${b.ownerNetAmount} | ritenute=${c.totalRitenute} ` +
        `>= ritenuta booking ${nz(b.withholdingAmount)}`,
    );
    expect(c.totalCompensi).toBeGreaterThanOrEqual(b.ownerNetAmount - EPS);
    expect(c.totalRitenute).toBeGreaterThanOrEqual(nz(b.withholdingAmount) - EPS);
  });
});

import { test, expect, Page } from '@playwright/test';
import { login, USERS } from './helpers/auth';
import { getToken, apiGet, apiPatch, apiDelete } from './helpers/api';

/**
 * Fase 04 — Documenti fiscali: emissione ricevuta owner e fattura PM su un booking
 * esistente, verifica degli importi, download dei PDF e stato SDI.
 *
 * Non crea prenotazioni: usa la prima in stato 'ready' senza documenti. L'afterAll
 * rimuove i documenti emessi e riporta il booking a 'ready', così la suite è ripetibile.
 *
 * L'ordine di emissione è ricevuta → fattura, come da specifica: in quest'ordine il
 * canone della ricevuta è l'owner_net_amount del booking. Emettendo prima la fattura,
 * DocumentGenerationService calcolerebbe invece canone = lordo − totale fattura.
 *
 * Il 4.11 verifica che il pulsante "Invia SDI" sia disponibile ma NON lo clicca:
 * l'invio consuma un valore di sdi_progressivo, che è un contatore fiscale e non si
 * riavvolge.
 */

interface BookingListItem {
  id: number;
  externalBookingId: string;
  statoPrenotazione: string;
}

interface Guest {
  guestName?: string | null;
  guestTaxCode?: string | null;
  guestBirthDate?: string | null;
  guestSesso?: string | null;
  guestBirthPlace?: string | null;
  guestBirthBelfiore?: string | null;
  guestDocType?: string | null;
  guestDocNumber?: string | null;
  guestCountry?: string | null;
  guestAddress?: string | null;
  guestPhone?: string | null;
}

interface BookingDetail extends Guest {
  id: number;
  externalBookingId: string;
  grossAmount: number;
  otaCommissionAmount: number;
  cleaningAmount: number;
  pmFeeAmount: number;
  ownerNetAmount: number;
  withholdingAmount: number;
}

/** CF di comodo per l'ospite del booking di test: la fattura PM lo esige. */
const CF_OSPITE_TEST = 'SMTJHN80A01Z404X';

const CAMPI_GUEST: (keyof Guest)[] = [
  'guestName', 'guestTaxCode', 'guestBirthDate', 'guestSesso', 'guestBirthPlace',
  'guestBirthBelfiore', 'guestDocType', 'guestDocNumber', 'guestCountry',
  'guestAddress', 'guestPhone',
];

const soloCampiGuest = (b: Guest): Guest =>
  Object.fromEntries(CAMPI_GUEST.map(k => [k, b[k] ?? null])) as Guest;

interface DocumentListItem {
  id: number;
  documentNumber: string;
  documentType: string;
  fkBookingId: number;
}

interface DocumentDetail extends DocumentListItem {
  totalAmount: number;
  vatAmount: number;
  imponibile: number;
  ritenutaAmount: number;
  canoneLocazione: number | null;
}

/** Confronto su importi in euro: tollera lo scarto di arrotondamento al centesimo. */
const vicino = (a: number, b: number) => Math.abs(a - b) < 0.011;

let token: string;
let booking: BookingDetail;
let documentiEmessi = false;
/** Anagrafica ospite originale, ripristinata dall'afterAll se il test l'ha modificata. */
let guestOriginale: Guest | null = null;

test.describe.configure({ mode: 'serial' });

test.describe('Fase 04 — Documenti Fiscali', () => {

  test.beforeAll(async () => {
    token = await getToken(USERS.tenantAdmin.email, USERS.tenantAdmin.password);

    const lista = await apiGet<BookingListItem[]>(token, '/bookings');
    expect(lista.ok, 'GET /bookings deve rispondere 200').toBeTruthy();

    // Le SEED- sono prenotazioni preparate per casi limite (es. servizi PM a zero,
    // usata dal 4.13): non servono al flusso normale di emissione.
    const candidati = lista.body.filter(
      b => b.statoPrenotazione === 'ready' && !b.externalBookingId.startsWith('SEED-'));
    expect(candidati.length, "serve almeno una prenotazione in stato 'ready'").toBeGreaterThan(0);

    // Fra le 'ready' si prende la prima senza documenti già emessi.
    const documenti = await apiGet<DocumentListItem[]>(token, '/documents');
    const conDocumenti = new Set((documenti.body ?? []).map(d => d.fkBookingId));
    const scelto = candidati.find(b => !conDocumenti.has(b.id));
    expect(scelto, "serve una prenotazione 'ready' senza documenti emessi").toBeTruthy();

    const dettaglio = await apiGet<BookingDetail>(token, `/bookings/${scelto!.id}`);
    booking = dettaglio.body;
    console.log(`booking di test: ${booking.id} (${booking.externalBookingId}) — `
      + `lordo ${booking.grossAmount}, ownerNet ${booking.ownerNetAmount}, `
      + `ritenuta ${booking.withholdingAmount}`);

    // La fattura PM esige il CF dell'ospite: i booking 'ready' dei dati di sviluppo ne
    // sono privi, quindi lo si valorizza qui e lo si ripristina nell'afterAll.
    // NB: PATCH /guest riscrive TUTTI i campi guest, va inviata l'anagrafica completa,
    // altrimenti guest_name (NOT NULL) andrebbe a null.
    if (!booking.guestTaxCode || booking.guestTaxCode.trim() === '') {
      guestOriginale = soloCampiGuest(booking);
      const res = await apiPatch(token, `/bookings/${booking.id}/guest`,
        { ...guestOriginale, guestTaxCode: CF_OSPITE_TEST });
      expect(res.ok, `PATCH guest fallita: ${JSON.stringify(res.body)}`).toBeTruthy();
      booking.guestTaxCode = CF_OSPITE_TEST;
      console.log(`CF ospite valorizzato per il test: ${CF_OSPITE_TEST}`);
    }
  });

  test.afterAll(async () => {
    if (documentiEmessi) {
      const res = await apiDelete(token, '/test/cleanup-documenti', { bookingId: booking.id });
      console.log(`cleanup documenti booking ${booking.id}: HTTP ${res.status} ${JSON.stringify(res.body)}`);
    }
    if (guestOriginale) {
      const res = await apiPatch(token, `/bookings/${booking.id}/guest`, guestOriginale);
      console.log(`ripristino anagrafica ospite booking ${booking.id}: HTTP ${res.status}`);
    }
  });

  /** Card documento nella griglia in cima al dettaglio booking. */
  const cardDocumento = (page: Page, etichetta: 'Fattura PM' | 'Ricevuta Owner') =>
    page.locator('div').filter({ hasText: new RegExp(`^${etichetta}`) }).last();

  test('4.1 — Login come tenant_admin', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('4.2 — Dettaglio booking con split e documenti da emettere', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${booking.id}`);

    await expect(page.getByText('Split Economico')).toBeVisible();
    await expect(page.getByText('Provvigione PM')).toBeVisible();

    // Nessun documento emesso: entrambe le card mostrano "Da emettere"
    await expect(page.getByText('Da emettere')).toHaveCount(2);
  });

  test('4.3 — Emetti ricevuta owner', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${booking.id}`);

    await page.getByRole('button', { name: 'Emetti Ricevuta' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    // Il dialog espone proprietario, canone e ritenuta calcolati sul booking
    await expect(dialog.getByText(booking.ownerNetAmount.toLocaleString('it-IT',
      { minimumFractionDigits: 2 })).first()).toBeVisible();
    await expect(dialog.getByText(/Ritenuta/i).first()).toBeVisible();

    await dialog.getByRole('button', { name: 'Emetti Documento' }).click();
    documentiEmessi = true;

    await expect(page.getByText(/Ricevuta emessa|Documento emesso|emessa/i).first())
      .toBeVisible({ timeout: 10000 });
  });

  test('4.4 — Ricevuta emessa e numerata', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${booking.id}`);

    // Una sola card resta "Da emettere": la fattura PM
    await expect(page.getByText('Da emettere')).toHaveCount(1);
    await expect(page.getByText(/^RIC-\d{4}-\d{4}$/)).toBeVisible();
  });

  test('4.5 — Emetti fattura PM', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${booking.id}`);

    await page.getByRole('button', { name: 'Emetti Fattura PM' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    // "Imponibile" compare sia come intestazione di colonna sia nel riepilogo
    await expect(dialog.getByText('Imponibile').first()).toBeVisible();
    await expect(dialog.getByText(/IVA/).first()).toBeVisible();

    await dialog.getByRole('button', { name: 'Emetti Documento' }).click();

    await expect(page.getByText(/Fattura emessa|Documento emesso|emessa/i).first())
      .toBeVisible({ timeout: 10000 });
  });

  test('4.6 — Fattura emessa e booking a doc_issued', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${booking.id}`);

    await expect(page.getByText('Da emettere')).toHaveCount(0);
    await expect(page.getByText(/^FT-\d{4}-\d{4}$/)).toBeVisible();
    // Il badge in intestazione riporta il codice grezzo dello stato, non l'etichetta
    await expect(page.getByText('doc_issued')).toBeVisible();
  });

  test('4.7 — Importi della ricevuta', async ({ page }) => {
    await login(page, 'tenantAdmin');
    const doc = await trovaDocumento('ricevuta');

    await page.goto(`/documents/${doc.id}`);
    await expect(page.getByText('Importi Fiscali')).toBeVisible();
    await expect(page.getByText('Canone lordo')).toBeVisible();
    await expect(page.getByText(/Ritenuta \d+%/)).toBeVisible();
    // exact: la nota informativa sul bollo cita la stessa espressione
    await expect(page.getByText('Netto a pagare', { exact: true })).toBeVisible();

    expect(vicino(doc.canoneLocazione ?? 0, booking.ownerNetAmount)).toBeTruthy();
    expect(doc.ritenutaAmount).toBeGreaterThan(0);
  });

  test('4.8 — Importi della fattura', async ({ page }) => {
    await login(page, 'tenantAdmin');
    const doc = await trovaDocumento('fattura');

    await page.goto(`/documents/${doc.id}`);
    await expect(page.getByText('Importi Fiscali')).toBeVisible();
    // .first(): "Imponibile" è anche intestazione della tabella delle righe
    await expect(page.getByText('Imponibile').first()).toBeVisible();
    await expect(page.getByText('Totale documento').first()).toBeVisible();

    expect(doc.totalAmount).toBeGreaterThan(0);
    expect(doc.vatAmount).toBeGreaterThan(0);
    // L'IVA è scorporata dal lordo dei servizi: imponibile = totale / 1.22
    expect(vicino(doc.imponibile, doc.totalAmount / 1.22)).toBeTruthy();
  });

  test('4.9 — Download PDF ricevuta', async ({ page }) => {
    await login(page, 'tenantAdmin');
    const doc = await trovaDocumento('ricevuta');
    await page.goto(`/documents/${doc.id}`);

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Scarica PDF' }).click();
    const file = await download;
    expect(file.suggestedFilename()).toContain('RIC-');
  });

  test('4.10 — Download PDF fattura', async ({ page }) => {
    await login(page, 'tenantAdmin');
    const doc = await trovaDocumento('fattura');
    await page.goto(`/documents/${doc.id}`);

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Scarica PDF' }).click();
    const file = await download;
    expect(file.suggestedFilename()).toContain('FT-');
  });

  test('4.11 — Invio SDI disponibile sulla fattura', async ({ page }) => {
    await login(page, 'tenantAdmin');
    const doc = await trovaDocumento('fattura');
    await page.goto(`/documents/${doc.id}`);

    // sdi_auto_send = false: la fattura non è ancora partita e il pulsante è offerto.
    // Non lo clicchiamo: l'invio consuma un valore di sdi_progressivo.
    const inviaSdi = page.getByRole('button', { name: 'Invia SDI' });
    await expect(inviaSdi).toBeVisible();
    await expect(inviaSdi).toBeEnabled();

    // La ricevuta owner invece non va allo SDI
    const ricevuta = await trovaDocumento('ricevuta');
    await page.goto(`/documents/${ricevuta.id}`);
    await expect(page.getByRole('button', { name: 'Invia SDI' })).toHaveCount(0);
  });

  test('4.12 — Coerenza degli importi via API', async () => {
    const ricevuta = await trovaDocumento('ricevuta');
    const fattura = await trovaDocumento('fattura');

    // Ricevuta: canone e ritenuta arrivano dal booking
    expect(vicino(ricevuta.canoneLocazione ?? 0, booking.ownerNetAmount)).toBeTruthy();
    expect(vicino(ricevuta.ritenutaAmount, booking.withholdingAmount)).toBeTruthy();

    // Fattura: i servizi sono già lordi IVA inclusa, il totale è la loro somma
    const lordoServizi = booking.otaCommissionAmount + booking.cleaningAmount + booking.pmFeeAmount;
    expect(vicino(fattura.totalAmount, lordoServizi)).toBeTruthy();

    const imponibileAtteso = Math.round((fattura.totalAmount / 1.22) * 100) / 100;
    expect(vicino(fattura.imponibile, imponibileAtteso)).toBeTruthy();
    expect(vicino(fattura.vatAmount, fattura.totalAmount - imponibileAtteso)).toBeTruthy();
  });

  test('4.13 — Fattura PM bloccata se i servizi PM sono a zero', async ({ page }) => {
    // La lista prenotazioni NON espone gli importi dei servizi (BookingListDTO ha solo
    // lordo e netto proprietario): la selezione passa dai dettagli.
    const lista = await apiGet<BookingListItem[]>(token, '/bookings');
    let senzaServizi: BookingDetail | undefined;
    for (const riga of (lista.body ?? []).filter(b => b.statoPrenotazione === 'ready')) {
      const d = (await apiGet<BookingDetail>(token, `/bookings/${riga.id}`)).body;
      if (d.otaCommissionAmount === 0 && d.cleaningAmount === 0 && d.pmFeeAmount === 0) {
        senzaServizi = d;
        break;
      }
    }
    test.skip(!senzaServizi, 'nessuna prenotazione con i servizi PM a zero');
    console.log(`booking senza servizi PM: ${senzaServizi!.id} (${senzaServizi!.externalBookingId})`);

    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${senzaServizi!.id}`);

    await page.getByRole('button', { name: 'Emetti Fattura PM' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    // Avviso preventivo nel dialog, con l'indicazione di dove guardare
    await expect(dialog.getByText(/servizi PM risultano tutti a zero/i)).toBeVisible();
    await expect(dialog.getByText(/regole contratto/i)).toBeVisible();

    // La difesa vera è il server: l'emissione viene rifiutata con 422
    // .first(): il toast è replicato in un <span role="status"> per gli screen reader
    await dialog.getByRole('button', { name: 'Emetti Documento' }).click();
    await expect(page.getByText('Errore generazione documento').first())
      .toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/nessun servizio PM calcolato/i).first()).toBeVisible();

    // Nessun documento creato per questa prenotazione
    const documenti = await apiGet<DocumentListItem[]>(token, '/documents');
    expect((documenti.body ?? []).filter(d => d.fkBookingId === senzaServizi!.id)).toHaveLength(0);
  });

  /**
   * Documento del booking di test, per tipo, letto dall'API.
   * NB: il tipo salvato è quello della lookup tipo_documento ('ricevuta' / 'fattura');
   * 'ricevuta_owner' e 'fattura_pm' sono solo i nomi della richiesta di generazione.
   */
  async function trovaDocumento(tipo: 'ricevuta' | 'fattura'): Promise<DocumentDetail> {
    const lista = await apiGet<DocumentListItem[]>(token, '/documents');
    const riga = (lista.body ?? [])
      .filter(d => d.fkBookingId === booking.id)
      .find(d => d.documentType === tipo);
    expect(riga, `documento ${tipo} del booking ${booking.id} non trovato`).toBeTruthy();
    const dettaglio = await apiGet<DocumentDetail>(token, `/documents/${riga!.id}`);
    return dettaglio.body;
  }

});

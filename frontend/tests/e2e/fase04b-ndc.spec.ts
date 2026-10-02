import { test, expect } from '@playwright/test';
import { login, USERS } from './helpers/auth';
import { getToken, apiGet, apiPost, apiDelete } from './helpers/api';

/**
 * Fase 04b — Note di credito (NDC): storno totale della fattura PM, stato 'stornata',
 * righe NDC nello split, blocco dei documenti, copia della prenotazione e annullamento.
 *
 * Non usa prenotazioni esistenti: una NDC su un booking reale annullerebbe la ricevuta,
 * stornerebbe la ritenuta (che all'annullamento torna 'da_versare' fuori dal suo F24) e, con la
 * fattura già allo SDI, trasmetterebbe un TD04 non più annullabile. Il beforeAll crea quindi
 * un booking E2E-NDC-<timestamp> con fattura PM e ricevuta owner; l'afterAll lo elimina con
 * /test/cleanup-bookings (documenti, NDC e ritenute compresi) e cancella l'eventuale copia.
 *
 * Documenti datati in un mese passato (DATA_EMISSIONE): la ritenuta non finisce nel periodo
 * usato da F24 e liquidazioni nelle fasi 05/06.
 */

interface BookingDetail {
  id: number;
  externalBookingId: string;
  statoPrenotazione: string;
  documenti: { id: number; documentNumber: string; tipoDocumento: string; statoDocumento: string }[];
}

const DATA_EMISSIONE = '2026-03-15';
const EXTERNAL_ID = `E2E-NDC-${Date.now()}`;

let token: string;
let bookingId: number;
let fatturaId: number;
let ndcId: number | null = null;
let ndcNumero: string | null = null;
let ndcAnnullata = false;
let copiaBookingId: number | null = null;

test.describe.configure({ mode: 'serial' });

test.describe('Fase 04b — Nota di Credito', () => {

  test.beforeAll(async () => {
    token = await getToken(USERS.tenantAdmin.email, USERS.tenantAdmin.password);

    // Con l'invio SDI automatico la fattura (e poi il TD04) andrebbero allo SDI: il
    // progressivo è un contatore fiscale che non si riavvolge, il test non lo consuma.
    const settings = await apiGet<{ sdiAutoSend: boolean }>(token, '/settings');
    test.skip(settings.body?.sdiAutoSend === true,
      'sdi_auto_send attivo: il test emetterebbe documenti verso lo SDI');

    // Immobile con regole di contratto (servizi PM > 0, altrimenti la fattura è bloccata)
    const immobili = await apiGet<{ id: number }[]>(token, '/properties');
    let propertyId: number | null = null;
    for (const p of immobili.body ?? []) {
      const regole = await apiGet<{ tipo: string }[]>(token, `/properties/${p.id}/contracts`);
      if ((regole.body ?? []).some(r => r.tipo === 'commissione_pm' || r.tipo === 'pulizie')) {
        propertyId = p.id;
        break;
      }
    }
    test.skip(propertyId === null, 'Nessun immobile con regole di contratto');

    const creato = await apiPost<BookingDetail>(token, '/bookings', {
      fkPropertyId: propertyId,
      externalBookingId: EXTERNAL_ID,
      checkinDate: '2026-03-10',
      checkoutDate: '2026-03-13',
      guests: 2,
      grossAmount: 500,
      guestName: 'Rossi E2E Ndc',
      guestTaxCode: 'RSSNDC80A01H501Z',
    });
    expect(creato.ok, `creazione booking fallita: ${JSON.stringify(creato.body)}`).toBeTruthy();
    bookingId = creato.body.id;

    for (const tipoDocumento of ['ricevuta_owner', 'fattura_pm']) {
      const res = await apiPost<{ documentId: number }>(token, '/documents/generate',
        { bookingId, tipoDocumento, dataEmissione: DATA_EMISSIONE });
      expect(res.ok, `emissione ${tipoDocumento} fallita: ${JSON.stringify(res.body)}`).toBeTruthy();
      if (tipoDocumento === 'fattura_pm') fatturaId = res.body.documentId;
    }
    const dettaglio = await apiGet<BookingDetail>(token, `/bookings/${bookingId}`);
    test.skip(dettaglio.body.statoPrenotazione !== 'doc_issued', 'Nessun booking con fattura PM emessa');
    console.log(`booking di test: ${bookingId} (${EXTERNAL_ID}) — fattura ${fatturaId}`);
  });

  test.afterAll(async () => {
    // NDC ancora attiva: si annulla (ripristina ricevuta, ritenuta e stato booking)
    if (ndcId && !ndcAnnullata) {
      if (copiaBookingId) {
        await apiDelete(token, `/bookings/${copiaBookingId}`);
        copiaBookingId = null;
      }
      const res = await apiDelete(token, `/ndc/${ndcId}`);
      console.log(`annullamento NDC ${ndcId}: HTTP ${res.status}`);
    }
    if (copiaBookingId) {
      const res = await apiDelete(token, `/bookings/${copiaBookingId}`);
      console.log(`cleanup copia ${copiaBookingId}: HTTP ${res.status}`);
    }
    // Booking di test con documenti, NDC (righe in cascata) e ritenute
    const res = await apiDelete(token, '/test/cleanup-bookings', { externalIdPattern: EXTERNAL_ID });
    console.log(`cleanup booking ${EXTERNAL_ID}: HTTP ${res.status} ${JSON.stringify(res.body)}`);
  });

  test('N.1 — Emetti NDC totale', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${bookingId}`);

    await page.getByRole('button', { name: /emetti nota di credito/i }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();

    // NDC sempre totale: tutte le voci selezionate e non modificabili
    const checkboxes = dialog.getByRole('checkbox');
    const count = await checkboxes.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i++) {
      await expect(checkboxes.nth(i)).toBeChecked();
      await expect(checkboxes.nth(i)).toBeDisabled();
    }

    await dialog.getByRole('button', { name: /emetti ndc/i }).click();
    await expect(dialog).not.toBeVisible();

    // Il numero compare nella card della fattura ("Stornata da …") e nelle righe dello split
    await expect(page.getByText(/NC-\d{4}-\d{4}/).first()).toBeVisible();

    const dettaglio = await apiGet<BookingDetail>(token, `/bookings/${bookingId}`);
    const ndc = dettaglio.body.documenti.find(d => d.tipoDocumento === 'nota_credito');
    expect(ndc, 'NDC non trovata tra i documenti del booking').toBeTruthy();
    ndcId = ndc!.id;
    ndcNumero = ndc!.documentNumber;
    console.log(`NDC emessa: ${ndcNumero} (id ${ndcId})`);
  });

  test('N.2 — Stato booking stornata', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${bookingId}`);
    // exact: "Stornata da NC-…" nella card fattura conterrebbe la stessa parola
    await expect(page.getByText('Stornata', { exact: true }).first()).toBeVisible();

    await page.goto('/bookings');
    await expect(page.getByRole('row')
      .filter({ hasText: EXTERNAL_ID })
      .filter({ hasText: 'Stornata' })).toBeVisible();
  });

  test('N.3 — NDC in lista documenti', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/documents');
    await page.getByPlaceholder(/cerca/i).fill(ndcNumero!);

    const riga = page.getByRole('row').filter({ hasText: ndcNumero! });
    await expect(riga).toBeVisible();
    await expect(riga.getByText('NDC', { exact: true })).toBeVisible();
  });

  test('N.4 — Righe NDC nello split', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${bookingId}`);

    // Una riga per voce stornata, sottotitolo "Storno NDC NC-…"
    await expect(page.getByText(/^Storno NDC/).first()).toBeVisible();
    // Importi delle righe NDC con segno + (riducono i costi PM)
    await expect(page.getByText(/^\+€/).first()).toBeVisible();
  });

  test('N.5 — Blocco emissione documenti', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${bookingId}`);

    // Ricevuta già emessa (ora annullata): nessun pulsante "Emetti ricevuta" attivo
    const btnRicevuta = page.getByRole('button', { name: /emetti ricevuta/i });
    if (await btnRicevuta.isVisible()) {
      await expect(btnRicevuta).toBeDisabled();
    }

    // Il backend rifiuta comunque l'emissione su un booking stornato
    const res = await apiPost<{ message?: string }>(token, '/documents/generate',
      { bookingId, tipoDocumento: 'ricevuta_owner' });
    expect(res.status).toBe(422);
    expect(res.body.message).toContain('stornata');
  });

  test('N.6 — Copia booking stornato', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/bookings/${bookingId}`);

    await page.getByRole('button', { name: /copia prenotazione/i }).click();
    // L'URL corrente corrisponde già a /bookings/<id>: si aspetta un id diverso
    await page.waitForURL(url => {
      const m = url.pathname.match(/\/bookings\/(\d+)$/);
      return !!m && Number(m[1]) !== bookingId;
    });
    const nuovoId = parseInt(new URL(page.url()).pathname.split('/').pop() ?? '0');
    expect(nuovoId).toBeGreaterThan(0);
    expect(nuovoId).not.toBe(bookingId);
    copiaBookingId = nuovoId;

    await expect(page.getByText(/copiata da/i)).toBeVisible();

    await page.goto(`/bookings/${bookingId}`);
    await expect(page.getByText(/copiata in/i)).toBeVisible();
  });

  test('N.7 — Annullamento NDC', async ({ page }) => {
    // Con una copia attiva l'annullamento è bloccato: la copia va eliminata prima
    if (copiaBookingId) {
      const res = await apiDelete(token, `/bookings/${copiaBookingId}`);
      expect(res.ok, `eliminazione copia fallita: ${JSON.stringify(res.body)}`).toBeTruthy();
      copiaBookingId = null;
    }

    await login(page, 'tenantAdmin');
    await page.goto('/documents');
    await page.getByPlaceholder(/cerca/i).fill(ndcNumero!);

    // La conferma è un window.confirm() del browser, non un dialog della pagina
    page.once('dialog', d => d.accept());
    await page.getByRole('row').filter({ hasText: ndcNumero! })
      .getByRole('button', { name: /annulla/i }).click();
    await expect(page.getByText('Nota di credito annullata', { exact: true })).toBeVisible();
    ndcAnnullata = true;

    // Fattura e ricevuta di nuovo valide: il booking torna 'doc_issued'
    await page.goto(`/bookings/${bookingId}`);
    await expect(page.getByText('Doc. emesso', { exact: true })).toBeVisible();
    await expect(page.getByText(/FT-\d{4}-\d{4}/).first()).toBeVisible();
  });
});

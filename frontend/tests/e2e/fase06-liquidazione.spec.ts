import { test, expect, Page } from '@playwright/test';
import { login, USERS } from './helpers/auth';
import { getToken, apiGet, apiPost, apiPatch, apiDelete } from './helpers/api';

/**
 * Fase 06 — Liquidazione: calcolo del settlement, verifica degli importi contro il
 * registro ritenute, dettaglio, PDF del rendiconto, approvazione, pagamento, rollover
 * degli arretrati, idempotenza del ricalcolo e filtro di stato.
 *
 * Il periodo di lavoro è 2026-10, futuro e libero: nessun owner ha ritenute o arretrati
 * lì, quindi "Calcola liquidazioni" tocca soltanto la liquidazione di questo test e i
 * settlement reali degli altri periodi restano intatti.
 *
 * Il beforeAll si fabbrica il dato di partenza — nel DB non esiste un owner con ritenute
 * e senza settlement — emettendo su una prenotazione 'ready' la ricevuta owner e la
 * fattura PM datate nel periodo di test. Servono ENTRAMBI i documenti: il booking passa a
 * 'doc_issued' solo quando ci sono tutti e due, e l'elenco "da liquidare" che alimenta il
 * banner filtra proprio su quello stato.
 *
 * L'ordine ricevuta → fattura è quello della fase 04: così il canone della ricevuta è
 * l'owner_net_amount del booking.
 *
 * L'afterAll smonta tutto in ordine inverso — settlement, poi documenti e ritenuta, poi
 * anagrafica ospite — così la suite è ripetibile.
 */

const ANNO = 2026;
const MESE = 10;
const PERIOD = `${ANNO}-${String(MESE).padStart(2, '0')}`;
const MESE_NOME = 'Ottobre';
const DATA_EMISSIONE = `${PERIOD}-01`;

/** CF di comodo per l'ospite: la fattura PM lo esige. */
const CF_OSPITE_TEST = 'SMTJHN80A01Z404X';

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

const CAMPI_GUEST: (keyof Guest)[] = [
  'guestName', 'guestTaxCode', 'guestBirthDate', 'guestSesso', 'guestBirthPlace',
  'guestBirthBelfiore', 'guestDocType', 'guestDocNumber', 'guestCountry',
  'guestAddress', 'guestPhone',
];

const soloCampiGuest = (b: Guest): Guest =>
  Object.fromEntries(CAMPI_GUEST.map(k => [k, b[k] ?? null])) as Guest;

interface BookingListItem extends Guest {
  id: number;
  externalBookingId: string;
  statoPrenotazione: string;
  ownerName: string;
  fkOwnerId: number;
}

interface SettlementListItem {
  id: number;
  ownerName: string;
  period: string;
  totalAmount: number;
  withholdingAmount: number;
  netAmount: number;
  bookingsCount: number;
  stato: string;
}

interface SettlementBookingItem {
  bookingId: number;
  externalBookingId: string;
  grossAmount: number;
  ownerNetAmount: number;
  withholdingAmount: number;
  /** Competenza della ritenuta ("MM/yyyy") se la prenotazione è entrata come arretrato. */
  periodoLedger?: string | null;
}

interface SettlementDetail extends SettlementListItem {
  fkOwnerId: number;
  bookings: SettlementBookingItem[];
}

interface RitenutaRow {
  id: number;
  ownerName: string | null;
  bookingExternalId: string | null;
  canoneLocazione: number;
  ritenutaAmount: number;
}

interface BookingDaLiquidare {
  bookingId: number;
  externalBookingId: string;
  ownerName: string;
  periodoLedger: string;
}

interface CalcolaResult {
  generated: number;
  updated: number;
  skipped: number;
}

/** Confronto su importi in euro: tollera lo scarto di arrotondamento al centesimo. */
const vicino = (a: number, b: number) => Math.abs(a - b) < 0.011;

/** Stesso formato delle pagine liquidazioni. */
const euro = (v: number) => `€${v.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

let token: string;
let booking: BookingListItem;
let ownerName: string;
let settlementId: number;
let settlementCreato = false;
/** Anagrafica ospite originale, ripristinata dall'afterAll se il test l'ha modificata. */
let guestOriginale: Guest | null = null;

test.describe.configure({ mode: 'serial' });

test.describe('Fase 06 — Liquidazione', () => {

  test.beforeAll(async () => {
    token = await getToken(USERS.tenantAdmin.email, USERS.tenantAdmin.password);

    // 1. Residui di una run interrotta: la liquidazione del periodo di test va rimossa
    //    prima, altrimenti il calcolo la aggiornerebbe invece di crearla.
    const settlements = await apiGet<SettlementListItem[]>(token, '/settlements');
    expect(settlements.ok, 'GET /settlements deve rispondere 200').toBeTruthy();
    for (const s of settlements.body.filter(x => x.period === PERIOD)) {
      const res = await apiDelete(token, '/test/cleanup-settlement',
        { settlementId: s.id, forzaSePagato: true });
      console.log(`residuo: rimossa liquidazione ${s.id} di ${PERIOD} — HTTP ${res.status}`);
    }

    // 2. Prenotazione di lavoro: 'ready' e di un owner senza liquidazioni nel periodo.
    const bookings = await apiGet<BookingListItem[]>(token, '/bookings');
    const ownerConSettlement = new Set(
      settlements.body.filter(s => s.period === PERIOD).map(s => s.ownerName));
    // Esclusa la SEED- con i servizi PM a zero: su quella la fattura è bloccata di
    // proposito (vedi 4.13) e non si arriverebbe mai alla liquidazione.
    let scelto = bookings.body.find(
      b => b.statoPrenotazione === 'ready'
        && !b.externalBookingId.startsWith('SEED-')
        && !ownerConSettlement.has(b.ownerName));

    if (!scelto) {
      // Documenti lasciati indietro da una run interrotta: la prenotazione è 'doc_issued'
      // e ancora fuori da ogni liquidazione. Si ripulisce e torna 'ready'.
      const daLiquidare = await apiGet<BookingDaLiquidare[]>(token, '/settlements/da-liquidare');
      const residuo = (daLiquidare.body ?? [])[0];
      expect(residuo, "serve una prenotazione 'ready' o un residuo da ripulire").toBeTruthy();
      const res = await apiDelete(token, '/test/cleanup-documenti', { bookingId: residuo!.bookingId });
      console.log(`residuo: ripuliti i documenti del booking ${residuo!.bookingId} — HTTP ${res.status}`);
      scelto = (await apiGet<BookingListItem[]>(token, '/bookings')).body
        .find(b => b.id === residuo!.bookingId);
    }
    expect(scelto, 'nessuna prenotazione utilizzabile per la fase 06').toBeTruthy();

    const dettaglio = await apiGet<BookingListItem>(token, `/bookings/${scelto!.id}`);
    booking = dettaglio.body;
    ownerName = booking.ownerName;

    // 3. CF ospite: la fattura PM lo esige. PATCH /guest riscrive tutti i campi,
    //    quindi va rispedita l'anagrafica completa (guest_name è NOT NULL).
    if (!booking.guestTaxCode || booking.guestTaxCode.trim() === '') {
      guestOriginale = soloCampiGuest(booking);
      const res = await apiPatch(token, `/bookings/${booking.id}/guest`,
        { ...guestOriginale, guestTaxCode: CF_OSPITE_TEST });
      expect(res.ok, `PATCH guest fallita: ${JSON.stringify(res.body)}`).toBeTruthy();
    }

    // 4. Ricevuta owner e fattura PM datate nel periodo di test: creano la ritenuta in
    //    ledger e portano la prenotazione a 'doc_issued'.
    for (const tipoDocumento of ['ricevuta_owner', 'fattura_pm']) {
      const res = await apiPost(token, '/documents/generate',
        { bookingId: booking.id, tipoDocumento, dataEmissione: DATA_EMISSIONE });
      expect(res.status, `emissione ${tipoDocumento} fallita: ${JSON.stringify(res.body)}`).toBe(201);
    }

    console.log(`booking di test: ${booking.id} (${booking.externalBookingId}) — owner ${ownerName}, `
      + `documenti emessi al ${DATA_EMISSIONE}, periodo di liquidazione ${PERIOD}`);
  });

  test.afterAll(async () => {
    // Ordine vincolante: settlement_booking punta al booking, quindi la liquidazione
    // va smontata prima dei documenti e della ritenuta.
    if (settlementCreato) {
      const res = await apiDelete(token, '/test/cleanup-settlement',
        { settlementId, forzaSePagato: true });
      console.log(`cleanup liquidazione ${settlementId}: HTTP ${res.status} ${JSON.stringify(res.body)}`);
    }
    if (booking) {
      const res = await apiDelete(token, '/test/cleanup-documenti', { bookingId: booking.id });
      console.log(`cleanup documenti booking ${booking.id}: HTTP ${res.status} ${JSON.stringify(res.body)}`);
    }
    if (guestOriginale) {
      const res = await apiPatch(token, `/bookings/${booking.id}/guest`, guestOriginale);
      console.log(`ripristino anagrafica ospite booking ${booking.id}: HTTP ${res.status}`);
    }
  });

  /** Riga della lista liquidazioni relativa al periodo di test. */
  const rigaSettlement = (page: Page) => page.getByRole('row').filter({ hasText: PERIOD });

  async function apriListaLiquidazioni(page: Page) {
    await login(page, 'tenantAdmin');
    await page.goto('/settlements');
    await expect(page.getByRole('heading', { name: 'Liquidazioni' })).toBeVisible();
  }

  const dettaglioSettlement = async () =>
    (await apiGet<SettlementDetail>(token, `/settlements/${settlementId}`)).body;

  test('6.1 — Login come tenant_admin', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('6.2 — Lista liquidazioni e avviso da liquidare', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.getByRole('link', { name: 'Liquidazioni' }).click();

    await expect(page).toHaveURL(/\/settlements$/);
    await expect(page.getByRole('heading', { name: 'Liquidazioni' })).toBeVisible();

    // La prenotazione con i documenti appena emessi non è ancora liquidata
    await expect(page.getByText(/con ricevuta emessa non ancora liquidat/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Vedi dettaglio' })).toBeVisible();
  });

  test('6.3 — Modale prenotazioni da liquidare', async ({ page }) => {
    await apriListaLiquidazioni(page);
    await page.getByRole('button', { name: 'Vedi dettaglio' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Prenotazioni da liquidare')).toBeVisible();
    for (const col of ['ID', 'Owner', 'Immobile', 'Check-in', 'Periodo', 'Canone €', 'Netto €']) {
      await expect(dialog.getByRole('columnheader', { name: col, exact: true })).toBeVisible();
    }

    // La prenotazione del test è in elenco, con la competenza della sua ritenuta
    const riga = dialog.getByRole('row').filter({ hasText: booking.externalBookingId });
    await expect(riga).toHaveCount(1);
    await expect(riga).toContainText(`${String(MESE).padStart(2, '0')}/${ANNO}`);

    await dialog.getByRole('button', { name: 'Chiudi' }).click();
    await expect(dialog).toHaveCount(0);
  });

  test('6.4 — Calcola liquidazioni', async ({ page }) => {
    await apriListaLiquidazioni(page);
    await page.getByRole('button', { name: 'Calcola liquidazioni' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Calcola liquidazioni')).toBeVisible();

    // Il periodo non è un campo testo: Select del mese + input numerico dell'anno
    await dialog.getByRole('combobox').click();
    await page.getByRole('option', { name: MESE_NOME, exact: true }).click();
    await dialog.locator('input[type="number"]').fill(String(ANNO));

    await dialog.getByRole('button', { name: 'Calcola' }).click();

    await expect(page.getByText('Liquidazioni calcolate')).toBeVisible();
    // Solo la liquidazione di questo test: nessun altro owner ha ritenute nel periodo
    await expect(page.getByText(/1 nuovi, \d+ aggiornati, \d+ saltati/)).toBeVisible();
    await dialog.getByRole('button', { name: 'Chiudi' }).click();

    // La liquidazione è nata: da qui in poi l'afterAll deve smontarla
    const lista = await apiGet<SettlementListItem[]>(token, '/settlements');
    const creato = lista.body.find(s => s.period === PERIOD && s.ownerName === ownerName);
    expect(creato, `liquidazione ${PERIOD} di ${ownerName} non trovata`).toBeTruthy();
    settlementId = creato!.id;
    settlementCreato = true;
    expect(creato!.stato).toBe('calculated');

    // La lista a schermo si è aggiornata
    await expect(rigaSettlement(page)).toHaveCount(1);
  });

  test('6.5 — Liquidazione in lista', async ({ page }) => {
    await apriListaLiquidazioni(page);
    const det = await dettaglioSettlement();

    const riga = rigaSettlement(page);
    await expect(riga).toContainText(ownerName);
    await expect(riga).toContainText(PERIOD);
    await expect(riga).toContainText(euro(det.totalAmount));
    // La ritenuta è esposta come importo negativo (in rosso)
    await expect(riga).toContainText(`-${euro(det.withholdingAmount)}`);
    await expect(riga).toContainText(euro(det.netAmount));
    await expect(riga.getByText('Calcolato')).toBeVisible();

    expect(det.bookingsCount).toBeGreaterThan(0);
    expect(det.totalAmount).toBeGreaterThan(0);
    expect(det.withholdingAmount).toBeGreaterThan(0);
    expect(det.netAmount).toBeGreaterThan(0);
  });

  test('6.6 — Importi coerenti con il registro ritenute', async () => {
    const det = await dettaglioSettlement();

    // Netto = canone − ritenuta (il bollo è informativo e non incide)
    expect(vicino(det.netAmount, det.totalAmount - det.withholdingAmount),
      `netto ${det.netAmount} ≠ ${det.totalAmount} − ${det.withholdingAmount}`).toBeTruthy();

    // Le ritenute vanno cercate nel periodo del settlement e in quelli degli arretrati.
    // L'API ledger non filtra per owner: l'incrocio avviene sull'id esterno del booking.
    const periodi = new Set<string>([`${MESE}/${ANNO}`]);
    for (const b of det.bookings) {
      if (b.periodoLedger) periodi.add(`${Number(b.periodoLedger.slice(0, 2))}/${b.periodoLedger.slice(3)}`);
    }
    const idsSettlement = new Set(det.bookings.map(b => b.externalBookingId));

    let sommaCanone = 0;
    let sommaRitenuta = 0;
    for (const p of periodi) {
      const [m, a] = p.split('/');
      const res = await apiGet<RitenutaRow[]>(token, `/withholding-ledger?anno=${a}&mese=${m}`);
      for (const r of res.body ?? []) {
        if (r.bookingExternalId && idsSettlement.has(r.bookingExternalId)) {
          sommaCanone += r.canoneLocazione;
          sommaRitenuta += r.ritenutaAmount;
        }
      }
    }

    expect(vicino(det.totalAmount, sommaCanone),
      `lordo ${det.totalAmount} ≠ Σ canoni ${sommaCanone}`).toBeTruthy();
    expect(vicino(det.withholdingAmount, sommaRitenuta),
      `ritenute ${det.withholdingAmount} ≠ Σ ritenute ${sommaRitenuta}`).toBeTruthy();
  });

  test('6.7 — Dettaglio liquidazione', async ({ page }) => {
    await apriListaLiquidazioni(page);
    const det = await dettaglioSettlement();

    await rigaSettlement(page).click();
    await expect(page).toHaveURL(new RegExp(`/settlements/${settlementId}$`));

    await expect(page.getByRole('heading', { name: `Liquidazione — ${ownerName}` })).toBeVisible();
    await expect(page.getByText(`Periodo: ${PERIOD}`)).toBeVisible();

    // Tabella delle prenotazioni: una riga per prenotazione collegata
    await expect(page.getByRole('columnheader', { name: 'ID Prenotazione' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Canone €' })).toBeVisible();
    await expect(page.getByText(booking.externalBookingId)).toBeVisible();

    // Riepilogo importi
    await expect(page.getByText('Lordo totale')).toBeVisible();
    await expect(page.getByText('Ritenute totali')).toBeVisible();
    await expect(page.getByText('Netto da pagare')).toBeVisible();
    await expect(page.getByText(euro(det.netAmount)).first()).toBeVisible();

    await expect(page.getByRole('button', { name: 'Scarica PDF' })).toBeVisible();
  });

  test('6.8 — Download PDF rendiconto', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/settlements/${settlementId}`);

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Scarica PDF' }).click();
    const file = await download;
    expect(file.suggestedFilename()).toContain('Rendiconto');
  });

  test('6.9 — Approva liquidazione', async ({ page }) => {
    await apriListaLiquidazioni(page);

    await rigaSettlement(page).getByRole('button', { name: 'Approva' }).click();

    await expect(page.getByText('Liquidazione approvata')).toBeVisible();
    await expect(rigaSettlement(page).getByText('Approvato')).toBeVisible();
    expect((await dettaglioSettlement()).stato).toBe('approved');
  });

  test('6.10 — Segna come pagata', async ({ page }) => {
    await apriListaLiquidazioni(page);

    await rigaSettlement(page).getByRole('button', { name: 'Segna pagato' }).click();

    await expect(page.getByText('Liquidazione segnata come pagata')).toBeVisible();
    await expect(rigaSettlement(page).getByText('Pagato')).toBeVisible();
    // Una liquidazione pagata non ha più azioni disponibili
    await expect(rigaSettlement(page).getByRole('button', { name: 'Approva' })).toHaveCount(0);
    await expect(rigaSettlement(page).getByRole('button', { name: 'Segna pagato' })).toHaveCount(0);
    expect((await dettaglioSettlement()).stato).toBe('paid');
  });

  test('6.11 — Rollover degli arretrati', async ({ page }) => {
    await login(page, 'tenantAdmin');

    // Una prenotazione entrata in una liquidazione di un periodo successivo alla
    // competenza della sua ritenuta: il dettaglio la segnala come arretrato.
    const lista = await apiGet<SettlementListItem[]>(token, '/settlements');
    let conArretrato: { id: number; periodoLedger: string; period: string } | null = null;
    for (const s of lista.body) {
      const det = (await apiGet<SettlementDetail>(token, `/settlements/${s.id}`)).body;
      const arretrato = det.bookings.find(b => b.periodoLedger);
      if (arretrato) {
        conArretrato = { id: s.id, periodoLedger: arretrato.periodoLedger!, period: s.period };
        break;
      }
    }

    if (conArretrato) {
      await page.goto(`/settlements/${conArretrato.id}`);
      await expect(page.getByText(
        `Arretrato: competenza ${conArretrato.periodoLedger}, liquidato in ${conArretrato.period}`,
      )).toBeVisible();
    } else {
      console.log('nessun arretrato in archivio: verificato solo lo svuotamento del da liquidare');
    }

    // Dopo il pagamento la prenotazione del test non è più "da liquidare"
    const daLiquidare = await apiGet<BookingDaLiquidare[]>(token, '/settlements/da-liquidare');
    expect((daLiquidare.body ?? []).some(b => b.bookingId === booking.id)).toBeFalsy();
    if ((daLiquidare.body ?? []).length === 0) {
      await page.goto('/settlements');
      await expect(page.getByText(/con ricevuta emessa non ancora liquidat/)).toHaveCount(0);
    }
  });

  test('6.12 — Ricalcolo idempotente su liquidazione pagata', async ({ page }) => {
    await apriListaLiquidazioni(page);
    const prima = await dettaglioSettlement();

    await page.getByRole('button', { name: 'Calcola liquidazioni' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('combobox').click();
    await page.getByRole('option', { name: MESE_NOME, exact: true }).click();
    await dialog.locator('input[type="number"]').fill(String(ANNO));
    await dialog.getByRole('button', { name: 'Calcola' }).click();

    // Il backend salta gli owner con liquidazione già pagata: nessuna modifica
    await expect(page.getByText(/0 nuovi, 0 aggiornati, 1 saltati/)).toBeVisible();

    const dopo = await dettaglioSettlement();
    expect(dopo.stato).toBe('paid');
    expect(vicino(dopo.totalAmount, prima.totalAmount)).toBeTruthy();
    expect(vicino(dopo.withholdingAmount, prima.withholdingAmount)).toBeTruthy();
    expect(dopo.bookingsCount).toBe(prima.bookingsCount);
  });

  test('6.13 — Filtro per stato', async ({ page }) => {
    await apriListaLiquidazioni(page);

    await page.getByRole('combobox').filter({ hasText: 'Tutti gli stati' }).click();
    await page.getByRole('option', { name: 'Pagato', exact: true }).click();
    await expect(rigaSettlement(page)).toHaveCount(1);

    await page.getByRole('combobox').filter({ hasText: 'Pagato' }).click();
    await page.getByRole('option', { name: 'Calcolato', exact: true }).click();
    await expect(rigaSettlement(page)).toHaveCount(0);
  });

});

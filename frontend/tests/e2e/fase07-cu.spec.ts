import { test, expect, Page } from '@playwright/test';
import { login, USERS } from './helpers/auth';
import { BASE, getToken, apiGet, apiPost, apiDelete } from './helpers/api';

/**
 * Fase 07 — Certificazione Unica: generazione, coerenza degli importi con il registro
 * ritenute, confronto con gli F24 versati, PDF e visibilità dal portale proprietario.
 *
 * Non emette documenti: la CU è un'aggregazione annuale di withholding_ledger. Il
 * beforeAll riusa la CU dell'anno corrente se c'è già, altrimenti la genera e l'afterAll
 * elimina soltanto quelle nate in questa run.
 *
 * NB sugli importi (vedi sql/cu_record/aggregate_by_owner_year.sql):
 *   totalImponibile = Σ canone_locazione        ← la base imponibile
 *   totalRitenute   = Σ ritenuta_amount
 *   totalCompensi   = Σ (canone + ritenuta)     ← il "lordo" dell'Agenzia delle Entrate
 * Il compenso lordo NON è la somma dei canoni: quella è l'imponibile.
 */

const ANNO = new Date().getFullYear();
/** Anno certamente privo di ritenute, per la generazione a vuoto del 7.9. */
const ANNO_VUOTO = 2020;

interface CuListItem {
  id: number;
  ownerName: string;
  taxYear: number;
  totalCompensi: number;
  totalImponibile: number;
  totalRitenute: number;
  stato: string;
  generatedAt?: string;
}

interface CuGeneraBatchResponse {
  generated: number;
  skipped: number;
  records: CuListItem[];
}

interface RitenutaRow {
  id: number;
  ownerName: string | null;
  canoneLocazione: number;
  ritenutaAmount: number;
}

interface F24Row {
  id: number;
  periodoAnno: number;
  totalAmount: number;
  stato: string;
}

/** Confronto su importi in euro: tollera lo scarto di arrotondamento al centesimo. */
const vicino = (a: number, b: number) => Math.abs(a - b) < 0.011;

/** Stesso formato delle pagine CU. */
const euro = (v: number) => `€${(v ?? 0).toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;

let token: string;
let cu: CuListItem;
let sommaCanone = 0;
let sommaRitenuta = 0;
/** Righe del registro ritenute del proprietario della CU nell'anno: 0 = niente da confrontare nel 7.4. */
let righeLedger = 0;
/** CU nate in questa run: solo queste vanno rimosse dall'afterAll. */
let cuCreate: number[] = [];

test.describe.configure({ mode: 'serial' });

test.describe('Fase 07 — Certificazione Unica', () => {

  test.beforeAll(async () => {
    token = await getToken(USERS.tenantAdmin.email, USERS.tenantAdmin.password);

    let lista = await apiGet<CuListItem[]>(token, `/cu?taxYear=${ANNO}`);
    expect(lista.ok, 'GET /cu deve rispondere 200').toBeTruthy();

    if ((lista.body ?? []).length === 0) {
      // ownerId omesso → batch su tutti i proprietari con ritenute nell'anno
      const res = await apiPost<CuGeneraBatchResponse>(token, '/cu/genera', { taxYear: ANNO });
      expect(res.ok, `POST /cu/genera fallita: ${JSON.stringify(res.body)}`).toBeTruthy();
      expect(res.body.generated, `nessuna CU generabile per il ${ANNO}`).toBeGreaterThan(0);
      lista = await apiGet<CuListItem[]>(token, `/cu?taxYear=${ANNO}`);
      cuCreate = lista.body.map(c => c.id);
      console.log(`CU generate per il ${ANNO}: ${cuCreate.join(', ')}`);
    }

    expect(lista.body.length, `nessuna CU per l'anno ${ANNO}`).toBeGreaterThan(0);
    cu = lista.body[0];

    // Valori attesi dal registro ritenute: l'API espone un periodo per volta,
    // quindi l'anno si ricompone mese per mese. Il filtro per proprietario passa dal
    // nome, l'unico riferimento all'owner presente nel DTO delle ritenute.
    for (let mese = 1; mese <= 12; mese++) {
      const res = await apiGet<RitenutaRow[]>(token, `/withholding-ledger?anno=${ANNO}&mese=${mese}`);
      for (const r of res.body ?? []) {
        if (r.ownerName === cu.ownerName) {
          righeLedger++;
          sommaCanone += r.canoneLocazione;
          sommaRitenuta += r.ritenutaAmount;
        }
      }
    }

    console.log(`CU di test: ${cu.id} — ${cu.ownerName} ${cu.taxYear}, compensi ${cu.totalCompensi}, `
      + `imponibile ${cu.totalImponibile}, ritenute ${cu.totalRitenute} `
      + `(ledger: canoni ${sommaCanone.toFixed(2)}, ritenute ${sommaRitenuta.toFixed(2)})`);
  });

  test.afterAll(async () => {
    for (const id of cuCreate) {
      const res = await apiDelete(token, '/test/cleanup-cu', { cuId: id });
      console.log(`cleanup CU ${id}: HTTP ${res.status} ${JSON.stringify(res.body)}`);
    }
  });

  /** Riga della lista CU relativa alla certificazione di test. */
  const rigaCu = (page: Page) => page.getByRole('row').filter({ hasText: cu.ownerName });

  /**
   * Filtro anno: è l'unico combobox il cui testo è un anno.
   * NB: non si può prendere il primo della pagina — la sidebar monta il RoleSwitcher,
   * anch'esso un Select, e nel DOM viene prima del contenuto.
   */
  async function selezionaAnno(page: Page, anno: number) {
    await page.getByRole('combobox').filter({ hasText: /^\d{4}$/ }).click();
    await page.getByRole('option', { name: String(anno), exact: true }).click();
  }

  async function apriListaCu(page: Page) {
    await login(page, 'tenantAdmin');
    await page.goto('/cu');
    await expect(page.getByRole('heading', { name: 'Certificazioni Uniche' })).toBeVisible();
  }

  test('7.1 — Login come tenant_admin', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('7.2 — Lista CU dalla voce di menu', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.getByRole('link', { name: 'CU', exact: true }).click();

    await expect(page).toHaveURL(/\/cu$/);
    await expect(page.getByRole('heading', { name: 'Certificazioni Uniche' })).toBeVisible();

    // La pagina parte dall'anno precedente: la CU dell'anno corrente si vede
    // solo dopo aver spostato il filtro.
    const annoPrecedente = await apiGet<CuListItem[]>(token, `/cu?taxYear=${ANNO - 1}`);
    if ((annoPrecedente.body ?? []).length === 0) {
      await expect(page.getByText(`Nessuna CU per l'anno ${ANNO - 1}`)).toBeVisible();
    }

    await selezionaAnno(page, ANNO);
    await expect(rigaCu(page)).toHaveCount(1);
  });

  test('7.3 — CU in lista', async ({ page }) => {
    await apriListaCu(page);
    await selezionaAnno(page, ANNO);

    const riga = rigaCu(page);
    await expect(riga).toContainText(cu.ownerName);
    await expect(riga).toContainText(String(ANNO));
    await expect(riga).toContainText(euro(cu.totalCompensi));
    await expect(riga).toContainText(euro(cu.totalRitenute));
    // Il badge riporta il codice grezzo dello stato ('generated', 'sent', …)
    await expect(riga.getByText(cu.stato).first()).toBeVisible();

    expect(cu.totalCompensi).toBeGreaterThan(0);
    expect(cu.totalRitenute).toBeGreaterThan(0);
  });

  test('7.4 — Importi coerenti con il registro ritenute', async () => {
    // CU riusata senza righe nel registro ritenute (es. ritenute ripulite dopo la sua
    // generazione): non c'è niente con cui confrontarla, il test viene saltato.
    test.skip(righeLedger === 0,
      `Nessuna riga nel registro ritenute per ${cu.ownerName} nel ${ANNO}: confronto non possibile`);
    const det = (await apiGet<CuListItem>(token, `/cu/${cu.id}`)).body;

    // Imponibile = Σ canoni; ritenute = Σ ritenute; compensi = lordo AdE = imponibile + ritenute
    expect(vicino(det.totalImponibile, sommaCanone),
      `imponibile ${det.totalImponibile} ≠ Σ canoni ledger ${sommaCanone}`).toBeTruthy();
    expect(vicino(det.totalRitenute, sommaRitenuta),
      `ritenute ${det.totalRitenute} ≠ Σ ritenute ledger ${sommaRitenuta}`).toBeTruthy();
    expect(vicino(det.totalCompensi, sommaCanone + sommaRitenuta),
      `compensi ${det.totalCompensi} ≠ canoni + ritenute ${sommaCanone + sommaRitenuta}`).toBeTruthy();
    console.log(`✅ CU totalImponibile = Σ canoni ledger (${det.totalImponibile})`);
    console.log(`✅ CU totalRitenute = Σ ritenute ledger (${det.totalRitenute})`);
    console.log(`✅ CU totalCompensi = canoni + ritenute (${det.totalCompensi})`);
  });

  test('7.5 — Confronto CU / F24 versati (warning, non errore)', async () => {
    const f24 = await apiGet<F24Row[]>(token, '/f24');
    expect(f24.ok, 'GET /f24 deve rispondere 200').toBeTruthy();

    const pagatiAnno = (f24.body ?? []).filter(f => f.periodoAnno === ANNO && f.stato === 'paid');
    const sommaF24 = pagatiAnno.reduce((t, f) => t + f.totalAmount, 0);
    const delta = Math.abs(cu.totalRitenute - sommaF24);

    // Il confronto è indicativo: gli F24 aggregano TUTTI i proprietari del tenant e le
    // ritenute non ancora versate non compaiono in alcun F24. Si registra lo scarto,
    // non lo si trasforma in un fallimento.
    console.log(`CU ritenute ${cu.totalRitenute.toFixed(2)} — F24 pagati ${ANNO} `
      + `${sommaF24.toFixed(2)} (${pagatiAnno.length} modelli) — scarto ${delta.toFixed(2)}`);
    console.log(delta <= 1
      ? '✅ ritenute CU allineate agli F24 versati'
      : "⚠️  scarto atteso: ritenute non ancora versate, o F24 di più proprietari");
  });

  test('7.6 — Download PDF dalla lista', async ({ page }) => {
    await apriListaCu(page);
    await selezionaAnno(page, ANNO);

    const download = page.waitForEvent('download');
    await rigaCu(page).getByRole('button', { name: 'PDF' }).click();
    const file = await download;
    expect(file.suggestedFilename()).toContain(`CU_${ANNO}`);
  });

  test('7.7 — PDF servito dall\'API', async ({ request }) => {
    const res = await request.get(`${BASE}/cu/${cu.id}/pdf`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/pdf');
    // Il backend nomina il file con l'anno e il CF del proprietario
    expect(res.headers()['content-disposition']).toContain(`CU_${ANNO}`);
    expect((await res.body()).length).toBeGreaterThan(0);
  });

  test('7.8 — Filtro per anno', async ({ page }) => {
    await apriListaCu(page);

    await selezionaAnno(page, ANNO);
    await expect(rigaCu(page)).toHaveCount(1);

    await selezionaAnno(page, ANNO - 1);
    await expect(rigaCu(page)).toHaveCount(0);
    const annoPrecedente = await apiGet<CuListItem[]>(token, `/cu?taxYear=${ANNO - 1}`);
    if ((annoPrecedente.body ?? []).length === 0) {
      await expect(page.getByText(`Nessuna CU per l'anno ${ANNO - 1}`)).toBeVisible();
    }
  });

  test('7.9 — Generazione per un anno senza ritenute', async () => {
    const res = await apiPost<CuGeneraBatchResponse>(token, '/cu/genera', { taxYear: ANNO_VUOTO });

    // Nessun proprietario con ritenute nell'anno: batch a vuoto, non un errore
    expect(res.ok, `POST /cu/genera ${ANNO_VUOTO}: ${JSON.stringify(res.body)}`).toBeTruthy();
    expect(res.body.generated).toBe(0);
    expect(res.body.records).toHaveLength(0);

    const lista = await apiGet<CuListItem[]>(token, `/cu?taxYear=${ANNO_VUOTO}`);
    expect(lista.body ?? []).toHaveLength(0);
  });

  test('7.10 — Portale proprietario: solo le proprie CU', async ({ page }) => {
    const tokenOwner = await getToken(USERS.owner.email, USERS.owner.password);

    // Il back-office CU è precluso a owner_user
    const backOffice = await apiGet(tokenOwner, '/cu');
    expect(backOffice.status, '/api/cu deve essere vietato a owner_user').toBe(403);

    // Il portale ricava il proprietario dal token: nessun ownerId dal client
    const proprie = await apiGet<CuListItem[]>(tokenOwner, '/owner/cu');
    expect(proprie.ok, 'GET /owner/cu deve rispondere 200').toBeTruthy();

    await login(page, 'owner');
    await page.goto('/owner/cu');
    await expect(page.getByRole('heading', { name: 'Certificazioni Uniche' })).toBeVisible();

    if ((proprie.body ?? []).length === 0) {
      await expect(page.getByText('Nessuna CU disponibile')).toBeVisible();
    } else {
      for (const propria of proprie.body) {
        const riga = page.getByRole('row').filter({ hasText: String(propria.taxYear) });
        await expect(riga.first()).toContainText(euro(propria.totalCompensi));
      }
      await expect(page.getByRole('button', { name: 'Scarica PDF' }).first()).toBeVisible();
    }

    // La CU del back-office non deve comparire se è di un altro proprietario
    if (!(proprie.body ?? []).some(c => c.id === cu.id)) {
      await expect(page.getByText(euro(cu.totalCompensi))).toHaveCount(0);
    }
  });

});

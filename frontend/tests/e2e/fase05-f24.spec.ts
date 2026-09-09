import { test, expect, Page } from '@playwright/test';
import { login, USERS } from './helpers/auth';
import { getToken, apiGet, apiPost, apiPatch, apiDelete } from './helpers/api';

/**
 * Fase 05 — F24: generazione del modello, verifica del totale contro il registro
 * ritenute, anteprima, PDF, ricalcolo, passaggio a pagato e filtro di periodo.
 *
 * Non emette documenti: parte dalle ritenute già in withholding_ledger. Il beforeAll
 * cerca il periodo più recente con ritenute 'da_versare' e, se non c'è già un F24 non
 * pagato per quel periodo, lo genera; l'afterAll lo elimina rimettendo le ritenute a
 * 'da_versare', così la suite è ripetibile.
 *
 * 5.8 e 5.9 marcano l'F24 come pagato — operazione irreversibile dalla UI — quindi
 * girano SOLO sull'F24 generato dal test. Se il test sta riusando un F24 preesistente
 * vengono saltati per non consumare dati veri.
 *
 * NB: nella UI il pulsante di ricalcolo è etichettato "Aggiungi ritenute non incluse",
 * e l'icona occhio apre il dialog "Dettaglio F24", non un'anteprima del modello:
 * F24PreviewDialog.tsx esiste ma non è agganciato a nessuna pagina.
 */

interface F24Row {
  id: number;
  periodoMese: number;
  periodoAnno: number;
  totalAmount: number;
  withholdingsCount: number;
  stato: string;
  deadlineDate: string;
  paymentDate?: string;
  codiceTributo: string;
}

interface RitenutaRow {
  id: number;
  ownerName: string | null;
  documentNumber: string | null;
  periodoMese: number;
  periodoAnno: number;
  ritenutaAmount: number;
  stato: string;
  fkF24RecordId: number | null;
}

interface F24Dettaglio {
  f24RecordId: number;
  periodoMese: number;
  periodoAnno: number;
  totaleRitenute: number;
  numeroRitenute: number;
  scadenza: string;
  stato: string;
  ritenute: RitenutaRow[];
}

/** Confronto su importi in euro: tollera lo scarto di arrotondamento al centesimo. */
const vicino = (a: number, b: number) => Math.abs(a - b) < 0.011;

/** Stesso formato di F24List.tsx, per ritrovare gli importi a schermo. */
const euro = (v: number) => `€${v.toLocaleString('it-IT', { minimumFractionDigits: 2 })}`;
const periodoLabel = (mese: number, anno: number) => `${String(mese).padStart(2, '0')}/${anno}`;

const MESI = [
  'Gennaio', 'Febbraio', 'Marzo', 'Aprile', 'Maggio', 'Giugno',
  'Luglio', 'Agosto', 'Settembre', 'Ottobre', 'Novembre', 'Dicembre',
];

let token: string;
let f24Id: number;
let anno: number;
let mese: number;
let periodo: string;
/** true se l'F24 l'ha generato questa run: solo allora è lecito pagarlo ed eliminarlo. */
let creatoDalTest = false;

test.describe.configure({ mode: 'serial' });

test.describe('Fase 05 — F24', () => {

  test.beforeAll(async () => {
    token = await getToken(USERS.tenantAdmin.email, USERS.tenantAdmin.password);

    const lista = await apiGet<F24Row[]>(token, '/f24');
    expect(lista.ok, 'GET /f24 deve rispondere 200').toBeTruthy();

    // Periodo di lavoro: il più recente con ritenute ancora da versare, cercando a
    // ritroso di 12 mesi. Il registro ritenute si interroga un periodo per volta.
    const oggi = new Date();
    let trovato: { anno: number; mese: number } | null = null;
    for (let i = 0; i < 12 && !trovato; i++) {
      const d = new Date(oggi.getFullYear(), oggi.getMonth() - i, 1);
      const a = d.getFullYear();
      const m = d.getMonth() + 1;
      const res = await apiGet<RitenutaRow[]>(token, `/withholding-ledger?anno=${a}&mese=${m}`);
      if ((res.body ?? []).some(r => r.stato === 'da_versare')) trovato = { anno: a, mese: m };
    }

    if (trovato) {
      anno = trovato.anno;
      mese = trovato.mese;
      const esistente = lista.body.find(
        f => f.periodoAnno === anno && f.periodoMese === mese && f.stato !== 'paid');
      if (esistente) {
        f24Id = esistente.id;
        console.log(`F24 ${f24Id} già presente per ${periodoLabel(mese, anno)}: lo riuso`);
      } else {
        const res = await apiPost<F24Dettaglio>(token, '/f24/genera', { anno, mese });
        expect(res.status, `POST /f24/genera fallita: ${JSON.stringify(res.body)}`).toBe(201);
        f24Id = res.body.f24RecordId;
        creatoDalTest = true;
        console.log(`F24 ${f24Id} generato per ${periodoLabel(mese, anno)}: `
          + `${res.body.numeroRitenute} ritenute, totale ${res.body.totaleRitenute}`);
      }
    } else {
      // Nessuna ritenuta da versare: si ripiega su un F24 non pagato già in archivio,
      // rinunciando ai test che ne alterano lo stato.
      const riusabile = lista.body.find(f => f.stato !== 'paid');
      expect(riusabile, 'serve una ritenuta da versare o un F24 non pagato').toBeTruthy();
      f24Id = riusabile!.id;
      anno = riusabile!.periodoAnno;
      mese = riusabile!.periodoMese;
      console.log(`nessuna ritenuta da versare: riuso l'F24 ${f24Id} di ${periodoLabel(mese, anno)}`);
    }

    periodo = periodoLabel(mese, anno);
  });

  test.afterAll(async () => {
    if (creatoDalTest) {
      // forzaSePagato: il 5.8 lo ha marcato pagato, ed è comunque l'F24 di questa run.
      const res = await apiDelete(token, '/test/cleanup-f24', { f24Id, forzaSePagato: true });
      console.log(`cleanup F24 ${f24Id}: HTTP ${res.status} ${JSON.stringify(res.body)}`);
    }
  });

  /** Riga della tabella F24 del periodo di lavoro (la colonna Scadenza usa il formato ISO). */
  const rigaF24 = (page: Page) => page.getByRole('row').filter({ hasText: periodo });

  /** Apre la lista F24 già autenticati. */
  async function apriListaF24(page: Page) {
    await login(page, 'tenantAdmin');
    await page.goto('/f24');
    await expect(page.getByRole('heading', { name: 'Modelli F24' })).toBeVisible();
  }

  const dettaglioF24 = async () =>
    (await apiGet<F24Dettaglio>(token, `/f24/${f24Id}`)).body;

  test('5.1 — Login come tenant_admin', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('5.2 — Lista F24 dalla voce di menu', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.getByRole('link', { name: 'F24' }).click();

    await expect(page).toHaveURL(/\/f24$/);
    await expect(page.getByRole('heading', { name: 'Modelli F24' })).toBeVisible();
    await expect(page.getByText('codice tributo 1919')).toBeVisible();
    // La lista non è vuota: almeno una riga oltre all'intestazione
    await expect(page.getByRole('row')).not.toHaveCount(0);
    await expect(page.getByText('Nessun F24')).toHaveCount(0);
  });

  test('5.3 — F24 del periodo presente in lista', async ({ page }) => {
    await apriListaF24(page);
    const det = await dettaglioF24();

    const riga = rigaF24(page);
    await expect(riga).toHaveCount(1);
    await expect(riga).toContainText(periodo);
    await expect(riga).toContainText('1919');
    await expect(riga).toContainText(euro(det.totaleRitenute));
    expect(det.totaleRitenute).toBeGreaterThan(0);
    // Badge di stato: una delle etichette della lookup
    await expect(riga.getByText(/Bozza|Pronto|Inviato|Pagato|Errore/)).toBeVisible();
  });

  test('5.4 — Totale F24 = Σ ritenute del periodo', async () => {
    const det = await dettaglioF24();
    const ledger = await apiGet<RitenutaRow[]>(token, `/withholding-ledger?anno=${anno}&mese=${mese}`);

    const collegate = (ledger.body ?? []).filter(r => r.fkF24RecordId === f24Id);
    const somma = collegate.reduce((t, r) => t + r.ritenutaAmount, 0);

    expect(collegate.length, 'ritenute agganciate all\'F24').toBe(det.numeroRitenute);
    expect(vicino(det.totaleRitenute, somma),
      `totale F24 ${det.totaleRitenute} ≠ Σ ritenute ${somma}`).toBeTruthy();

    // Il totale esposto dalla lista è lo stesso valore persistito
    const lista = await apiGet<F24Row[]>(token, '/f24');
    const riga = lista.body.find(f => f.id === f24Id);
    expect(vicino(riga!.totalAmount, somma)).toBeTruthy();
    expect(riga!.codiceTributo).toBe('1919');
  });

  test('5.5 — Dettaglio ritenute collegate', async ({ page }) => {
    await apriListaF24(page);
    const det = await dettaglioF24();

    await rigaF24(page).getByRole('button', { name: 'Dettaglio' }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText(`Dettaglio F24 ${periodo}`)).toBeVisible();
    await expect(dialog.getByText(`${det.numeroRitenute} ritenute — ${euro(det.totaleRitenute)}`))
      .toBeVisible();
    await expect(dialog.getByRole('columnheader', { name: 'Proprietario' })).toBeVisible();
    await expect(dialog.getByRole('columnheader', { name: 'Ritenuta €' })).toBeVisible();
    // Una riga per ritenuta, più l'intestazione
    await expect(dialog.getByRole('row')).toHaveCount(det.numeroRitenute + 1);
    // Le ritenute nascono da ricevute owner: il numero documento è quello della ricevuta
    for (const r of det.ritenute) {
      await expect(dialog.getByText(r.documentNumber!)).toBeVisible();
    }
  });

  test('5.6 — Download PDF F24', async ({ page }) => {
    await apriListaF24(page);

    const download = page.waitForEvent('download');
    await rigaF24(page).getByRole('button', { name: 'Scarica PDF' }).click();
    const file = await download;
    expect(file.suggestedFilename()).toContain('F24');
  });

  test('5.7 — Ricalcolo F24', async ({ page }) => {
    await apriListaF24(page);
    const prima = await dettaglioF24();
    const ledger = await apiGet<RitenutaRow[]>(token, `/withholding-ledger?anno=${anno}&mese=${mese}`);
    const nuove = (ledger.body ?? []).filter(r => r.stato === 'da_versare');

    // Nella UI il ricalcolo è il pulsante "Aggiungi ritenute non incluse"
    await rigaF24(page).getByRole('button', { name: 'Aggiungi ritenute non incluse' }).click();

    const dopo = await (async () => {
      if (nuove.length > 0) {
        await expect(page.getByText('F24 aggiornato')).toBeVisible();
        return dettaglioF24();
      }
      // Senza ritenute nuove il backend risponde 400 e la UI mostra il toast d'errore
      await expect(page.getByText('Nessuna ritenuta nuova da aggiungere')).toBeVisible();
      return dettaglioF24();
    })();

    if (nuove.length > 0) {
      const atteso = prima.totaleRitenute + nuove.reduce((t, r) => t + r.ritenutaAmount, 0);
      expect(vicino(dopo.totaleRitenute, atteso)).toBeTruthy();
      expect(dopo.numeroRitenute).toBe(prima.numeroRitenute + nuove.length);
    } else {
      expect(vicino(dopo.totaleRitenute, prima.totaleRitenute)).toBeTruthy();
      expect(dopo.numeroRitenute).toBe(prima.numeroRitenute);
    }
  });

  test('5.8 — Passaggio a pagato', async ({ page }) => {
    test.skip(!creatoDalTest, 'F24 preesistente: marcarlo pagato è irreversibile');
    await apriListaF24(page);

    await rigaF24(page).getByRole('button', { name: 'Marca pagato' }).click();

    const conferma = page.getByRole('alertdialog');
    await expect(conferma.getByText('Conferma pagamento F24')).toBeVisible();
    await conferma.getByRole('button', { name: 'Confermo il pagamento' }).click();

    await expect(page.getByText('F24 segnato come pagato')).toBeVisible();
    await expect(rigaF24(page).getByText('Pagato')).toBeVisible();
    // Un F24 pagato non si ricalcola più: il pulsante sparisce dalla riga
    await expect(rigaF24(page).getByRole('button', { name: 'Aggiungi ritenute non incluse' }))
      .toHaveCount(0);
    expect((await dettaglioF24()).stato).toBe('paid');
  });

  test('5.9 — F24 pagato non modificabile', async ({ page }) => {
    test.skip(!creatoDalTest, 'dipende dal 5.8');
    await apriListaF24(page);
    const prima = await dettaglioF24();

    await expect(rigaF24(page).getByRole('button', { name: 'Aggiungi ritenute non incluse' }))
      .toHaveCount(0);
    await expect(rigaF24(page).getByRole('button', { name: 'Marca pagato' })).toHaveCount(0);

    // Anche forzando la chiamata il backend rifiuta: 422, F24 già pagato
    const res = await apiPatch(token, `/f24/${f24Id}/ricalcola`, {});
    expect(res.status).toBe(422);
    expect(JSON.stringify(res.body)).toContain('già pagato');

    const dopo = await dettaglioF24();
    expect(vicino(dopo.totaleRitenute, prima.totaleRitenute)).toBeTruthy();
    expect(dopo.numeroRitenute).toBe(prima.numeroRitenute);
  });

  test('5.10 — Filtro anno e mese', async ({ page }) => {
    await apriListaF24(page);

    await page.getByRole('combobox').filter({ hasText: 'Tutti gli anni' }).click();
    await page.getByRole('option', { name: String(anno), exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`anno=${anno}`));
    await expect(rigaF24(page)).toHaveCount(1);

    await page.getByRole('combobox').filter({ hasText: 'Tutti i mesi' }).click();
    await page.getByRole('option', { name: MESI[mese - 1], exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`mese=${mese}`));
    await expect(rigaF24(page)).toHaveCount(1);

    // Con il filtro attivo restano solo gli F24 del periodo: intestazione + la nostra riga
    await expect(page.getByRole('row')).toHaveCount(2);
  });

});

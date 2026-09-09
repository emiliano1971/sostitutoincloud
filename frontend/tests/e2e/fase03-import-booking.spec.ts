import { test, expect, Page } from '@playwright/test';
import { mkdtempSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';
import { login, USERS } from './helpers/auth';
import { getToken, apiDelete } from './helpers/api';

/**
 * Fase 03 — Import prenotazioni dal wizard: doppio file, mapping colonne, anteprima,
 * conferma, CF calcolato e CF letto dal file, blocco dei duplicati.
 *
 * I fixture sono CSV generati a runtime con il timestamp della run: il wizard accetta
 * ".csv,.xlsx" e il backend sceglie il parser dall'estensione (BookingImportService.readTable).
 * Gli header sono quelli riconosciuti dall'auto-mapping (EXPECTED_BOOKING_HEADERS /
 * EXPECTED_GUEST_HEADERS), così il passo di mapping arriva già compilato.
 *
 * Modalità serial: i test costruiscono uno stato incrementale (import → verifica → duplicati).
 */

const STAMP = Date.now().toString().slice(-6);
const ID_CALC = `E2E-CALC-${STAMP}`;   // CF calcolato dall'anagrafica
const ID_FILE = `E2E-FILE-${STAMP}`;   // CF già presente nel file ospiti
const CF_FILE = 'BNCMRC80A01H501U';

/** Struttura e canale devono risolvere su un property_ota_code esistente del tenant. */
const STRUTTURA = "Ca' Serenella";
const ORIGINE = 'Booking.com';

const fmtIt = (d: Date) =>
  `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;

const primoDelMeseProssimo = () => {
  const oggi = new Date();
  return new Date(oggi.getFullYear(), oggi.getMonth() + 1, 1);
};
const piuGiorni = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

const ARRIVO_1 = primoDelMeseProssimo();
const PARTENZA_1 = piuGiorni(ARRIVO_1, 4);
const ARRIVO_2 = piuGiorni(ARRIVO_1, 10);
const PARTENZA_2 = piuGiorni(ARRIVO_2, 7);

let dirFixtures: string;
let csvPrenotazioni: string;
let csvOspiti: string;

function scriviFixtures() {
  dirFixtures = mkdtempSync(join(tmpdir(), 'e2e-fase03-'));

  const prenotazioni = [
    'Id,Stato,Struttura,Importo totale,Adulti,Arrivo,Partenza,Origine,Cliente',
    `${ID_CALC},CONFIRMADA,${STRUTTURA},380.00,2,${fmtIt(ARRIVO_1)},${fmtIt(PARTENZA_1)},${ORIGINE},Anna Verdi`,
    `${ID_FILE},CONFIRMADA,${STRUTTURA},520.00,1,${fmtIt(ARRIVO_2)},${fmtIt(PARTENZA_2)},${ORIGINE},Marco Bianchi`,
  ].join('\n');

  // Ospite 1: niente CF → viene calcolato da nome/cognome/nascita/sesso/comune.
  // Ospite 2: CF nel file → i dati di nascita restano vuoti e il CF non viene ricalcolato.
  const ospiti = [
    'Id,Nome,Cognome,Data di nascita,Sesso,Comune emittente,Documento,Nº documento,Nazione,Codice Fiscale',
    `${ID_CALC},Anna,Verdi,15/03/1985,Donna,Roma,Carta di identità,AA1234567,100000100,`,
    `${ID_FILE},Marco,Bianchi,,,,,,100000100,${CF_FILE}`,
  ].join('\n');

  csvPrenotazioni = join(dirFixtures, 'bookings-fase03.csv');
  csvOspiti = join(dirFixtures, 'ospiti-fase03.csv');
  writeFileSync(csvPrenotazioni, prenotazioni, 'utf8');
  writeFileSync(csvOspiti, ospiti, 'utf8');
}

/** Input file della dropzone indicata: è nascosto, si valorizza direttamente. */
const inputFile = (page: Page, etichetta: 'File Prenotazioni' | 'File Ospiti') =>
  page.locator(`xpath=//p[contains(text(),"${etichetta}")]`
    + '/ancestor::div[contains(@class,"border-dashed")]//input[@type="file"]');

/** StepNav è renderizzata due volte (in cima e in fondo): serve sempre la prima. */
const navButton = (page: Page, nome: string | RegExp) =>
  page.getByRole('button', { name: nome }).first();

/**
 * Colonna scelta dall'auto-mapping per un campo di sistema, dentro una card precisa.
 *
 * Due accortezze nell'XPath:
 *  - l'ancoraggio alla card, perché "ID Prenotazione" combacia anche con
 *    "ID Prenotazione (merge)" del mapping ospiti;
 *  - not(ancestor::button), perché il SelectValue di Radix rende il valore scelto in
 *    uno <span> come l'etichetta: quando campo e colonna hanno lo stesso nome
 *    (es. "Importo totale") si prenderebbe anche quello, e il suo following::button
 *    è il Select della riga dopo.
 */
const mappingTrigger = (page: Page, card: 'Mapping Prenotazioni' | 'Mapping Ospiti', campo: string) =>
  page.locator(`xpath=//*[normalize-space(text())="${card}"]`
    + '/ancestor::div[contains(@class,"flex-1")][1]'
    + `//span[not(ancestor::button)][starts-with(normalize-space(.),"${campo}")]`
    + '/following::button[1]');

test.describe.configure({ mode: 'serial' });

test.describe('Fase 03 — Import Booking', () => {

  let token: string;

  test.beforeAll(async () => {
    scriviFixtures();
    token = await getToken(USERS.tenantAdmin.email, USERS.tenantAdmin.password);
    // Residui di una run interrotta: gli id sono nuovi a ogni run, ma il pattern li copre tutti.
    const pre = await apiDelete(token, '/test/cleanup-bookings', { externalIdPattern: 'E2E-%' });
    console.log(`pre-cleanup booking 'E2E-%': HTTP ${pre.status} ${JSON.stringify(pre.body)}`);
  });

  test.afterAll(async () => {
    const res = await apiDelete(token, '/test/cleanup-bookings', { externalIdPattern: 'E2E-%' });
    console.log(`cleanup booking 'E2E-%': HTTP ${res.status} ${JSON.stringify(res.body)}`);
  });

  test('3.1 — Login come tenant_admin', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('3.2 — Naviga a import prenotazioni', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/bookings');

    await page.getByRole('button', { name: 'Import' }).click();
    await expect(page).toHaveURL(/\/import\/bookings/);

    await expect(page.getByRole('heading', { name: 'Import Dati' })).toBeVisible();
    await expect(page.getByText('File Prenotazioni')).toBeVisible();
  });

  test('3.3 — Carica file e vai al mapping', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/import/bookings');

    await inputFile(page, 'File Prenotazioni').setInputFiles(csvPrenotazioni);
    await inputFile(page, 'File Ospiti').setInputFiles(csvOspiti);

    await expect(page.getByText('bookings-fase03.csv')).toBeVisible();
    await expect(page.getByText('ospiti-fase03.csv')).toBeVisible();

    await navButton(page, 'Avanti').click();
    await expect(page.getByRole('heading', { name: 'Mapping Prenotazioni' })).toBeVisible();
  });

  test('3.4 — Mapping colonne riconosciuto', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/import/bookings');
    await inputFile(page, 'File Prenotazioni').setInputFiles(csvPrenotazioni);
    await inputFile(page, 'File Ospiti').setInputFiles(csvOspiti);
    await navButton(page, 'Avanti').click();

    const prenotazioni = 'Mapping Prenotazioni';
    await expect(mappingTrigger(page, prenotazioni, 'ID Prenotazione')).toHaveText('Id');
    await expect(mappingTrigger(page, prenotazioni, 'Origine (canale)')).toHaveText('Origine');
    await expect(mappingTrigger(page, prenotazioni, 'Struttura (cod. OTA)')).toHaveText('Struttura');
    await expect(mappingTrigger(page, prenotazioni, 'Check-in')).toHaveText('Arrivo');
    await expect(mappingTrigger(page, prenotazioni, 'Check-out')).toHaveText('Partenza');
    await expect(mappingTrigger(page, prenotazioni, 'Importo totale')).toHaveText('Importo totale');
    await expect(mappingTrigger(page, prenotazioni, 'Cliente nome')).toHaveText('Cliente');

    // Mapping ospiti: da qui dipendono il CF calcolato (3.8) e quello letto dal file (3.9)
    const ospiti = 'Mapping Ospiti';
    await expect(mappingTrigger(page, ospiti, 'ID Prenotazione (merge)')).toHaveText('Id');
    await expect(mappingTrigger(page, ospiti, 'Data di nascita')).toHaveText('Data di nascita');
    await expect(mappingTrigger(page, ospiti, 'Sesso')).toHaveText('Sesso');
    await expect(mappingTrigger(page, ospiti, 'Comune di nascita')).toHaveText('Comune emittente');
    await expect(mappingTrigger(page, ospiti, 'Codice Fiscale')).toHaveText('Codice Fiscale');

    // Tutti i campi obbligatori mappati: l'avviso non compare e si può proseguire
    await expect(page.getByText('Mappa tutti i campi obbligatori')).toHaveCount(0);

    await navButton(page, 'Genera Anteprima').click();
    await expect(page.getByText('Anteprima Import')).toBeVisible();
  });

  test('3.5 — Anteprima con 2 prenotazioni nuove', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/import/bookings');
    await inputFile(page, 'File Prenotazioni').setInputFiles(csvPrenotazioni);
    await inputFile(page, 'File Ospiti').setInputFiles(csvOspiti);
    await navButton(page, 'Avanti').click();
    await navButton(page, 'Genera Anteprima').click();

    await expect(page.getByText('2 nuove')).toBeVisible();
    await expect(page.getByText('0 errori')).toBeVisible();

    const rigaCalc = page.locator('tr', { hasText: ID_CALC });
    const rigaFile = page.locator('tr', { hasText: ID_FILE });
    await expect(rigaCalc).toHaveCount(1);
    await expect(rigaFile).toHaveCount(1);
    await expect(rigaCalc.getByText('nuova')).toBeVisible();
    await expect(rigaFile.getByText('nuova')).toBeVisible();
  });

  test('3.6 — Conferma import', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/import/bookings');
    await inputFile(page, 'File Prenotazioni').setInputFiles(csvPrenotazioni);
    await inputFile(page, 'File Ospiti').setInputFiles(csvOspiti);
    await navButton(page, 'Avanti').click();
    await navButton(page, 'Genera Anteprima').click();

    await page.getByRole('checkbox', { name: 'Seleziona tutte' }).check();
    await navButton(page, /Conferma Import \(2 prenotazioni\)/).click();

    await expect(page.getByText('Conferma Import', { exact: true })).toBeVisible();
    await navButton(page, 'Procedi').click();

    await expect(page.getByText('Import Completato')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/2 importate/)).toBeVisible();
  });

  test('3.7 — Booking in lista', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/bookings');

    await page.getByPlaceholder('Cerca ospite, immobile, ID...').fill(ID_CALC);

    const riga = page.locator('tr', { hasText: ID_CALC });
    await expect(riga).toHaveCount(1, { timeout: 10000 });
    await expect(riga.getByText('Anna Verdi')).toBeVisible();
    // Lo stato è derivato dai dati (BookingService.resolveStatoId): l'import scrive
    // 'imported', ma con il CF calcolato si sale a 'enriched' e, se l'immobile ha
    // regole di contratto che rendono calcolabile lo split, fino a 'ready'.
    await expect(riga.getByText(/Importata|Arricchita|Pronta/)).toBeVisible();
  });

  test('3.8 — CF calcolato dall’anagrafica', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/bookings');
    await page.getByPlaceholder('Cerca ospite, immobile, ID...').fill(ID_CALC);
    await page.locator('tr', { hasText: ID_CALC }).click();
    await expect(page).toHaveURL(/\/bookings\/\d+/);

    await page.getByRole('button', { name: 'Modifica' }).first().click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Modifica anagrafica ospite')).toBeVisible();

    const cf = dialog.locator('xpath=.//label[contains(., "Codice Fiscale")]/following::input[1]');
    const valoreCf = await cf.inputValue();
    expect(valoreCf).not.toBe('');
    expect(valoreCf).toHaveLength(16);

    const nascita = dialog.locator('xpath=.//label[contains(., "Data di nascita")]/following::input[1]');
    await expect(nascita).toHaveValue('1985-03-15');

    const comune = dialog.locator('xpath=.//label[contains(., "Comune di nascita")]/following::input[1]');
    await expect(comune).toHaveValue(/Roma/);
  });

  test('3.9 — CF letto dal file', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/bookings');
    await page.getByPlaceholder('Cerca ospite, immobile, ID...').fill(ID_FILE);
    await page.locator('tr', { hasText: ID_FILE }).click();
    await expect(page).toHaveURL(/\/bookings\/\d+/);

    await expect(page.getByText(CF_FILE)).toBeVisible();
    // Il dettaglio mostra "Data nascita" solo se valorizzata: qui il file non la porta
    await expect(page.getByText('Data nascita')).toHaveCount(0);
  });

  test('3.10 — Duplicati bloccati al secondo import', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/import/bookings');
    await inputFile(page, 'File Prenotazioni').setInputFiles(csvPrenotazioni);
    await inputFile(page, 'File Ospiti').setInputFiles(csvOspiti);
    await navButton(page, 'Avanti').click();
    await navButton(page, 'Genera Anteprima').click();

    await expect(page.getByText('2 duplicate')).toBeVisible();
    await expect(page.getByText('0 nuove')).toBeVisible();

    await expect(page.locator('tr', { hasText: ID_CALC }).getByText('duplicata')).toBeVisible();
    await expect(page.locator('tr', { hasText: ID_FILE }).getByText('duplicata')).toBeVisible();

    // Nessuna conferma: si esce dal wizard senza importare nulla
    await page.goto('/bookings');
  });

});

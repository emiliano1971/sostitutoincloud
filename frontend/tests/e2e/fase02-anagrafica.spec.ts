import { test, expect, Page } from '@playwright/test';
import { login, USERS } from './helpers/auth';
import { getToken, apiDelete } from './helpers/api';
import { fillCampo } from './helpers/form';

/**
 * Fase 02 — Anagrafica: proprietario, immobile, associazione, contratto, codice OTA.
 *
 * Gira come tenant_admin sul tenant esistente (Casa Vacanze Italia): nessun tenant
 * nuovo. Modalità serial perché i test costruiscono uno stato incrementale.
 *
 * Selettori: i form usano <Label> senza htmlFor e <Input> senza id, quindi si passa
 * da fillCampo() (vedi helpers/form.ts). Le etichette sono quelle reali a schermo,
 * asterischi inclusi.
 */

const SUFFIX = 'E2E-' + Date.now().toString().slice(-6);

const OWNER = {
  firstName: 'Giovanni',
  lastName: `Verdi${SUFFIX}`,
  taxCode: 'VRDGNN80A01H501Z',
  iban: 'IT60X0542811101000000123456',
  email: `giovanni.verdi.${SUFFIX}@test.it`,
  phone: '3331234567',
};

const PROPERTY = {
  name: `Appartamento ${SUFFIX}`,
  internalCode: `APP-${SUFFIX}`,
  city: 'Roma',
  address: 'Via Test 123',
};

/** Regime fiscale: obbligatorio in OwnerCreate anche se non citato nei dati di test. */
const OWNER_REGIME = 'Cedolare secca';

test.describe.configure({ mode: 'serial' });

test.describe('Fase 02 — Anagrafica', () => {

  let token: string;
  let ownerId: number | null = null;
  let propertyId: number | null = null;

  /** Seleziona un'opzione in un Select shadcn: apre il trigger accanto all'etichetta. */
  async function selezionaOpzione(page: Page, label: string, opzione: string | RegExp) {
    const trigger = page.locator(
      `xpath=.//label[starts-with(normalize-space(.), '${label}')]/following::button[1]`,
    );
    await trigger.click();
    await page.getByRole('option', { name: opzione }).click();
  }

  test.beforeAll(async () => {
    token = await getToken(USERS.tenantAdmin.email, USERS.tenantAdmin.password);
    // Pre-cleanup: il CF dei dati di test è fisso e owner_profile ha
    // UNIQUE (fk_tenant_id, tax_code), quindi il residuo di una run interrotta
    // bloccherebbe la successiva. Si spazza per cognome, così si rimuove ogni
    // proprietario di test rimasto e non solo quello con il CF corrente.
    const pre = await apiDelete(token, '/test/cleanup-anagrafica', { lastNamePattern: 'E2E-' });
    console.log(`pre-cleanup lastName ~ 'E2E-': HTTP ${pre.status} ${JSON.stringify(pre.body)}`);
  });

  test.afterAll(async () => {
    if (propertyId === null && ownerId === null) return;
    const res = await apiDelete(token, '/test/cleanup-anagrafica', { ownerId, propertyId });
    console.log(`cleanup owner=${ownerId} property=${propertyId}: `
      + `HTTP ${res.status} ${JSON.stringify(res.body)}`);
  });

  test('2.1 — Login come tenant_admin', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('2.2 — Naviga a lista proprietari', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/owners');
    await expect(page.getByRole('heading', { name: 'Proprietari' })).toBeVisible();
  });

  test('2.3 — Crea nuovo proprietario', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/owners');

    await page.getByRole('button', { name: /nuovo proprietario/i }).click();
    await expect(page.getByRole('heading', { name: 'Nuovo Proprietario' })).toBeVisible();

    await selezionaOpzione(page, 'Tipo proprietario', 'Persona Fisica');
    await fillCampo(page, 'Nome', OWNER.firstName, { exact: false });
    await fillCampo(page, 'Cognome', OWNER.lastName);
    await fillCampo(page, 'Codice Fiscale', OWNER.taxCode);
    await fillCampo(page, 'Email', OWNER.email);
    await fillCampo(page, 'Telefono', OWNER.phone);
    await fillCampo(page, 'IBAN', OWNER.iban);
    await selezionaOpzione(page, 'Regime', OWNER_REGIME);

    await page.getByRole('button', { name: 'Crea Proprietario' }).click();

    // Dopo il salvataggio si torna alla lista
    const row = page.locator('tr', { hasText: OWNER.lastName });
    await expect(row).toHaveCount(1, { timeout: 5000 });

    // ownerId dalla URL del dettaglio (la riga è cliccabile)
    await row.click();
    await expect(page).toHaveURL(/\/owners\/\d+/);
    ownerId = Number(page.url().match(/\/owners\/(\d+)/)![1]);
    expect(ownerId).toBeGreaterThan(0);
  });

  test('2.4 — Verifica dettaglio proprietario', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/owners/${ownerId}`);

    await expect(page.getByRole('heading',
      { name: `${OWNER.firstName} ${OWNER.lastName}` })).toBeVisible();
    await expect(page.getByText(OWNER.taxCode)).toBeVisible();
    await expect(page.getByText(OWNER.iban)).toBeVisible();
    await expect(page.getByText('Attivo', { exact: true })).toBeVisible();
  });

  test('2.5 — Crea nuovo immobile', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto('/properties');

    await page.getByRole('button', { name: /nuovo immobile/i }).click();
    await expect(page.getByRole('heading', { name: 'Nuovo Immobile' })).toBeVisible();

    await fillCampo(page, 'Nome immobile', PROPERTY.name);
    await fillCampo(page, 'Codice interno', PROPERTY.internalCode);
    await fillCampo(page, 'Indirizzo', PROPERTY.address);

    // Città: ComuneAutocomplete, opzioni role=option con testo "Roma (RM)"
    await page.getByPlaceholder('es. Roma').fill(PROPERTY.city);
    await page.getByRole('option', { name: 'Roma (RM)' }).click();

    // Proprietario: le opzioni sono "Nome Cognome — CF"
    await selezionaOpzione(page, 'Proprietario', new RegExp(OWNER.lastName));

    await page.getByRole('button', { name: 'Crea Immobile' }).click();

    const row = page.locator('tr', { hasText: PROPERTY.name });
    await expect(row).toHaveCount(1, { timeout: 5000 });

    await row.click();
    await expect(page).toHaveURL(/\/properties\/\d+/);
    propertyId = Number(page.url().match(/\/properties\/(\d+)/)![1]);
    expect(propertyId).toBeGreaterThan(0);
  });

  test('2.6 — Verifica dettaglio immobile', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/properties/${propertyId}`);

    await expect(page.getByRole('heading', { name: PROPERTY.name })).toBeVisible();
    await expect(page.getByText(OWNER.lastName).first()).toBeVisible();
    await expect(page.getByText(PROPERTY.city).first()).toBeVisible();
  });

  test('2.7 — Configura contratto immobile', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await page.goto(`/properties/${propertyId}/contracts`);

    await expect(page.getByRole('heading',
      { name: `Contratto — ${PROPERTY.name}` })).toBeVisible();

    await page.getByRole('button', { name: /aggiungi regola/i }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Nuova Regola di Costo')).toBeVisible();

    await selezionaOpzione(page, 'Tipo di Costo', 'Pulizie Abitazione');
    await selezionaOpzione(page, 'Modalità di Calcolo', 'Importo Fisso (€)');
    await fillCampo(dialog, 'Importo', '50');

    await dialog.getByRole('button', { name: 'Aggiungi' }).click();

    // La regola compare nella lista con tipo e importo. L'assert è ancorato alla riga
    // della prima tabella (Regole di Imputazione Costi): il solo importo comparirebbe
    // anche nel breakdown della card "Simulazione Prenotazione".
    const rigaRegola = page.locator('table').first()
      .locator('tr', { hasText: 'Pulizie Abitazione' });
    await expect(rigaRegola).toHaveCount(1, { timeout: 5000 });
    await expect(rigaRegola.getByText('€50.00')).toBeVisible();
  });

  test('2.8 — Aggiungi codice OTA', async ({ page }) => {
    await login(page, 'tenantAdmin');
    // I codici OTA si inseriscono dalla pagina di modifica: il dettaglio li mostra
    // in sola lettura e la pagina contratti non li gestisce.
    await page.goto(`/properties/${propertyId}/edit`);

    await fillCampo(page, 'Booking.com', PROPERTY.name);
    await page.getByRole('button', { name: 'Salva Modifiche' }).click();

    await expect(page).toHaveURL(new RegExp(`/properties/${propertyId}$`));
    await expect(page.getByText('Booking.com').first()).toBeVisible({ timeout: 5000 });
    await expect(page.getByText(PROPERTY.name).first()).toBeVisible();
  });

});

import { test, expect } from '@playwright/test';
import { login, USERS } from './helpers/auth';
import { getToken, apiGet, apiDelete } from './helpers/api';
import { fillCampo } from './helpers/form';

/**
 * Fase 01 — Onboarding di un tenant come super_admin.
 *
 * I test 1.3 → 1.7 costruiscono uno stato incrementale (crea tenant → attiva →
 * crea admin → login), quindi il describe è in modalità serial: al primo
 * fallimento i successivi vengono saltati invece di fallire a cascata.
 *
 * Selettori: i form di TenantCreate e del dialog "Crea Admin" usano <Label>
 * senza htmlFor e <Input> senza id, quindi getByLabel() non li associa — si passa
 * dall'helper fillCampo(). I pulsanti icona della lista tenant non hanno nome
 * accessibile: si individuano per posizione (0 = dettaglio, 1 = attiva/sospendi).
 */

const SUFFIX = 'E2E-' + Date.now().toString().slice(-6);
const TENANT_NAME = `Test Tenant ${SUFFIX}`;
const TENANT_DISPLAY = `Test ${SUFFIX}`;
const TENANT_EMAIL = `admin-${SUFFIX}@testlocale.it`;
const TENANT_PASSWORD = 'Test2026!';

interface TenantRow {
  id: number;
  legalName: string;
  displayName: string;
  stato: string;
}

test.describe.configure({ mode: 'serial' });

test.describe('Fase 01 — Onboarding Tenant', () => {

  let superAdminToken: string;

  test.beforeAll(async () => {
    superAdminToken = await getToken(USERS.superAdmin.email, USERS.superAdmin.password);
  });

  test.afterAll(async () => {
    // Cleanup: elimina il tenant di test trovandolo per suffisso.
    const lista = await apiGet<TenantRow[]>(superAdminToken, '/admin/tenants');
    if (!lista.ok || !Array.isArray(lista.body)) return;
    const testTenant = lista.body.find(t => (t.legalName ?? '').includes(SUFFIX));
    if (!testTenant) return;
    const res = await apiDelete(superAdminToken, `/admin/tenants/${testTenant.id}/cleanup`);
    console.log(`cleanup tenant ${testTenant.id} (${testTenant.legalName}): HTTP ${res.status}`);
  });

  test('1.1 — Login come super_admin', async ({ page }) => {
    await login(page, 'superAdmin');
    await expect(page).toHaveURL(/\/admin/);
    // Il ruolo corrente è mostrato dal RoleSwitcher nella sidebar
    await expect(page.getByText(/super.admin/i).first()).toBeVisible();
  });

  test('1.2 — Naviga a lista tenant', async ({ page }) => {
    await login(page, 'superAdmin');
    await page.goto('/admin/tenants');
    await expect(page.getByRole('heading', { name: /tenant/i })).toBeVisible();
  });

  test('1.3 — Crea nuovo tenant', async ({ page }) => {
    await login(page, 'superAdmin');
    await page.goto('/admin/tenants');

    await page.getByRole('button', { name: /nuovo tenant/i }).click();
    await expect(page.getByRole('heading', { name: 'Nuovo Tenant' })).toBeVisible();

    await fillCampo(page, 'Ragione Sociale', TENANT_NAME);
    await fillCampo(page, 'Nome Display', TENANT_DISPLAY);
    await fillCampo(page, 'Codice Fiscale', 'TSTCMP80A01H501Z');
    await fillCampo(page, 'Partita IVA', '99999999901');
    await fillCampo(page, 'Indirizzo', 'Via Test 1');
    await fillCampo(page, 'CAP', '00100');
    await fillCampo(page, 'Email Amministrativa', TENANT_EMAIL);

    // ComuneAutocomplete: placeholder "es. Roma", opzioni role=option con "Roma (RM)"
    await page.getByPlaceholder('es. Roma').fill('Roma');
    await page.getByRole('option', { name: 'Roma (RM)' }).click();

    await page.getByRole('button', { name: /salva|crea/i }).click();

    // Dopo il salvataggio si torna alla lista, che contiene il nuovo tenant
    await expect(page.getByText(TENANT_NAME)).toBeVisible({ timeout: 5000 });
  });

  test('1.4 — Verifica stato draft', async ({ page }) => {
    await login(page, 'superAdmin');
    await page.goto('/admin/tenants');

    const row = page.locator('tr', { hasText: TENANT_NAME });
    await expect(row).toHaveCount(1);
    await expect(row.getByText(/draft|bozza/i)).toBeVisible();
  });

  test('1.5 — Attiva tenant', async ({ page }) => {
    await login(page, 'superAdmin');
    await page.goto('/admin/tenants');

    const row = page.locator('tr', { hasText: TENANT_NAME });
    // Pulsanti icona senza nome accessibile: 0 = dettaglio (Eye), 1 = attiva (Play)
    await row.getByRole('button').nth(1).click();

    await expect(row.getByText(/^active$|attivo/i)).toBeVisible({ timeout: 5000 });
  });

  test('1.6 — Crea utente admin tenant', async ({ page }) => {
    await login(page, 'superAdmin');
    await page.goto('/admin/tenants');

    // La riga non è cliccabile: al dettaglio si arriva col pulsante Eye
    const row = page.locator('tr', { hasText: TENANT_NAME });
    await row.getByRole('button').first().click();
    await expect(page).toHaveURL(/\/admin\/tenants\/\d+/);

    await page.getByRole('button', { name: /crea admin/i }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Crea Utente Amministratore')).toBeVisible();

    await fillCampo(dialog, 'Email', TENANT_EMAIL);
    await fillCampo(dialog, 'Nome', 'Admin', { exact: false });
    await fillCampo(dialog, 'Cognome', SUFFIX);
    await fillCampo(dialog, 'Password', TENANT_PASSWORD);

    await dialog.getByRole('button', { name: /salva|crea/i }).click();

    await expect(page.getByText(TENANT_EMAIL)).toBeVisible({ timeout: 5000 });
  });

  test('1.7 — Login come nuovo admin', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(TENANT_EMAIL);
    // exact: come in helpers/auth.ts, il toggle "Mostra password" renderebbe il locator ambiguo.
    await page.getByLabel('Password', { exact: true }).fill(TENANT_PASSWORD);
    await page.getByRole('button', { name: 'Accedi' }).click();

    // Utente appena creato: al primo accesso il cambio password è obbligatorio,
    // quindi il login porta a /change-password e non alla dashboard.
    await expect(page).toHaveURL(/\/change-password/, { timeout: 10000 });
    await expect(page.getByText('Cambio password obbligatorio')).toBeVisible();
  });

});

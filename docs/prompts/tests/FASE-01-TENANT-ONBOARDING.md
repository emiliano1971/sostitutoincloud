Leggi il file CLAUDE.md prima di procedere.

Configura Playwright per i test E2E
in frontend/tests/e2e/ e scrivi
il primo test per la Fase 01
(onboarding tenant come super_admin).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. CONFIGURAZIONE PLAYWRIGHT
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica/crea frontend/playwright.config.ts:

import { defineConfig } from '@playwright/test'

export default defineConfig({
testDir: './tests/e2e',
timeout: 30000,
retries: 0,
use: {
baseURL: 'http://localhost:5173',
headless: true,
screenshot: 'only-on-failure',
video: 'off',
},
reporter: [
['list'],
['html', {
outputFolder: 'tests/e2e/report',
open: 'never'
}]
],
})

Aggiungi in frontend/package.json
nella sezione scripts:
"test:e2e": "playwright test",
"test:e2e:ui": "playwright test --headed"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. HELPERS CONDIVISI
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/tests/e2e/helpers/
auth.ts:

import { Page } from '@playwright/test'

export const USERS = {
superAdmin: {
email: 'superadmin@sostitutoincloud.it',
password: 'atena',
role: 'super_admin'
},
tenantAdmin: {
email: 'admin@casavacanze.it',
password: 'atena2026',
role: 'tenant_admin'
},
owner: {
email: 'proprietario@email.it',
password: 'atena',
role: 'owner_user'
}
}

export async function login(
page: Page,
user: keyof typeof USERS
) {
const u = USERS[user]
await page.goto('/login')
await page.getByLabel('Email').fill(u.email)
await page.getByLabel('Password').fill(u.password)
await page.getByRole('button',
{ name: 'Accedi' }).click()
// Attendi redirect post-login
await page.waitForURL(url =>
!url.includes('/login'),
{ timeout: 10000 })
}

export async function logout(page: Page) {
// Cerca pulsante logout nella sidebar
await page.getByRole('button',
{ name: /logout/i }).click()
await page.waitForURL('**/login')
}

Crea frontend/tests/e2e/helpers/
api.ts:
(helper per chiamate API dirette
durante i test — es. cleanup)

import { request } from '@playwright/test'

const BASE = 'http://localhost:8081/sostitutoincloud/api'

export async function getToken(
email: string,
password: string
): Promise<string> {
const ctx = await request.newContext()
const res = await ctx.post(
`${BASE}/public/login`,
{ data: { email, password } })
const json = await res.json()
return json.token
}

export async function apiDelete(
token: string,
path: string,
body?: object
) {
const ctx = await request.newContext({
extraHTTPHeaders: {
'Authorization': `Bearer ${token}`
}
})
return ctx.delete(`${BASE}${path}`,
{ data: body })
}

export async function apiPost(
token: string,
path: string,
body: object
) {
const ctx = await request.newContext({
extraHTTPHeaders: {
'Authorization': `Bearer ${token}`
}
})
return ctx.post(`${BASE}${path}`,
{ data: body })
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. PRIMO TEST — Fase 01 Onboarding Tenant
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/tests/e2e/
fase01-tenant-onboarding.spec.ts:

import { test, expect } from
'@playwright/test'
import { login } from './helpers/auth'
import { getToken, apiDelete }
from './helpers/api'

const SUFFIX = 'E2E-' +
Date.now().toString().slice(-6)
const TENANT_NAME =
`Test Tenant ${SUFFIX}`
const TENANT_EMAIL =
`admin-${SUFFIX}@testlocale.it`
const TENANT_PASSWORD = 'Test2026!'

test.describe('Fase 01 — Onboarding Tenant',
() => {

let superAdminToken: string

test.beforeAll(async () => {
superAdminToken = await getToken(
'superadmin@sostitutoincloud.it',
'atena')
})

test.afterAll(async () => {
// Cleanup: elimina tenant di test
// trovando per nome
const ctx = await (await import(
'@playwright/test')).request
.newContext({
extraHTTPHeaders: {
'Authorization':
`Bearer ${superAdminToken}`
}
})
const list = await ctx.get(
'http://localhost:8081/' +
'sostitutoincloud/api/admin/tenants')
const tenants = await list.json()
const testTenant = tenants.find(
(t: any) => t.displayName
.includes(SUFFIX))
if (testTenant) {
// Elimina utenti del tenant
await ctx.delete(
`http://localhost:8081/` +
`sostitutoincloud/api/admin/` +
`tenants/${testTenant.id}/cleanup`)
}
})

test('1.1 — Login come super_admin',
async ({ page }) => {
await login(page, 'superAdmin')
await expect(page).toHaveURL(
/\/admin/)
await expect(page.getByText(
/super.admin/i)).toBeVisible()
})

test('1.2 — Naviga a lista tenant',
async ({ page }) => {
await login(page, 'superAdmin')
await page.goto('/admin/tenants')
await expect(page.getByRole(
'heading', { name: /tenant/i }))
.toBeVisible()
})

test('1.3 — Crea nuovo tenant',
async ({ page }) => {
await login(page, 'superAdmin')
await page.goto('/admin/tenants')

    // Clicca "Nuovo Tenant"
    await page.getByRole('button',
      { name: /nuovo tenant/i }).click()

    // Compila form
    await page.getByLabel(
      'Ragione Sociale').fill(TENANT_NAME)
    await page.getByLabel(
      'Nome Display').fill(
        `Test ${SUFFIX}`)
    await page.getByLabel(
      'Partita IVA').fill('99999999901')
    await page.getByLabel(
      'Codice Fiscale').fill(
        'TSTCMP80A01H501Z')
    await page.getByLabel(
      'Email Amministrativa').fill(
        TENANT_EMAIL)
    await page.getByLabel(
      'Indirizzo').fill('Via Test 1')
    await page.getByLabel(
      'CAP').fill('00100')

    // Seleziona comune Roma con
    // ComuneAutocomplete
    await page.getByPlaceholder(
      /comune/i).fill('Roma')
    await page.getByText('Roma',
      { exact: true }).first().click()

    // Salva
    await page.getByRole('button',
      { name: /salva|crea/i }).click()

    // Verifica creazione
    await expect(page.getByText(
      TENANT_NAME)).toBeVisible(
        { timeout: 5000 })
})

test('1.4 — Verifica stato draft',
async ({ page }) => {
await login(page, 'superAdmin')
await page.goto('/admin/tenants')

    const row = page.getByText(TENANT_NAME)
      .locator('..')
    await expect(row.getByText(
      /draft|bozza/i)).toBeVisible()
})

test('1.5 — Attiva tenant',
async ({ page }) => {
await login(page, 'superAdmin')
await page.goto('/admin/tenants')

    // Trova riga del tenant di test
    const row = page.locator('tr',
      { hasText: TENANT_NAME })

    // Clicca pulsante Play (attiva)
    await row.getByRole('button',
      { name: /attiva|play/i }).click()

    // Verifica stato active
    await expect(row.getByText(
      /active|attivo/i)).toBeVisible(
        { timeout: 5000 })
})

test('1.6 — Crea utente admin tenant',
async ({ page }) => {
await login(page, 'superAdmin')

    // Vai al dettaglio del tenant
    await page.goto('/admin/tenants')
    await page.getByText(TENANT_NAME)
      .click()

    // Clicca "Crea Admin"
    await page.getByRole('button',
      { name: /crea admin/i }).click()

    // Compila form utente
    await page.getByLabel('Email')
      .fill(TENANT_EMAIL)
    await page.getByLabel('Nome')
      .fill('Admin')
    await page.getByLabel('Cognome')
      .fill(SUFFIX)
    await page.getByLabel('Password')
      .fill(TENANT_PASSWORD)

    // Salva
    await page.getByRole('button',
      { name: /salva|crea/i }).click()

    // Verifica utente creato
    await expect(page.getByText(
      TENANT_EMAIL)).toBeVisible(
        { timeout: 5000 })
})

test('1.7 — Login come nuovo admin',
async ({ page }) => {
await page.goto('/login')
await page.getByLabel('Email')
.fill(TENANT_EMAIL)
await page.getByLabel('Password')
.fill(TENANT_PASSWORD)
await page.getByRole('button',
{ name: 'Accedi' }).click()

    // Deve andare alla dashboard tenant
    await expect(page).toHaveURL(
      /\/dashboard/, { timeout: 10000 })
})

})

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BACKEND — endpoint cleanup
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/TenantController.java:

DELETE /api/admin/tenants/{id}/cleanup
- Solo ROLE_SUPER_ADMIN
- Elimina in ordine:
    1. audit_log WHERE entity_id = id
       AND entity_type = 'Tenant'
    2. utente WHERE fk_tenant_id = id
    3. tenant_settings WHERE
       fk_tenant_id = id
    4. tenant WHERE id = id
- ResponseEntity.ok(Map.of(
  "message", "Tenant eliminato"))
- Solo per tenant con legal_name
  LIKE '%E2E-%' o '%TEST-%'
  (protezione anti-eliminazione
  dati reali)
- Log INFO

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. ESECUZIONE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Assicurati che siano attivi:
- Tomcat su :8081
- Vite su :5173 (npm run dev)

Poi esegui:
cd frontend
npx playwright test \
tests/e2e/fase01-tenant-onboarding.spec.ts \
--headed

Riporta output dei test con
✅/❌ per ogni test case.
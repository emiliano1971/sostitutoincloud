import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { login, USERS } from './helpers/auth';
import { BASE, getToken, apiGet, apiDelete } from './helpers/api';

/**
 * Fase 08 — Security: ogni ruolo raggiunge solo le proprie risorse, e i tentativi
 * cross-tenant e cross-owner vengono respinti.
 *
 * Quasi tutto è a livello di API: la difesa vera è la SecurityChain, non le rotte React.
 * Il browser serve solo alla sezione D, che verifica i redirect per ruolo — il
 * corrispettivo lato client, utile a non mostrare pagine che l'utente non può popolare.
 *
 * Nessun dato creato, nessun cleanup: sono tutte letture, più una DELETE che deve
 * essere respinta.
 *
 * NB: i test NON sono in modalità serial — un buco di sicurezza non deve impedire
 * la verifica di tutti gli altri.
 */

/** Back-office del tenant: precluso a owner_user (SecurityConfig). */
const ENDPOINT_TENANT = [
  '/bookings', '/settlements', '/documents', '/cu', '/owners',
  '/properties', '/f24', '/users', '/dashboard', '/settings',
];

/** Portale proprietario: riservato a owner_user. */
const ENDPOINT_OWNER = ['/owner/bookings', '/owner/settlements', '/owner/cu', '/owner/dashboard'];

interface Utente {
  id: number;
  email: string;
  ruolo: string;
  fkTenantId: number;
  fkOwnerId?: number;
}

interface BookingRow {
  id: number;
  externalBookingId: string;
  ownerName: string;
  fkOwnerId: number;
}

interface OwnerRow {
  id: number;
}

interface SettlementRow {
  id: number;
  fkOwnerId?: number;
  ownerName: string;
}

interface CuRow {
  id: number;
  ownerName: string;
}

let tokenSuper: string;
let tokenAdmin: string;
let tokenOwner: string;

/** Esiti in forma "path=status": in caso di rosso il diff dice subito quale endpoint. */
async function statusDi(token: string, paths: string[]): Promise<string[]> {
  const esiti: string[] = [];
  for (const p of paths) {
    esiti.push(`${p}=${(await apiGet(token, p)).status}`);
  }
  return esiti;
}

const attesi = (paths: string[], status: number) => paths.map(p => `${p}=${status}`);

test.describe('Fase 08 — Security', () => {

  test.beforeAll(async () => {
    tokenSuper = await getToken(USERS.superAdmin.email, USERS.superAdmin.password);
    tokenAdmin = await getToken(USERS.tenantAdmin.email, USERS.tenantAdmin.password);
    tokenOwner = await getToken(USERS.owner.email, USERS.owner.password);
  });

  // ── SEZIONE A — Endpoint tenant protetti ─────────────────────────────────

  test('8.1 — owner_user non accede agli endpoint tenant', async () => {
    expect(await statusDi(tokenOwner, ENDPOINT_TENANT)).toEqual(attesi(ENDPOINT_TENANT, 403));
    console.log('✅ owner_user bloccato su tutti gli endpoint tenant');
  });

  test('8.2 — owner_user accede agli endpoint del portale owner', async () => {
    const paths = [...ENDPOINT_OWNER, '/auth/me'];
    expect(await statusDi(tokenOwner, paths)).toEqual(attesi(paths, 200));
    console.log('✅ owner_user accede al portale owner');
  });

  test('8.3 — tenant_admin non accede al portale owner', async () => {
    expect(await statusDi(tokenAdmin, ENDPOINT_OWNER)).toEqual(attesi(ENDPOINT_OWNER, 403));
    console.log('✅ tenant_admin bloccato sul portale owner');
  });

  test('8.4 — tenant_admin non accede agli endpoint admin', async () => {
    expect((await apiGet(tokenAdmin, '/admin/tenants')).status).toBe(403);
    console.log('✅ tenant_admin bloccato su /api/admin/**');
  });

  test('8.5 — super_admin accede agli endpoint admin', async () => {
    expect((await apiGet(tokenSuper, '/admin/tenants')).status).toBe(200);
    console.log('✅ super_admin accede a /api/admin/**');
  });

  // ── SEZIONE B — Isolamento tenant ────────────────────────────────────────

  test('8.6 — tenant_admin non vede risorse di altri tenant', async () => {
    // Non è super_admin: la lista dei tenant gli è preclusa
    expect((await apiGet(tokenAdmin, '/admin/tenants')).status).toBe(403);

    const me = (await apiGet<Utente>(tokenAdmin, '/auth/me')).body;
    expect(me.ruolo).toBe('tenant_admin');

    // La lista prenotazioni non espone fk_tenant_id — e fa bene. L'appartenenza si
    // verifica per via indiretta: ogni prenotazione è di un proprietario del tenant
    // corrente, e il dettaglio riporta il fkTenantId del chiamante.
    const bookings = (await apiGet<BookingRow[]>(tokenAdmin, '/bookings')).body;
    expect(bookings.length).toBeGreaterThan(0);

    const ownerIdsDelTenant = new Set(
      (await apiGet<OwnerRow[]>(tokenAdmin, '/owners')).body.map(o => o.id));
    const estranei = bookings.filter(b => !ownerIdsDelTenant.has(b.fkOwnerId));
    expect(estranei, `prenotazioni di proprietari fuori dal tenant: `
      + `${estranei.map(b => b.externalBookingId).join(', ')}`).toHaveLength(0);

    const dettaglio = (await apiGet<{ fkTenantId: number }>(
      tokenAdmin, `/bookings/${bookings[0].id}`)).body;
    expect(dettaglio.fkTenantId).toBe(me.fkTenantId);
    console.log(`✅ booking filtrati per tenant corrente (${bookings.length} prenotazioni, `
      + `tenant ${me.fkTenantId})`);
  });

  test('8.7 — Documento inesistente → 404, non 403', async () => {
    // Un 403 rivelerebbe che la risorsa esiste in un altro tenant
    expect((await apiGet(tokenAdmin, '/documents/999999')).status).toBe(404);
    console.log('✅ documento inesistente → 404 non 403');
  });

  // ── SEZIONE C — Isolamento owner ─────────────────────────────────────────

  test('8.8 — owner_user vede solo i propri booking', async () => {
    const me = (await apiGet<Utente>(tokenOwner, '/auth/me')).body;
    expect(me.ruolo).toBe('owner_user');
    expect(me.fkOwnerId, 'utente owner senza proprietario collegato').toBeTruthy();

    const bookings = (await apiGet<BookingRow[]>(tokenOwner, '/owner/bookings')).body;
    const altrui = bookings.filter(b => b.fkOwnerId !== me.fkOwnerId);
    expect(altrui, `prenotazioni di altri proprietari: `
      + `${altrui.map(b => `${b.externalBookingId}/${b.ownerName}`).join(', ')}`).toHaveLength(0);
    console.log(`✅ owner vede solo i propri booking (${bookings.length}, owner ${me.fkOwnerId})`);
  });

  test('8.9 — owner_user non accede alla CU di un altro proprietario', async () => {
    const me = (await apiGet<Utente>(tokenOwner, '/auth/me')).body;

    // La CU altrui si individua dal back-office, poi la si tenta dal portale owner
    const tutte = (await apiGet<CuRow[]>(tokenAdmin, '/cu')).body;
    const proprie = new Set((await apiGet<CuRow[]>(tokenOwner, '/owner/cu')).body.map(c => c.id));
    const altrui = tutte.find(c => !proprie.has(c.id));
    expect(altrui, 'serve una CU di un altro proprietario per questo test').toBeTruthy();

    const res = await apiGet(tokenOwner, `/owner/cu/${altrui!.id}/pdf`);
    expect(res.status, `CU ${altrui!.id} (${altrui!.ownerName}) non deve essere `
      + `scaricabile dall'owner ${me.fkOwnerId}`).toBe(403);
    console.log(`✅ CU di altro owner → 403 (CU ${altrui!.id} di ${altrui!.ownerName})`);
  });

  test('8.10 — owner_user non accede ai settlement di altri', async () => {
    const me = (await apiGet<Utente>(tokenOwner, '/auth/me')).body;
    const propri = (await apiGet<SettlementRow[]>(tokenOwner, '/owner/settlements')).body;

    // Lista vuota è un esito legittimo: il proprietario può non avere liquidazioni
    const altrui = propri.filter(
      s => s.fkOwnerId !== undefined && s.fkOwnerId !== me.fkOwnerId);
    expect(altrui, `liquidazioni di altri proprietari: ${altrui.map(s => s.id).join(', ')}`)
      .toHaveLength(0);

    // Controprova: il back-office ne vede di più (quelle degli altri proprietari)
    const tutti = (await apiGet<SettlementRow[]>(tokenAdmin, '/settlements')).body;
    expect(propri.length).toBeLessThanOrEqual(tutti.length);
    console.log(`✅ settlement filtrati per owner corrente `
      + `(owner ${me.fkOwnerId}: ${propri.length}, tenant: ${tutti.length})`);
  });

  // ── SEZIONE D — Redirect UI per ruolo ────────────────────────────────────

  test('8.11 — owner_user rediretto a /owner dopo il login', async ({ page }) => {
    await login(page, 'owner');
    await expect(page).toHaveURL(/\/owner$/);
    expect(page.url()).not.toContain('/dashboard');
  });

  test('8.12 — owner_user non entra nelle rotte tenant da URL diretto', async ({ page }) => {
    await login(page, 'owner');

    for (const rotta of ['/bookings', '/dashboard']) {
      await page.goto(rotta);
      await expect(page, `${rotta} deve rimandare al portale owner`).toHaveURL(/\/owner$/);
    }
    console.log('✅ rotte tenant reindirizzate a /owner per owner_user');
  });

  test('8.13 — tenant_admin rediretto a /dashboard dopo il login', async ({ page }) => {
    await login(page, 'tenantAdmin');
    await expect(page).toHaveURL(/\/dashboard$/);
    expect(page.url()).not.toContain('/owner');
  });

  test('8.14 — super_admin rediretto a /admin dopo il login', async ({ page }) => {
    await login(page, 'superAdmin');
    await expect(page).toHaveURL(/\/admin/);
  });

  test('8.15 — Accesso senza token respinto', async ({ request }) => {
    for (const p of ['/bookings', '/auth/me']) {
      const res = await request.get(`${BASE}${p}`);
      expect(res.status(), `${p} senza token`).toBe(403);
    }
    console.log('✅ endpoint protetti senza autenticazione → 403');
  });

  // ── SEZIONE E — Endpoint di test protetti ────────────────────────────────

  test('8.16 — owner_user non accede agli endpoint di test', async () => {
    // Body vuoto: se la chiamata passasse non cancellerebbe nulla (ownerId e
    // propertyId null), ma il punto è che non deve nemmeno arrivare al controller.
    const res = await apiDelete(tokenOwner, '/test/cleanup-anagrafica', {});
    expect(res.status, 'gli endpoint distruttivi di test non vanno esposti a owner_user')
      .toBe(403);
    console.log('✅ endpoint test protetti da owner_user');
  });

  test('8.17 — TestRunnerController è annotato @Profile("local")', async () => {
    const root = path.resolve(test.info().project.testDir, '../../..');
    const sorgente = readFileSync(
      path.join(root, 'src/main/java/it/gavia/sostitutoincloud/controller/TestRunnerController.java'),
      'utf-8');

    expect(sorgente, 'senza @Profile("local") gli endpoint di test esisterebbero anche in prod')
      .toContain('@Profile("local")');
    console.log('✅ TestRunnerController annotato con @Profile(local)');
  });

});

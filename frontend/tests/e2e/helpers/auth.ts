import { Page } from '@playwright/test';

/** Utenti di sviluppo locale — validi SOLO in locale (vedi CLAUDE.md). */
export const USERS = {
  superAdmin: {
    email: 'superadmin@sostitutoincloud.it',
    password: 'atena',
    role: 'super_admin',
  },
  tenantAdmin: {
    email: 'admin@casavacanze.it',
    password: 'atena2026',
    role: 'tenant_admin',
  },
  owner: {
    email: 'proprietario@email.it',
    password: 'atena',
    role: 'owner_user',
  },
};

/**
 * Login dalla pagina /login. Qui getByLabel funziona: la pagina Login usa
 * Label htmlFor="email"/"password" con gli id corrispondenti sugli Input.
 */
export async function login(page: Page, user: keyof typeof USERS): Promise<void> {
  const u = USERS[user];
  await page.goto('/login');
  await page.getByLabel('Email').fill(u.email);
  // exact: il pulsante "Mostra password" di PasswordInput ha un aria-label che contiene
  // "password" e renderebbe il locator ambiguo (strict mode violation).
  await page.getByLabel('Password', { exact: true }).fill(u.password);
  await page.getByRole('button', { name: 'Accedi' }).click();
  // Attendi il redirect post-login (la destinazione dipende dal ruolo)
  await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 10000 });
}

/** Logout dalla sidebar: il pulsante è etichettato "Esci", non "Logout". */
export async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: /esci|logout/i }).click();
  await page.waitForURL('**/login');
}

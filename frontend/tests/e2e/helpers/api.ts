import { request } from '@playwright/test';

export const BASE = 'http://localhost:8081/sostitutoincloud/api';

/**
 * Esito di una chiamata API con il body già deserializzato.
 *
 * NB: APIRequestContext.dispose() scarta i body di tutte le risposte del contesto,
 * quindi il body va letto PRIMA di chiudere il contesto — per questo gli helper
 * restituiscono i dati e non l'APIResponse.
 */
export interface ApiResult<T = unknown> {
  status: number;
  ok: boolean;
  body: T;
}

export async function getToken(email: string, password: string): Promise<string> {
  const ctx = await request.newContext();
  try {
    const res = await ctx.post(`${BASE}/public/login`, { data: { email, password } });
    const testo = await res.text();
    if (!res.ok()) {
      throw new Error(`Login fallito per ${email}: HTTP ${res.status()} ${testo}`);
    }
    return JSON.parse(testo).token;
  } finally {
    await ctx.dispose();
  }
}

type Metodo = 'get' | 'post' | 'delete' | 'patch';

async function chiamata<T>(
  token: string,
  metodo: Metodo,
  path: string,
  body?: object,
): Promise<ApiResult<T>> {
  const ctx = await request.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
  try {
    const res = await ctx[metodo](`${BASE}${path}`, body ? { data: body } : undefined);
    const testo = await res.text();
    let parsed: unknown = testo;
    try {
      parsed = testo ? JSON.parse(testo) : null;
    } catch {
      /* risposta non JSON: resta il testo grezzo */
    }
    return { status: res.status(), ok: res.ok(), body: parsed as T };
  } finally {
    await ctx.dispose();
  }
}

export function apiGet<T>(token: string, path: string): Promise<ApiResult<T>> {
  return chiamata<T>(token, 'get', path);
}

export function apiPost<T>(token: string, path: string, body: object): Promise<ApiResult<T>> {
  return chiamata<T>(token, 'post', path, body);
}

export function apiPatch<T>(token: string, path: string, body: object): Promise<ApiResult<T>> {
  return chiamata<T>(token, 'patch', path, body);
}

export function apiDelete<T>(token: string, path: string, body?: object): Promise<ApiResult<T>> {
  return chiamata<T>(token, 'delete', path, body);
}

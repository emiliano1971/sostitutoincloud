import { getConfig } from '@/config/AppConfig';

const TOKEN_KEY = 'auth_token';

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}

function apiUrl(path: string): string {
  const base = getConfig().apiBaseUrl;
  return `${base}${path.startsWith('/') ? path : '/' + path}`;
}

function buildHeaders(): Record<string, string> {
  const token = getToken();
  return {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
  };
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    const text = await res.text().catch(() => '');
    let message = 'UNAUTHORIZED';
    try {
      const json = JSON.parse(text);
      if (json.error) message = json.error;
    } catch { /* non-JSON body */ }
    throw new Error(message);
  }
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    let message = text;
    try {
      const json = JSON.parse(text);
      if (json.message) message = json.message;
    } catch { /* non-JSON body */ }
    throw new Error(message);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

export async function get<T>(path: string): Promise<T> {
  const res = await fetch(apiUrl(path), { headers: buildHeaders() });
  return handleResponse<T>(res);
}

export async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: 'POST',
    headers: buildHeaders(),
    body: JSON.stringify(body),
  });
  return handleResponse<T>(res);
}

export async function put<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: 'PUT',
    headers: buildHeaders(),
    body: JSON.stringify(body),
  });
  return handleResponse<T>(res);
}

export async function patch<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: 'PATCH',
    headers: buildHeaders(),
    body: JSON.stringify(body),
  });
  return handleResponse<T>(res);
}

export async function del<T>(path: string): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: 'DELETE',
    headers: buildHeaders(),
  });
  return handleResponse<T>(res);
}

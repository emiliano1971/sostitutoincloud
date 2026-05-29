import { getConfig } from '@/config/AppConfig';

let _email: string | null = null;
let _password: string | null = null;

export function setCredentials(email: string, password: string): void {
  _email = email;
  _password = password;
}

export function clearCredentials(): void {
  _email = null;
  _password = null;
}

export function getAuthHeader(): string | null {
  if (_email && _password) {
    return 'Basic ' + btoa(_email + ':' + _password);
  }
  return null;
}

function apiUrl(path: string): string {
  const base = getConfig().apiBaseUrl;
  return `${base}${path.startsWith('/') ? path : '/' + path}`;
}

function buildHeaders(): Record<string, string> {
  const authHeader = getAuthHeader();
  return {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    ...(authHeader ? { 'Authorization': authHeader } : {}),
  };
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (res.status === 401) {
    throw new Error('UNAUTHORIZED');
  }
  if (!res.ok) {
    const error = await res.text().catch(() => res.statusText);
    throw new Error(`HTTP ${res.status}: ${error}`);
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

export async function del<T>(path: string): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: 'DELETE',
    headers: buildHeaders(),
  });
  return handleResponse<T>(res);
}

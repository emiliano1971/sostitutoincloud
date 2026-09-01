import { get, post, put, patch, del } from '@/lib/apiClient';

export interface UtenteListItem {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  ruolo: string;
  attivo: boolean;
  ownerName?: string;
  createdAt: string;
  lastLogin?: string;
}

export interface UtenteCreateRequest {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
  ruolo: string;
  fkOwnerId?: number;
}

export interface UtenteUpdateRequest {
  email: string;
  firstName: string;
  lastName: string;
}

export async function getUsers(): Promise<UtenteListItem[]> {
  return get<UtenteListItem[]>('/users');
}

export async function createUser(data: UtenteCreateRequest): Promise<UtenteListItem> {
  return post<UtenteListItem>('/users', data);
}

/** Modifica dei dati anagrafici. Lo stato si cambia con updateUserStatus(). */
export async function updateUser(id: number, data: UtenteUpdateRequest): Promise<UtenteListItem> {
  return put<UtenteListItem>(`/users/${id}`, data);
}

export async function updateUserStatus(id: number, attivo: boolean): Promise<UtenteListItem> {
  return patch<UtenteListItem>(`/users/${id}/status`, { attivo });
}

export async function deleteUser(id: number): Promise<void> {
  return del<void>(`/users/${id}`);
}

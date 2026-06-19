import { get, post, patch, del } from '@/lib/apiClient';

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

export async function getUsers(): Promise<UtenteListItem[]> {
  return get<UtenteListItem[]>('/users');
}

export async function createUser(data: UtenteCreateRequest): Promise<UtenteListItem> {
  return post<UtenteListItem>('/users', data);
}

export async function updateUserStatus(id: number, attivo: boolean): Promise<UtenteListItem> {
  return patch<UtenteListItem>(`/users/${id}/status`, { attivo });
}

export async function deleteUser(id: number): Promise<void> {
  return del<void>(`/users/${id}`);
}

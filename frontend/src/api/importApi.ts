import { getConfig } from '@/config/AppConfig';
import { getToken } from '@/lib/apiClient';
import { get, post, put, del } from '@/lib/apiClient';

export interface ImportPreviewRow {
  rowNumber: number;
  externalBookingId: string;
  guestName: string;
  propertyCode: string;
  propertyName?: string;
  fkPropertyId?: number;
  channelCode: string;
  channelName?: string;
  checkinDate: string;
  checkoutDate: string;
  grossAmount: number;
  status: 'nuova' | 'duplicata' | 'errore';
  errorMessage?: string;
  splitWarnings?: string[];
}

export interface ImportPreview {
  fileName: string;
  totalRows: number;
  newCount: number;
  dupeCount: number;
  errorCount: number;
  warningCount?: number;
  excludedCount: number;
  rows: ImportPreviewRow[];
  importSessionId: string;
}

export interface ImportUploadResponse {
  bookingSessionId: string;
  guestSessionId?: string;
  bookingColumns: string[];
  guestColumns?: string[];
  suggestedBookingMapping: Record<string, string>;
  suggestedGuestMapping?: Record<string, string>;
  statoColumnValues?: string[];
}

export interface ImportColumnMapping {
  bookingMapping: Record<string, string>;
  guestMapping?: Record<string, string>;
  statiDaEscludere?: string[];
}

export interface ImportPreviewV2Request {
  bookingSessionId: string;
  guestSessionId?: string;
  mapping: ImportColumnMapping;
}

export interface ImportResult {
  imported: number;
  skipped: number;
  errors: number;
  errorMessages: string[];
}

export async function uploadImportFile(file: File): Promise<ImportPreview> {
  const base = getConfig().apiBaseUrl;
  const url = `${base}/bookings/import`;
  const token = getToken();
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
    body: formData,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    let message = text;
    try { const j = JSON.parse(text); if (j.error) message = j.error; } catch { /* */ }
    throw new Error(message);
  }
  return res.json();
}

export async function confirmImport(
  importSessionId: string,
  selectedExternalIds: string[]
): Promise<ImportResult> {
  return post<ImportResult>('/bookings/import/confirm', { importSessionId, selectedExternalIds });
}

// ── Import V2: doppio file (prenotazioni + ospiti) + mapping colonne manuale ──

export async function uploadImportFiles(
  bookingFile: File,
  guestFile?: File,
  headerRow: number = 0
): Promise<ImportUploadResponse> {
  const base = getConfig().apiBaseUrl;
  const url = `${base}/bookings/import/upload`;
  const token = getToken();
  const formData = new FormData();
  formData.append('bookingFile', bookingFile);
  if (guestFile) formData.append('guestFile', guestFile);
  formData.append('headerRow', headerRow.toString());
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
    },
    body: formData,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    let message = text;
    try { const j = JSON.parse(text); if (j.error) message = j.error; } catch { /* */ }
    throw new Error(message);
  }
  return res.json();
}

export async function previewImportV2(
  data: ImportPreviewV2Request
): Promise<ImportPreview> {
  return post<ImportPreview>('/bookings/import/preview-v2', data);
}

// ── Template di mapping import ────────────────────────────────────────────────

export interface ImportTemplate {
  id: number;
  nome: string;
  descrizione?: string;
  headerRow: number;
  bookingMapping: Record<string, string>;
  guestMapping: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface ImportTemplateSave {
  id?: number;
  nome: string;
  descrizione?: string;
  headerRow: number;
  bookingMapping: Record<string, string>;
  guestMapping: Record<string, string>;
}

export async function getImportTemplates(): Promise<ImportTemplate[]> {
  return get<ImportTemplate[]>('/import-templates');
}

export async function saveImportTemplate(data: ImportTemplateSave): Promise<ImportTemplate> {
  return data.id != null
    ? put<ImportTemplate>(`/import-templates/${data.id}`, data)
    : post<ImportTemplate>('/import-templates', data);
}

export async function deleteImportTemplate(id: number): Promise<void> {
  await del<{ message: string }>(`/import-templates/${id}`);
}

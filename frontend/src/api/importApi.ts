import { getConfig } from '@/config/AppConfig';
import { getToken } from '@/lib/apiClient';
import { post } from '@/lib/apiClient';

export interface ImportPreviewRow {
  rowNumber: number;
  externalBookingId: string;
  guestName: string;
  propertyCode: string;
  propertyName?: string;
  channelCode: string;
  channelName?: string;
  checkinDate: string;
  checkoutDate: string;
  grossAmount: number;
  status: 'nuova' | 'duplicata' | 'errore';
  errorMessage?: string;
}

export interface ImportPreview {
  fileName: string;
  totalRows: number;
  newCount: number;
  dupeCount: number;
  errorCount: number;
  rows: ImportPreviewRow[];
  importSessionId: string;
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

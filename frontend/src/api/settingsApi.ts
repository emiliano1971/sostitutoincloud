import { get, put } from '@/lib/apiClient';

export interface TenantSettingsDTO {
  // dati aziendali
  legalName: string;
  displayName: string;
  taxCode: string;
  vatNumber?: string;
  administrativeEmail: string;
  pec?: string;
  phone?: string;
  legalAddress: string;
  // parametri fiscali
  withholdingRatePrimary: number;
  withholdingRateSecondary: number;
  codiceTributoF24: string;
  documentWindowDays: number;
  cedolareSeccaEnabled: boolean;
  // policy documentali
  sdiAutoSend: boolean;
  derogaRicevutaEnabled: boolean;
  numerazioneAutomatica: boolean;
  // notifiche
  alertScadenzeDocumenti: boolean;
  alertScadenzeF24: boolean;
  notificheEmail: boolean;
}

export async function getSettings(): Promise<TenantSettingsDTO> {
  return get<TenantSettingsDTO>('/settings');
}

export async function updateSettings(data: Partial<TenantSettingsDTO>): Promise<TenantSettingsDTO> {
  return put<TenantSettingsDTO>('/settings', data);
}

import { get } from '@/lib/apiClient';

export interface F24ListItem {
  id: number;
  period: string;
  codiceTributo: string;
  totalAmount: number;
  withholdingsCount: number;
  stato: string;
  deadlineDate: string;
  paymentDate?: string;
  createdAt: string;
}

export interface F24Detail extends F24ListItem {
  fkTenantId: number;
  tenantLegalName: string;
  tenantTaxCode: string;
  tenantAddress: string;
  updatedAt: string;
}

export async function getF24List(): Promise<F24ListItem[]> {
  return get<F24ListItem[]>('/f24');
}

export async function getF24ById(id: number): Promise<F24Detail> {
  return get<F24Detail>(`/f24/${id}`);
}

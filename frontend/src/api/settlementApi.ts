import { get } from '@/lib/apiClient';

export interface SettlementListItem {
  id: number;
  ownerName: string;
  period: string;
  totalAmount: number;
  withholdingAmount: number;
  netAmount: number;
  bookingsCount: number;
  stato: string;
  paymentDate?: string;
  createdAt: string;
}

export interface SettlementBookingItem {
  bookingId: number;
  externalBookingId: string;
  propertyName: string;
  checkinDate: string;
  checkoutDate: string;
  grossAmount: number;
  ownerNetAmount: number;
  withholdingAmount: number;
}

export interface SettlementDetail extends SettlementListItem {
  fkTenantId: number;
  fkOwnerId: number;
  updatedAt: string;
  bookings: SettlementBookingItem[];
}

export async function getSettlements(params?: {
  ownerId?: number;
  period?: string;
}): Promise<SettlementListItem[]> {
  if (!params || Object.keys(params).length === 0) {
    return get<SettlementListItem[]>('/settlements');
  }
  const qs = new URLSearchParams();
  if (params.ownerId !== undefined) qs.set('ownerId', String(params.ownerId));
  if (params.period) qs.set('period', params.period);
  return get<SettlementListItem[]>(`/settlements?${qs.toString()}`);
}

export async function getSettlementById(id: number): Promise<SettlementDetail> {
  return get<SettlementDetail>(`/settlements/${id}`);
}

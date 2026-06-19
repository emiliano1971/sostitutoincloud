import { get } from '@/lib/apiClient';

export interface AuditLogItem {
  id: number;
  fkTenantId?: number;
  fkUtenteId?: number;
  userEmail: string;
  action: string;
  entityType: string;
  entityId?: number;
  details: string;
  ipAddress?: string;
  createdAt: string;
}

export async function getAuditLog(params?: {
  q?: string;
  action?: string;
  entity?: string;
  page?: number;
  size?: number;
}): Promise<AuditLogItem[]> {
  if (!params || Object.keys(params).length === 0) {
    return get<AuditLogItem[]>('/audit-log');
  }
  const qs = new URLSearchParams();
  if (params.q) qs.set('q', params.q);
  if (params.action) qs.set('action', params.action);
  if (params.entity) qs.set('entity', params.entity);
  if (params.page !== undefined) qs.set('page', String(params.page));
  if (params.size !== undefined) qs.set('size', String(params.size));
  return get<AuditLogItem[]>(`/audit-log?${qs.toString()}`);
}

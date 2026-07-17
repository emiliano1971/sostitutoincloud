import { get, post, put, del } from '@/lib/apiClient';

export interface ContractRule {
  id: number;
  fkPropertyId: number;
  fkCanaleOtaId?: number;
  canaleName?: string;
  tipo: string;
  tipoLabel: string;
  calcMode: string;
  calcModeLabel: string;
  valore: number;
  isRemainder: boolean;
  ordine: number;
}

export interface ContractRuleCreate {
  fkPropertyId: number;
  fkCanaleOtaId?: number;
  tipo: string;
  calcMode: string;
  valore: number;
  isRemainder: boolean;
  ordine: number;
}

export async function getContractRules(propertyId: number): Promise<ContractRule[]> {
  return get<ContractRule[]>(`/properties/${propertyId}/contracts`);
}

export async function createContractRule(
  propertyId: number,
  data: ContractRuleCreate,
): Promise<ContractRule> {
  return post<ContractRule>(`/properties/${propertyId}/contracts`, data);
}

export async function updateContractRule(
  propertyId: number,
  ruleId: number,
  data: ContractRuleCreate,
): Promise<ContractRule> {
  return put<ContractRule>(`/properties/${propertyId}/contracts/${ruleId}`, data);
}

export async function deleteContractRule(
  propertyId: number,
  ruleId: number,
): Promise<void> {
  return del<void>(`/properties/${propertyId}/contracts/${ruleId}`);
}

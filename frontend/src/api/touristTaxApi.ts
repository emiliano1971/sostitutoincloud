import { get, post, put, patch } from '@/lib/apiClient';

// ---- Tipi allineati ai DTO backend (it.gavia.sostitutoincloud.dto.touristtax) ----

export interface TouristTaxRuleListItem {
  id: number;
  comune: string;
  provincia: string;
  region?: string | null;
  importoPerNotte: number;
  maxNotti: number | null;
  maxAmountPerPerson?: number | null;
  attivo: boolean;
  validaDal?: string | null;
  validaAl?: string | null;
  fascieEtaCount: number;
  stagioniCount: number;
  zoneCount: number;
}

export interface TouristTaxAgeBand {
  label: string;
  minAge: number;
  maxAge: number;
  reductionPct: number;
}

export interface TouristTaxSeason {
  label: string;
  startMonth: number;
  startDay: number;
  endMonth: number;
  endDay: number;
  reductionPct: number;
}

export interface TouristTaxZone {
  label: string;
  reductionPct: number;
}

export interface TouristTaxRuleDetail extends TouristTaxRuleListItem {
  fascieEta: TouristTaxAgeBand[];
  stagioni: TouristTaxSeason[];
  zone: TouristTaxZone[];
  exemptions?: string | null;
  notes?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface GuestTax {
  age: number;
  nightsCharged: number;
  ratePerNight: number;
  total: number;
  esente: boolean;
}

export interface TouristTaxCalculation {
  total: number;
  perPerson: GuestTax[];
}

export interface TouristTaxCalculateRequest {
  nights: number;
  checkinDate: string;        // ISO yyyy-MM-dd
  zona?: string | null;
  guestAges: number[];
}

export interface TouristTaxRuleCreateRequest {
  comune: string;
  provincia: string;
  region?: string | null;
  importoPerNotte: number;
  maxNotti?: number | null;
  maxAmountPerPerson?: number | null;
  validaDal: string;          // ISO yyyy-MM-dd
  validaAl?: string | null;
  exemptions?: string | null;
  notes?: string | null;
  fascieEta?: TouristTaxAgeBand[];
  stagioni?: TouristTaxSeason[];
  zone?: TouristTaxZone[];
}

// ---- API ----

export async function getTouristTaxRules(): Promise<TouristTaxRuleListItem[]> {
  return get<TouristTaxRuleListItem[]>('/tourist-tax');
}

export async function getTouristTaxRule(id: number): Promise<TouristTaxRuleDetail> {
  return get<TouristTaxRuleDetail>(`/tourist-tax/${id}`);
}

export async function createTouristTaxRule(
  data: TouristTaxRuleCreateRequest,
): Promise<TouristTaxRuleDetail> {
  return post<TouristTaxRuleDetail>('/tourist-tax', data);
}

export async function updateTouristTaxRule(
  id: number,
  data: TouristTaxRuleCreateRequest,
): Promise<TouristTaxRuleDetail> {
  return put<TouristTaxRuleDetail>(`/tourist-tax/${id}`, data);
}

export async function updateTouristTaxStatus(
  id: number,
  attivo: boolean,
): Promise<TouristTaxRuleDetail> {
  return patch<TouristTaxRuleDetail>(`/tourist-tax/${id}/status`, { attivo });
}

export async function calculateTouristTax(
  id: number,
  req: TouristTaxCalculateRequest,
): Promise<TouristTaxCalculation> {
  return post<TouristTaxCalculation>(`/tourist-tax/${id}/calculate`, req);
}

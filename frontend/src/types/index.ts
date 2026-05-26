// ---- Enums ----
export type UserRole = 'super_admin' | 'tenant_admin' | 'pm_user' | 'owner_user';
export type TenantStatus = 'draft' | 'active' | 'suspended' | 'closed';
export type BookingStatus = 'imported' | 'enriched' | 'ready' | 'doc_issued' | 'settled' | 'cancelled';
export type DocumentStatus = 'draft' | 'ready' | 'sent_sdi' | 'accepted' | 'rejected' | 'error';
export type PaymentStatus = 'pending' | 'received' | 'failed';
export type SettlementStatus = 'pending' | 'calculated' | 'approved' | 'paid';
export type OwnerType = 'persona_fisica' | 'piva' | 'societa';
export type FiscalRegime = 'cedolare_secca' | 'iva_10' | 'ordinario';
export type F24Status = 'draft' | 'ready' | 'sent' | 'paid' | 'error';

// ---- Entities ----
export interface Tenant {
  tenant_id: string;
  legal_name: string;
  display_name: string;
  tax_code: string;
  vat_number: string;
  tenant_status: TenantStatus;
  administrative_email: string;
  pec: string;
  phone: string;
  legal_address: string;
  created_at: string;
  activated_at?: string;
  properties_count: number;
  owners_count: number;
  bookings_count: number;
}

export interface OwnerProfile {
  owner_id: string;
  tenant_id: string;
  owner_type: OwnerType;
  first_name: string;
  last_name: string;
  legal_name?: string;
  tax_code: string;
  vat_number?: string;
  fiscal_regime: FiscalRegime;
  email: string;
  phone: string;
  iban: string;
  status: 'active' | 'inactive';
  properties_count: number;
  created_at: string;
}

export interface Property {
  property_id: string;
  tenant_id: string;
  owner_id: string;
  pm_id: string;
  internal_code: string;
  display_name: string;
  address: string;
  city: string;
  region: string;
  property_type: string;
  cin_code: string;
  ota_codes: {
    airbnb_id?: string;
    booking_id?: string;
    vrbo_id?: string;
    tripadvisor_id?: string;
    expedia_id?: string;
  };
  status: 'active' | 'inactive';
  listings_count: number;
  bookings_count: number;
  created_at: string;
}

export interface Booking {
  booking_id: string;
  tenant_id: string;
  property_id: string;
  property_name: string;
  owner_name: string;
  guest_name: string;
  external_booking_id: string;
  channel_name: string;
  guest_tax_code: string;
  checkin_date: string;
  checkout_date: string;
  nights: number;
  guests: number;
  gross_amount: number;
  ota_commission_amount: number;
  cleaning_amount: number;
  pm_fee_amount: number;
  owner_net_amount: number;
  withholding_amount: number;
  tourist_tax_amount: number;
  tourist_tax_included_in_gross: boolean;
  tourist_tax_collection: 'contanti' | 'payment_link' | 'altro';
  booking_status: BookingStatus;
  payment_status: PaymentStatus;
  document_status: DocumentStatus;
  settlement_status: SettlementStatus;
  fiscal_scenario_code: string;
  created_at: string;
}

export interface FiscalDocument {
  document_id: string;
  tenant_id: string;
  booking_id: string;
  document_type: 'fattura' | 'ricevuta' | 'nota_credito';
  document_number: string;
  issue_date: string;
  recipient_name: string;
  total_amount: number;
  vat_amount: number;
  status: DocumentStatus;
  sdi_status?: string;
  sdi_identifier?: string;
  property_name: string;
  channel_name: string;
}

export interface Settlement {
  settlement_id: string;
  tenant_id: string;
  owner_id: string;
  owner_name: string;
  period: string;
  total_amount: number;
  withholding_amount: number;
  net_amount: number;
  bookings_count: number;
  status: SettlementStatus;
  payment_date?: string;
  created_at: string;
}

export interface F24Record {
  f24_id: string;
  tenant_id: string;
  period: string;
  tax_code: string;
  total_amount: number;
  withholdings_count: number;
  status: F24Status;
  deadline_date: string;
  payment_date?: string;
  created_at: string;
}

export interface CURecord {
  cu_id: string;
  tenant_id: string;
  owner_id: string;
  owner_name: string;
  tax_year: number;
  total_compensi: number;
  total_ritenute: number;
  status: 'draft' | 'generated' | 'sent' | 'delivered';
  generated_at?: string;
  created_at: string;
}

export interface AuditLogEntry {
  log_id: string;
  tenant_id?: string;
  user_email: string;
  action: string;
  entity_type: string;
  entity_id: string;
  details: string;
  ip_address: string;
  created_at: string;
}

export interface DashboardKPI {
  label: string;
  value: string | number;
  change?: number;
  changeLabel?: string;
  icon?: string;
}

export interface AlertItem {
  id: string;
  type: 'warning' | 'error' | 'info';
  message: string;
  action?: string;
  created_at: string;
}

export interface UserContext {
  user_id: string;
  email: string;
  first_name: string;
  last_name: string;
  role: UserRole;
  tenant_id?: string;
  tenant_name?: string;
  owner_id?: string;
}

export interface OTAChannel {
  ota_id: string;
  name: string;
  logo_color: string;
  default_commission_pct: number;
  tourist_tax_included: boolean;
  tourist_tax_collection: 'contanti' | 'payment_link' | 'altro';
  status: 'active' | 'inactive';
}

export const mockOTARegistry: OTAChannel[] = [
  { ota_id: 'ota1', name: 'Airbnb', logo_color: '#FF5A5F', default_commission_pct: 15, tourist_tax_included: true, tourist_tax_collection: 'payment_link', status: 'active' },
  { ota_id: 'ota2', name: 'Booking.com', logo_color: '#003580', default_commission_pct: 18, tourist_tax_included: false, tourist_tax_collection: 'contanti', status: 'active' },
  { ota_id: 'ota3', name: 'Vrbo', logo_color: '#0E4FA1', default_commission_pct: 12, tourist_tax_included: false, tourist_tax_collection: 'contanti', status: 'active' },
  { ota_id: 'ota4', name: 'Expedia', logo_color: '#00355F', default_commission_pct: 16, tourist_tax_included: true, tourist_tax_collection: 'payment_link', status: 'active' },
  { ota_id: 'ota5', name: 'TripAdvisor', logo_color: '#00AF87', default_commission_pct: 14, tourist_tax_included: false, tourist_tax_collection: 'contanti', status: 'active' },
  { ota_id: 'ota6', name: 'Google Travel', logo_color: '#4285F4', default_commission_pct: 10, tourist_tax_included: false, tourist_tax_collection: 'payment_link', status: 'active' },
  { ota_id: 'ota7', name: 'Diretto', logo_color: '#6B7280', default_commission_pct: 0, tourist_tax_included: false, tourist_tax_collection: 'contanti', status: 'active' },
];

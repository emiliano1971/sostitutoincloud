export interface TouristTaxAgeBand {
  label: string;
  min_age: number;
  max_age: number;
  reduction_pct: number; // 100 = exempt, 50 = half price, 0 = full price
}

export interface TouristTaxSeason {
  label: string;
  start_month: number; // 1-12
  start_day: number;
  end_month: number;
  end_day: number;
  reduction_pct: number; // 0 = full price (alta stagione), 30 = bassa stagione
}

export interface TouristTaxZone {
  label: string;
  reduction_pct: number;
}

export interface TouristTaxRule {
  rule_id: string;
  municipality: string;
  province: string;
  region: string;
  base_rate_per_person_per_night: number;
  max_nights_per_stay: number | null; // null = unlimited
  max_amount_per_person: number | null; // null = unlimited
  age_bands: TouristTaxAgeBand[];
  seasons: TouristTaxSeason[];
  zones: TouristTaxZone[];
  exemptions: string[];
  notes: string;
  status: 'active' | 'inactive';
}

export const mockTouristTaxRules: TouristTaxRule[] = [
  {
    rule_id: 'tt-venezia',
    municipality: 'Venezia',
    province: 'VE',
    region: 'Veneto',
    base_rate_per_person_per_night: 5.00,
    max_nights_per_stay: 5,
    max_amount_per_person: null,
    age_bands: [
      { label: 'Sotto i 10 anni', min_age: 0, max_age: 9, reduction_pct: 100 },
      { label: '10-16 anni', min_age: 10, max_age: 16, reduction_pct: 50 },
      { label: 'Adulti (17+)', min_age: 17, max_age: 999, reduction_pct: 0 },
    ],
    seasons: [
      { label: 'Alta stagione', start_month: 2, start_day: 1, end_month: 12, end_day: 31, reduction_pct: 0 },
      { label: 'Bassa stagione (Gennaio)', start_month: 1, start_day: 1, end_month: 1, end_day: 31, reduction_pct: 30 },
    ],
    zones: [
      { label: 'Centro Storico / Giudecca', reduction_pct: 0 },
      { label: 'Isole della laguna', reduction_pct: 20 },
      { label: 'Terraferma (Mestre)', reduction_pct: 30 },
    ],
    exemptions: [
      'Minori sotto i 10 anni',
      'Residenti nel Comune di Venezia',
      'Malati e accompagnatori in strutture sanitarie',
      'Forze armate e forze dell\'ordine in servizio',
      'Persone con disabilità',
      'Volontari in servizio civile',
      'Autisti pullman e accompagnatori turistici (gruppi ≥25)',
    ],
    notes: 'Le riduzioni sono cumulabili. Calcolo successivo: es. base 5€, riduzione 20% + 50% = 5€ × 0.80 × 0.50 = 2€. Vigente dal 01/04/2025 (DCC 77/2024).',
    status: 'active',
  },
  {
    rule_id: 'tt-roma',
    municipality: 'Roma',
    province: 'RM',
    region: 'Lazio',
    base_rate_per_person_per_night: 3.50,
    max_nights_per_stay: 10,
    max_amount_per_person: 35,
    age_bands: [
      { label: 'Sotto i 10 anni', min_age: 0, max_age: 9, reduction_pct: 100 },
      { label: 'Adulti (10+)', min_age: 10, max_age: 999, reduction_pct: 0 },
    ],
    seasons: [
      { label: 'Tutto l\'anno', start_month: 1, start_day: 1, end_month: 12, end_day: 31, reduction_pct: 0 },
    ],
    zones: [
      { label: 'Tutto il territorio', reduction_pct: 0 },
    ],
    exemptions: [
      'Minori sotto i 10 anni',
      'Residenti nel Comune di Roma',
    ],
    notes: 'Tariffa per locazioni turistiche (extra-alberghiero). Max 10 notti, cap €35 per persona.',
    status: 'active',
  },
  {
    rule_id: 'tt-firenze',
    municipality: 'Firenze',
    province: 'FI',
    region: 'Toscana',
    base_rate_per_person_per_night: 5.50,
    max_nights_per_stay: 7,
    max_amount_per_person: null,
    age_bands: [
      { label: 'Sotto i 12 anni', min_age: 0, max_age: 11, reduction_pct: 100 },
      { label: 'Adulti (12+)', min_age: 12, max_age: 999, reduction_pct: 0 },
    ],
    seasons: [
      { label: 'Tutto l\'anno', start_month: 1, start_day: 1, end_month: 12, end_day: 31, reduction_pct: 0 },
    ],
    zones: [
      { label: 'Tutto il territorio', reduction_pct: 0 },
    ],
    exemptions: ['Minori sotto i 12 anni', 'Residenti nel Comune di Firenze'],
    notes: 'Tariffa per appartamenti ammobiliati ad uso turistico.',
    status: 'active',
  },
  {
    rule_id: 'tt-napoli',
    municipality: 'Napoli',
    province: 'NA',
    region: 'Campania',
    base_rate_per_person_per_night: 3.00,
    max_nights_per_stay: 14,
    max_amount_per_person: null,
    age_bands: [
      { label: 'Sotto i 14 anni', min_age: 0, max_age: 13, reduction_pct: 100 },
      { label: 'Adulti (14+)', min_age: 14, max_age: 999, reduction_pct: 0 },
    ],
    seasons: [
      { label: 'Tutto l\'anno', start_month: 1, start_day: 1, end_month: 12, end_day: 31, reduction_pct: 0 },
    ],
    zones: [{ label: 'Tutto il territorio', reduction_pct: 0 }],
    exemptions: ['Minori sotto i 14 anni'],
    notes: 'Tariffa per case e appartamenti per vacanze.',
    status: 'active',
  },
  {
    rule_id: 'tt-genova',
    municipality: 'Genova',
    province: 'GE',
    region: 'Liguria',
    base_rate_per_person_per_night: 2.50,
    max_nights_per_stay: 5,
    max_amount_per_person: null,
    age_bands: [
      { label: 'Sotto i 12 anni', min_age: 0, max_age: 11, reduction_pct: 100 },
      { label: 'Adulti (12+)', min_age: 12, max_age: 999, reduction_pct: 0 },
    ],
    seasons: [
      { label: 'Tutto l\'anno', start_month: 1, start_day: 1, end_month: 12, end_day: 31, reduction_pct: 0 },
    ],
    zones: [{ label: 'Tutto il territorio', reduction_pct: 0 }],
    exemptions: ['Minori sotto i 12 anni'],
    notes: 'Max 5 pernottamenti consecutivi.',
    status: 'active',
  },
];

// Calculate tourist tax for a booking
export function calculateTouristTax(params: {
  rule: TouristTaxRule;
  nights: number;
  guests: { age: number }[];
  checkinDate: Date;
  zone?: string;
}): { total: number; perPerson: { age: number; nightsCharged: number; ratePerNight: number; total: number }[] } {
  const { rule, nights, guests, checkinDate, zone } = params;
  const effectiveNights = rule.max_nights_per_stay ? Math.min(nights, rule.max_nights_per_stay) : nights;

  // Find season reduction
  const month = checkinDate.getMonth() + 1;
  const day = checkinDate.getDate();
  const seasonReduction = rule.seasons.find(s => {
    if (s.start_month <= s.end_month) {
      return (month > s.start_month || (month === s.start_month && day >= s.start_day)) &&
             (month < s.end_month || (month === s.end_month && day <= s.end_day));
    }
    return (month > s.start_month || (month === s.start_month && day >= s.start_day)) ||
           (month < s.end_month || (month === s.end_month && day <= s.end_day));
  })?.reduction_pct ?? 0;

  // Find zone reduction
  const zoneReduction = zone ? (rule.zones.find(z => z.label === zone)?.reduction_pct ?? 0) : 0;

  const perPerson = guests.map(guest => {
    const ageBand = rule.age_bands.find(b => guest.age >= b.min_age && guest.age <= b.max_age);
    const ageReduction = ageBand?.reduction_pct ?? 0;

    if (ageReduction >= 100) {
      return { age: guest.age, nightsCharged: 0, ratePerNight: 0, total: 0 };
    }

    // Cumulative reductions: apply sequentially
    let rate = rule.base_rate_per_person_per_night;
    if (ageReduction > 0) rate *= (1 - ageReduction / 100);
    if (seasonReduction > 0) rate *= (1 - seasonReduction / 100);
    if (zoneReduction > 0) rate *= (1 - zoneReduction / 100);

    rate = Math.round(rate * 100) / 100;
    let total = rate * effectiveNights;

    // Apply per-person cap
    if (rule.max_amount_per_person) {
      total = Math.min(total, rule.max_amount_per_person);
    }

    return { age: guest.age, nightsCharged: effectiveNights, ratePerNight: rate, total: Math.round(total * 100) / 100 };
  });

  return { total: Math.round(perPerson.reduce((s, p) => s + p.total, 0) * 100) / 100, perPerson };
}

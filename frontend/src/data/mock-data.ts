import type {
  Tenant, OwnerProfile, Property, Booking, FiscalDocument,
  Settlement, F24Record, CURecord, AuditLogEntry, AlertItem, UserContext,
} from '@/types';

// ---- User Contexts ----
export const mockUsers: UserContext[] = [
  { user_id: 'u1', email: 'superadmin@sostitutoincloud.it', first_name: 'Marco', last_name: 'Rossi', role: 'super_admin' },
  { user_id: 'u2', email: 'admin@casavacanze.it', first_name: 'Laura', last_name: 'Bianchi', role: 'tenant_admin', tenant_id: 't1', tenant_name: 'Casa Vacanze Italia SRL' },
  { user_id: 'u3', email: 'pm@casavacanze.it', first_name: 'Giovanni', last_name: 'Verdi', role: 'pm_user', tenant_id: 't1', tenant_name: 'Casa Vacanze Italia SRL' },
  { user_id: 'u4', email: 'proprietario@email.it', first_name: 'Anna', last_name: 'Moretti', role: 'owner_user', tenant_id: 't1', tenant_name: 'Casa Vacanze Italia SRL', owner_id: 'o1' },
];

// ---- Tenants ----
export const mockTenants: Tenant[] = [
  { tenant_id: 't1', legal_name: 'Casa Vacanze Italia SRL', display_name: 'Casa Vacanze Italia', tax_code: 'CVITRL80A01H501Z', vat_number: 'IT12345678901', tenant_status: 'active', administrative_email: 'admin@casavacanze.it', pec: 'casavacanze@pec.it', phone: '+39 06 1234567', legal_address: 'Via Roma 1, 00100 Roma RM', created_at: '2024-01-15', activated_at: '2024-01-20', properties_count: 5, owners_count: 3, bookings_count: 28 },
  { tenant_id: 't2', legal_name: 'Riviera Homes SAS', display_name: 'Riviera Homes', tax_code: 'RVRHMS90B02F205X', vat_number: 'IT98765432101', tenant_status: 'active', administrative_email: 'info@rivierahomes.it', pec: 'rivierahomes@pec.it', phone: '+39 010 9876543', legal_address: 'Corso Italia 55, 16121 Genova GE', created_at: '2024-03-10', activated_at: '2024-03-15', properties_count: 3, owners_count: 2, bookings_count: 15 },
  { tenant_id: 't3', legal_name: 'Toscana Property Management', display_name: 'Toscana PM', tax_code: 'TSCPM85C03D612Y', vat_number: 'IT11223344556', tenant_status: 'suspended', administrative_email: 'gestione@toscanapm.it', pec: 'toscanapm@pec.it', phone: '+39 055 5555555', legal_address: 'Piazza del Campo 12, 53100 Siena SI', created_at: '2024-06-01', properties_count: 2, owners_count: 1, bookings_count: 7 },
];

// ---- Owners ----
export const mockOwners: OwnerProfile[] = [
  { owner_id: 'o1', tenant_id: 't1', owner_type: 'persona_fisica', first_name: 'Anna', last_name: 'Moretti', tax_code: 'MRTANN85A41H501X', fiscal_regime: 'cedolare_secca', email: 'anna.moretti@email.it', phone: '+39 333 1111111', iban: 'IT60X0542811101000000123456', status: 'active', properties_count: 2, created_at: '2024-01-20' },
  { owner_id: 'o2', tenant_id: 't1', owner_type: 'persona_fisica', first_name: 'Roberto', last_name: 'Ferrari', tax_code: 'FRRRBT70M15F205Z', fiscal_regime: 'cedolare_secca', email: 'r.ferrari@email.it', phone: '+39 333 2222222', iban: 'IT60X0542811101000000654321', status: 'active', properties_count: 2, created_at: '2024-02-01' },
  { owner_id: 'o3', tenant_id: 't1', owner_type: 'persona_fisica', first_name: 'Lucia', last_name: 'Conti', tax_code: 'CNTLCU65T45D612W', fiscal_regime: 'cedolare_secca', email: 'lucia.conti@email.it', phone: '+39 333 3333333', iban: 'IT60X0542811101000000789012', status: 'active', properties_count: 1, created_at: '2024-03-15' },
  { owner_id: 'o4', tenant_id: 't2', owner_type: 'persona_fisica', first_name: 'Paolo', last_name: 'Russo', tax_code: 'RSSPAL80D20L219V', fiscal_regime: 'cedolare_secca', email: 'p.russo@email.it', phone: '+39 333 4444444', iban: 'IT60X0542811101000000345678', status: 'active', properties_count: 2, created_at: '2024-03-20' },
  { owner_id: 'o5', tenant_id: 't2', owner_type: 'persona_fisica', first_name: 'Elena', last_name: 'Galli', tax_code: 'GLLELN75S55F839Q', fiscal_regime: 'cedolare_secca', email: 'e.galli@email.it', phone: '+39 333 5555555', iban: 'IT60X0542811101000000901234', status: 'active', properties_count: 1, created_at: '2024-04-01' },
];

// ---- Properties ----
export const mockProperties: Property[] = [
  { property_id: 'p1', tenant_id: 't1', owner_id: 'o1', pm_id: 'pm1', internal_code: 'ROM-001', display_name: 'Appartamento Trastevere', address: 'Via della Scala 15', city: 'Roma', region: 'Lazio', property_type: 'LT', cin_code: 'IT058091C1A2B3C4D5', ota_codes: { airbnb_id: '12345678', booking_id: '9876543', vrbo_id: 'VR-001122' }, status: 'active', listings_count: 2, bookings_count: 12, created_at: '2024-01-25' },
  { property_id: 'p2', tenant_id: 't1', owner_id: 'o1', pm_id: 'pm1', internal_code: 'ROM-002', display_name: 'Loft Monti', address: 'Via dei Serpenti 80', city: 'Roma', region: 'Lazio', property_type: 'LT', cin_code: 'IT058091C5E6F7G8H9', ota_codes: { airbnb_id: '22334455', booking_id: '5544332' }, status: 'active', listings_count: 2, bookings_count: 8, created_at: '2024-01-25' },
  { property_id: 'p3', tenant_id: 't1', owner_id: 'o2', pm_id: 'pm1', internal_code: 'ROM-003', display_name: 'Suite Vaticano', address: 'Via Cola di Rienzo 200', city: 'Roma', region: 'Lazio', property_type: 'LT', cin_code: 'IT058091C9I0J1K2L3', ota_codes: { airbnb_id: '33445566', tripadvisor_id: 'TP-887766' }, status: 'active', listings_count: 1, bookings_count: 5, created_at: '2024-02-10' },
  { property_id: 'p4', tenant_id: 't1', owner_id: 'o2', pm_id: 'pm1', internal_code: 'FIR-001', display_name: 'Casa Centro Storico', address: 'Via dei Calzaiuoli 12', city: 'Firenze', region: 'Toscana', property_type: 'LT', cin_code: 'IT048017C4M5N6O7P8', ota_codes: { booking_id: '1122334', expedia_id: 'EX-556677' }, status: 'active', listings_count: 2, bookings_count: 3, created_at: '2024-02-15' },
  { property_id: 'p5', tenant_id: 't1', owner_id: 'o3', pm_id: 'pm1', internal_code: 'NAP-001', display_name: 'Vista Golfo Napoli', address: 'Via Partenope 45', city: 'Napoli', region: 'Campania', property_type: 'LT', cin_code: 'IT063049C8Q9R0S1T2', ota_codes: { airbnb_id: '66778899' }, status: 'active', listings_count: 1, bookings_count: 0, created_at: '2024-03-20' },
  { property_id: 'p6', tenant_id: 't2', owner_id: 'o4', pm_id: 'pm2', internal_code: 'GEN-001', display_name: 'Bilocale Porto Antico', address: 'Via al Porto Antico 8', city: 'Genova', region: 'Liguria', property_type: 'LT', cin_code: 'IT010025C2U3V4W5X6', ota_codes: { airbnb_id: '44556677', booking_id: '7766554' }, status: 'active', listings_count: 2, bookings_count: 9, created_at: '2024-03-25' },
  { property_id: 'p7', tenant_id: 't2', owner_id: 'o4', pm_id: 'pm2', internal_code: 'GEN-002', display_name: 'Terrazza Nervi', address: 'Via Oberdan 3', city: 'Genova', region: 'Liguria', property_type: 'LT', cin_code: 'IT010025C6Y7Z8A9B0', ota_codes: { booking_id: '3344556' }, status: 'active', listings_count: 1, bookings_count: 4, created_at: '2024-04-01' },
  { property_id: 'p8', tenant_id: 't2', owner_id: 'o5', pm_id: 'pm2', internal_code: 'SPE-001', display_name: 'Casa Cinque Terre', address: 'Via Discovolo 12', city: 'Riomaggiore', region: 'Liguria', property_type: 'LT', cin_code: 'IT011027C0D1E2F3G4', ota_codes: { airbnb_id: '55667788', vrbo_id: 'VR-998877' }, status: 'active', listings_count: 1, bookings_count: 2, created_at: '2024-04-15' },
];

// ---- Bookings ----
const channels = ['airbnb', 'booking', 'vrbo'];

// Helper to format date
const fmtDate = (d: Date) => d.toISOString().slice(0, 10);

// Generate bookings with a mix of timings relative to today
const today = new Date();
export const mockBookings: Booking[] = Array.from({ length: 50 }, (_, i) => {
  const propIdx = i % 8;
  const prop = mockProperties[propIdx];
  const owner = mockOwners.find(o => o.owner_id === prop.owner_id)!;
  const channel = channels[i % 3];
  const nights = 2 + (i % 7);
  const guests = 1 + (i % 4);
  const gross = 80 + (i * 15) + (nights * 50);
  const otaComm = Math.round(gross * 0.15 * 100) / 100;
  const cleaning = 50 + (i % 3) * 10;
  const pmFee = Math.round(gross * 0.20 * 100) / 100;
  const ownerNet = Math.round((gross - otaComm - cleaning - pmFee) * 100) / 100;
  const withholding = Math.round(ownerNet * 0.21 * 100) / 100;
  const statuses: Booking['booking_status'][] = ['imported', 'enriched', 'ready', 'doc_issued', 'settled'];
  const docStatuses: Booking['document_status'][] = ['draft', 'ready', 'sent_sdi', 'accepted', 'accepted'];
  const settlStatuses: Booking['settlement_status'][] = ['pending', 'pending', 'calculated', 'approved', 'paid'];

  // Distribute checkout dates: some in penalty (>12gg), some recent (1-12gg), some future, some completed
  let checkoutDate: Date;
  if (i < 8) {
    // In penale: checkout 15-40 giorni fa
    checkoutDate = new Date(today);
    checkoutDate.setDate(today.getDate() - 15 - (i * 3));
  } else if (i < 18) {
    // Scadute ma non in penale: checkout 1-11 giorni fa
    checkoutDate = new Date(today);
    checkoutDate.setDate(today.getDate() - 1 - (i % 11));
  } else if (i < 30) {
    // Future: checkout tra 1-60 giorni
    checkoutDate = new Date(today);
    checkoutDate.setDate(today.getDate() + 1 + (i * 2));
  } else {
    // Storiche completate (doc_issued o settled)
    checkoutDate = new Date(today);
    checkoutDate.setDate(today.getDate() - 20 - (i * 2));
  }

  const checkinDate = new Date(checkoutDate);
  checkinDate.setDate(checkoutDate.getDate() - nights);

  const checkin = fmtDate(checkinDate);
  const checkout = fmtDate(checkoutDate);

  // Assign status based on category
  let si: number;
  if (i < 18) {
    // Da completare: imported, enriched or ready (not doc_issued/settled)
    si = i % 3; // 0=imported, 1=enriched, 2=ready
  } else if (i >= 30) {
    // Storiche: doc_issued or settled
    si = 3 + (i % 2); // 3=doc_issued, 4=settled
  } else {
    si = i % 5;
  }

  const touristTaxAmount = Math.round(guests * Math.min(nights, 5) * 3.50 * 100) / 100;
  const taxIncluded = channel === 'airbnb';

  return {
    booking_id: `b${i + 1}`,
    tenant_id: prop.tenant_id,
    property_id: prop.property_id,
    property_name: prop.display_name,
    owner_name: `${owner.first_name} ${owner.last_name}`,
    guest_name: ['John Smith', 'Marie Dupont', 'Hans Müller', 'Yuki Tanaka', 'Carlos García', 'Emma Wilson', 'Luca Romano', 'Sophie Martin'][i % 8],
    guest_tax_code: ['SMTJHN85A01H501X', 'DPNMRA90B42F205Z', 'MLLHNS80C03D612Y', 'TNKYKU75D44L219V', 'GRCCRL88E05F839Q', 'WLSEMM92F46H501R', 'RMNLCU87G07F205T', 'MRTSPH91H48D612W'][i % 8],
    external_booking_id: `${channel.toUpperCase()}-${checkout.replace(/-/g, '').slice(0, 6)}${String(i + 1).padStart(4, '0')}`,
    channel_name: channel,
    checkin_date: checkin,
    checkout_date: checkout,
    nights,
    guests,
    gross_amount: gross,
    ota_commission_amount: otaComm,
    cleaning_amount: cleaning,
    pm_fee_amount: pmFee,
    owner_net_amount: ownerNet,
    withholding_amount: withholding,
    tourist_tax_amount: touristTaxAmount,
    tourist_tax_included_in_gross: taxIncluded,
    tourist_tax_collection: taxIncluded ? 'contanti' as const : 'payment_link' as const,
    booking_status: statuses[si],
    payment_status: si >= 3 ? 'received' : 'pending',
    document_status: docStatuses[si],
    settlement_status: settlStatuses[si],
    fiscal_scenario_code: 'scenario_A',
    created_at: checkin,
  };
});

// ---- Fiscal Documents ----
// Each booking generates TWO documents:
// 1. Fattura PM → ospite (con IVA 22%)
// 2. Ricevuta Owner → ospite (esente IVA)
const eligibleBookings = mockBookings.filter(b => b.document_status !== 'draft').slice(0, 20);
export const mockDocuments: FiscalDocument[] = eligibleBookings.flatMap((b, i) => {
  // Fattura PM: imponibile = commissione OTA + pulizie + provvigione PM, IVA 22%
  const fatturaImponibile = b.ota_commission_amount + b.cleaning_amount + b.pm_fee_amount;
  const fatturaVat = Math.round(fatturaImponibile * 0.22 * 100) / 100;
  // Ricevuta Owner: canone di locazione, fuori campo IVA
  const ricevutaTotal = b.gross_amount;

  return [
    {
      document_id: `doc-ft-${i + 1}`,
      tenant_id: b.tenant_id,
      booking_id: b.booking_id,
      document_type: 'fattura' as const,
      document_number: `FT-2025-${String(i + 1).padStart(4, '0')}`,
      issue_date: b.checkout_date,
      recipient_name: b.guest_name,
      total_amount: Math.round((fatturaImponibile + fatturaVat) * 100) / 100,
      vat_amount: fatturaVat,
      status: b.document_status,
      sdi_status: b.document_status === 'accepted' ? 'RC' : b.document_status === 'sent_sdi' ? 'MC' : undefined,
      sdi_identifier: b.document_status === 'accepted' || b.document_status === 'sent_sdi' ? `SDI${String(i + 1).padStart(8, '0')}` : undefined,
      property_name: b.property_name,
      channel_name: b.channel_name,
    },
    {
      document_id: `doc-ric-${i + 1}`,
      tenant_id: b.tenant_id,
      booking_id: b.booking_id,
      document_type: 'ricevuta' as const,
      document_number: `RIC-2025-${String(i + 1).padStart(4, '0')}`,
      issue_date: b.checkout_date,
      recipient_name: b.guest_name,
      total_amount: ricevutaTotal,
      vat_amount: 0,
      status: b.document_status,
      property_name: b.property_name,
      channel_name: b.channel_name,
    },
  ];
});

// ---- Settlements ----
export const mockSettlements: Settlement[] = mockOwners.slice(0, 3).flatMap((owner, oi) =>
  ['2025-01', '2025-02', '2025-03'].map((period, pi) => {
    const ownerBookings = mockBookings.filter(b => b.owner_name === `${owner.first_name} ${owner.last_name}`);
    const total = ownerBookings.slice(0, 3 + pi).reduce((s, b) => s + b.owner_net_amount, 0);
    const wh = Math.round(total * 0.21 * 100) / 100;
    return {
      settlement_id: `set${oi * 3 + pi + 1}`,
      tenant_id: 't1',
      owner_id: owner.owner_id,
      owner_name: `${owner.first_name} ${owner.last_name}`,
      period,
      total_amount: Math.round(total * 100) / 100,
      withholding_amount: wh,
      net_amount: Math.round((total - wh) * 100) / 100,
      bookings_count: 3 + pi,
      status: pi === 2 ? 'pending' as const : pi === 1 ? 'approved' as const : 'paid' as const,
      payment_date: pi === 0 ? `2025-02-15` : undefined,
      created_at: `${period}-28`,
    };
  })
);

// ---- F24 ----
export const mockF24: F24Record[] = [
  { f24_id: 'f24-1', tenant_id: 't1', period: '2025-01', tax_code: '1919', total_amount: 2345.67, withholdings_count: 8, status: 'paid', deadline_date: '2025-02-16', payment_date: '2025-02-14', created_at: '2025-02-01' },
  { f24_id: 'f24-2', tenant_id: 't1', period: '2025-02', tax_code: '1919', total_amount: 3120.45, withholdings_count: 12, status: 'sent', deadline_date: '2025-03-16', created_at: '2025-03-01' },
  { f24_id: 'f24-3', tenant_id: 't1', period: '2025-03', tax_code: '1919', total_amount: 1890.23, withholdings_count: 6, status: 'draft', deadline_date: '2025-04-16', created_at: '2025-04-01' },
];

// ---- CU ----
export const mockCU: CURecord[] = mockOwners.slice(0, 3).map((owner, i) => ({
  cu_id: `cu${i + 1}`,
  tenant_id: 't1',
  owner_id: owner.owner_id,
  owner_name: `${owner.first_name} ${owner.last_name}`,
  tax_year: 2024,
  total_compensi: 15000 + i * 5000,
  total_ritenute: Math.round((15000 + i * 5000) * 0.21 * 100) / 100,
  status: i === 0 ? 'sent' : i === 1 ? 'generated' : 'draft',
  generated_at: i < 2 ? '2025-02-28' : undefined,
  created_at: '2025-02-01',
}));

// ---- Audit Log ----
export const mockAuditLog: AuditLogEntry[] = [
  { log_id: 'al1', tenant_id: 't1', user_email: 'admin@casavacanze.it', action: 'booking.import', entity_type: 'Booking', entity_id: 'b1', details: 'Importate 12 prenotazioni da file CSV', ip_address: '192.168.1.10', created_at: '2025-03-15T10:30:00' },
  { log_id: 'al2', tenant_id: 't1', user_email: 'pm@casavacanze.it', action: 'document.issue', entity_type: 'FiscalDocument', entity_id: 'doc5', details: 'Emessa fattura FE-2025-0005 per prenotazione b5', ip_address: '192.168.1.11', created_at: '2025-03-14T14:22:00' },
  { log_id: 'al3', tenant_id: 't1', user_email: 'admin@casavacanze.it', action: 'settlement.approve', entity_type: 'Settlement', entity_id: 'set2', details: 'Approvata liquidazione proprietario Ferrari - Febbraio 2025', ip_address: '192.168.1.10', created_at: '2025-03-13T09:15:00' },
  { log_id: 'al4', user_email: 'superadmin@sostitutoincloud.it', action: 'tenant.create', entity_type: 'Tenant', entity_id: 't3', details: 'Creato tenant Toscana Property Management', ip_address: '10.0.0.1', created_at: '2025-03-12T16:45:00' },
  { log_id: 'al5', tenant_id: 't1', user_email: 'pm@casavacanze.it', action: 'document.choose_ricevuta', entity_type: 'Booking', entity_id: 'b12', details: 'PM ha scelto ricevuta semplice invece di fattura - Motivazione: ospite straniero senza CF', ip_address: '192.168.1.11', created_at: '2025-03-11T11:00:00' },
  { log_id: 'al6', tenant_id: 't1', user_email: 'admin@casavacanze.it', action: 'f24.generate', entity_type: 'F24', entity_id: 'f24-2', details: 'Generato F24 febbraio 2025 - €3.120,45', ip_address: '192.168.1.10', created_at: '2025-03-10T08:30:00' },
  { log_id: 'al7', tenant_id: 't1', user_email: 'admin@casavacanze.it', action: 'owner.create', entity_type: 'OwnerProfile', entity_id: 'o3', details: 'Creato proprietario Lucia Conti', ip_address: '192.168.1.10', created_at: '2025-03-09T15:00:00' },
  { log_id: 'al8', user_email: 'superadmin@sostitutoincloud.it', action: 'tenant.suspend', entity_type: 'Tenant', entity_id: 't3', details: 'Sospeso tenant Toscana PM - morosità', ip_address: '10.0.0.1', created_at: '2025-03-08T12:00:00' },
];

// ---- Alerts ----
export const mockAlerts: AlertItem[] = [
  { id: 'a1', type: 'warning', message: '3 prenotazioni con documenti in scadenza (entro 2 giorni)', action: 'Vedi prenotazioni', created_at: '2025-03-18T08:00:00' },
  { id: 'a2', type: 'error', message: 'Fattura FE-2025-0018 rifiutata da SDI: codice fiscale non valido', action: 'Correggi documento', created_at: '2025-03-17T14:30:00' },
  { id: 'a3', type: 'info', message: 'F24 marzo 2025 pronto per la generazione (scadenza 16/04)', action: 'Genera F24', created_at: '2025-03-16T09:00:00' },
  { id: 'a4', type: 'warning', message: '2 prenotazioni senza match ospite — dati Alloggiati mancanti', action: 'Riconcilia', created_at: '2025-03-15T10:00:00' },
];

// ---- Revenue chart data ----
export const mockRevenueData = [
  { month: 'Apr', ricavi_pm: 3280, ricavi_ow: 4920, commissioni: 1640, ritenute: 1377 },
  { month: 'Mag', ricavi_pm: 5000, ricavi_ow: 7500, commissioni: 2500, ritenute: 2100 },
  { month: 'Giu', ricavi_pm: 7560, ricavi_ow: 11340, commissioni: 3780, ritenute: 3175 },
  { month: 'Lug', ricavi_pm: 9720, ricavi_ow: 14580, commissioni: 4860, ritenute: 4082 },
  { month: 'Ago', ricavi_pm: 10440, ricavi_ow: 15660, commissioni: 5220, ritenute: 4385 },
  { month: 'Set', ricavi_pm: 7800, ricavi_ow: 11700, commissioni: 3900, ritenute: 3276 },
  { month: 'Ott', ricavi_pm: 5680, ricavi_ow: 8520, commissioni: 2840, ritenute: 2386 },
  { month: 'Nov', ricavi_pm: 3120, ricavi_ow: 4680, commissioni: 1560, ritenute: 1310 },
  { month: 'Dic', ricavi_pm: 3800, ricavi_ow: 5700, commissioni: 1900, ritenute: 1596 },
  { month: 'Gen', ricavi_pm: 2480, ricavi_ow: 3720, commissioni: 1240, ritenute: 1042 },
  { month: 'Feb', ricavi_pm: 2840, ricavi_ow: 4260, commissioni: 1420, ritenute: 1193 },
  { month: 'Mar', ricavi_pm: 4720, ricavi_ow: 7080, commissioni: 2360, ritenute: 1982 },
];

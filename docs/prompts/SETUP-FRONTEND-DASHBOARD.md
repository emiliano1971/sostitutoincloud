Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Collega la TenantDashboard al backend reale
sostituendo i dati mock.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima leggi e descrivi brevemente:
- frontend/src/pages/tenant/TenantDashboard.tsx
- Quali dati mock usa e come li aggrega

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. ENDPOINT BACKEND — /api/dashboard
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/main/java/it/gavia/sostitutoincloud/
dto/dashboard/DashboardDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi KPI:
    * Integer bookingsDaCompletare
      ← booking con checkout <= oggi e stato non finale
    * Integer bookingsInPenale
      ← booking con checkout <= oggi - 12gg e stato non finale
    * Integer documentiPending
      ← fiscal_document con stato draft o ready
    * Integer f24DaGenerare
      ← f24_record con stato draft o ready
    * Integer liquidazioniPending
      ← settlement con stato pending o calculated
    * BigDecimal ricaviMeseCorrente
      ← SUM gross_amount booking con checkin nel mese corrente
    * BigDecimal ricaviMesePrecedente
      ← SUM gross_amount booking con checkin nel mese precedente
    * List<MensileDTO> ricaviUltimi12Mesi
      ← aggregazione mensile ultimi 12 mesi

Crea dto/dashboard/MensileDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Campi:
    * String mese          ← formato "MMM yyyy" es. "Gen 2026"
    * String meseKey       ← formato "YYYY-MM" per ordinamento
    * BigDecimal ricaviPm  ← SUM pm_fee_amount
    * BigDecimal ricaviOw  ← SUM owner_net_amount
    * BigDecimal commissioni ← SUM ota_commission_amount
    * BigDecimal ritenute  ← SUM withholding_amount

Crea service/DashboardService.java:
- @Service @Log4j2
- Costruttore con BookingDAO, FiscalDocumentDAO,
  F24RecordDAO, SettlementDAO, StatoPrenotazioneDAO,
  StatoDocumentoDAO

- Metodo:
  DashboardDTO getDashboard(Integer tenantId)
    - carica tutti i booking del tenant una volta sola
    - carica stati prenotazione come Map<codice, id>
    - calcola tutti i KPI in memoria:

      bookingsDaCompletare:
      booking dove checkout <= oggi AND
      statoPrenotazione NOT IN (doc_issued, settled, cancelled)

      bookingsInPenale:
      booking dove checkout <= oggi - 12 giorni AND
      statoPrenotazione NOT IN (doc_issued, settled, cancelled)

      documentiPending:
      fiscalDocumentDAO.findByTenantId() filtrati per
      stato IN (draft, ready)

      f24DaGenerare:
      f24RecordDAO.findByTenantId() filtrati per
      stato IN (draft, ready)

      liquidazioniPending:
      settlementDAO.findByTenantId() filtrati per
      stato IN (pending, calculated)

      ricaviMeseCorrente:
      SUM gross_amount booking con checkin nel mese corrente

      ricaviMesePrecedente:
      SUM gross_amount booking con checkin nel mese precedente

      ricaviUltimi12Mesi:
      per ogni mese degli ultimi 12 (da oggi a ritroso):
        - filtra booking con checkin in quel mese
        - calcola SUM pm_fee, owner_net, commissioni, ritenute
        - crea MensileDTO con mese formattato in italiano
          (usa Locale.ITALIAN per i nomi dei mesi)
        - ordina dal mese più vecchio al più recente

    - Log INFO con tenantId

Crea controller/DashboardController.java:
- @RestController @Log4j2
- @RequestMapping("/api/dashboard")
- Costruttore con DashboardService
- Endpoint:

  GET /api/dashboard
    - tenantId = SecurityUtils.getCurrentTenantId()
    - ResponseEntity<DashboardDTO>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CREA frontend/src/api/dashboardApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

```ts
export interface MensileDTO {
  mese: string;
  meseKey: string;
  ricaviPm: number;
  ricaviOw: number;
  commissioni: number;
  ritenute: number;
}

export interface DashboardDTO {
  bookingsDaCompletare: number;
  bookingsInPenale: number;
  documentiPending: number;
  f24DaGenerare: number;
  liquidazioniPending: number;
  ricaviMeseCorrente: number;
  ricaviMesePrecedente: number;
  ricaviUltimi12Mesi: MensileDTO[];
}

export async function getDashboard(): Promise<DashboardDTO>
// GET /api/dashboard
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. AGGIORNA TenantDashboard.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/TenantDashboard.tsx:
- Mantieni TUTTO il layout e UI esistente
- Sostituisci mock con useEffect → getDashboard()
- Aggiungi loading/error state
- Adatta i dati:
    * KPI cards usano i campi diretti del DashboardDTO
    * Il grafico "Ricavi ultimi 12 mesi" usa
      ricaviUltimi12Mesi come array — ogni elemento
      ha mese (label asse X), ricaviPm, ricaviOw,
      commissioni, ritenute (barre)
    * Gli alert in cima usano:
      bookingsInPenale > 0 → alert rosso penali
      bookingsDaCompletare > 0 → alert giallo da completare
      f24DaGenerare > 0 → alert blu F24 da generare

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima lancia il build backend:
mvn -Plocal clean package

Verifica endpoint:
curl -u admin@casavacanze.it:atena \
http://localhost:8081/sostitutoincloud/api/dashboard

Poi verifica frontend:
cd frontend && npm run build

Riporta output di entrambi.
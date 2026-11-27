package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.SettlementBookingDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.document.CambioDataControlloDTO;
import it.gavia.sostitutoincloud.dto.document.CambioDataControlloDTO.Avviso;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.Settlement;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.model.WithholdingLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cambio della issue_date di una fattura PM o di una nota di credito già emessa e mai inviata
 * (draft/ready, senza progressivo né file SDI), o di una ricevuta (con la sua riga di ritenuta).
 * Anno invariato, nessuna rinumerazione: la nuova
 * data deve restare tra quella del documento precedente e quella del successivo nella serie
 * (tenant, tipo, anno, annullati compresi), e non oltre oggi.
 *
 * <p>Ricevuta in un altro mese: consentito con la ritenuta da_versare libera o in un F24 ready
 * (che viene sganciata, torna da_versare e l'F24 si ricalcola) e con la prenotazione fuori dai
 * rendiconti o in uno pending/calculated (da cui esce, con ricalcolo o eliminazione se resta
 * vuoto). La ritenuta NON viene agganciata a F24 o rendiconti del nuovo periodo: ci entra con la
 * normale generazione/ricalcolo. Non tocca CU, emissione, invio SDI né NdcService.
 */
@Service
@Log4j2
public class DocumentDataModificaService {

    private static final String CODICE_FATTURA = "fattura";
    private static final String CODICE_NOTA_CREDITO = "nota_credito";
    private static final String CODICE_RICEVUTA = "ricevuta";
    private static final String LEDGER_DA_VERSARE = "da_versare";
    private static final String LEDGER_VERSATA = "versata";
    private static final String F24_READY = "ready";
    private static final String F24_SENT = "sent";
    private static final String F24_PAID = "paid";
    private static final String SETTLEMENT_PENDING = "pending";
    private static final Set<String> CODICI_SCADENZA = Set.of("SCADENZA_F24_SUPERATA", "SCADENZA_ORIGINE_SUPERATA");
    private static final Set<String> STATI_F24_VERSATO = Set.of("sent", "paid");
    private static final Set<String> STATI_SETTLEMENT_BLOCCATI = Set.of("approved", "paid");
    private static final Set<String> STATI_CU_CONSEGNATA = Set.of("sent", "delivered");
    private static final Set<String> STATI_MODIFICABILI = Set.of("draft", "ready");
    private static final Set<String> STATI_CREDITO = Set.of("credito_imposta", "compensato");
    private static final int MOTIVO_MIN = 5;
    private static final int MOTIVO_MAX = 500;
    private static final DateTimeFormatter DATA_IT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_ORA_IT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final BookingDAO bookingDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final NdcService ndcService;
    private final AuditService auditService;
    private final F24RecordDAO f24RecordDAO;
    private final SettlementBookingDAO settlementBookingDAO;
    private final SettlementDAO settlementDAO;
    private final CuRecordDAO cuRecordDAO;
    private final F24Service f24Service;
    private final SettlementService settlementService;
    private final TransactionTemplate transactionTemplate;

    public DocumentDataModificaService(FiscalDocumentDAO fiscalDocumentDAO,
                                       TipoDocumentoDAO tipoDocumentoDAO,
                                       StatoDocumentoDAO statoDocumentoDAO,
                                       BookingDAO bookingDAO,
                                       WithholdingLedgerDAO withholdingLedgerDAO,
                                       NdcService ndcService,
                                       AuditService auditService,
                                       F24RecordDAO f24RecordDAO,
                                       SettlementBookingDAO settlementBookingDAO,
                                       SettlementDAO settlementDAO,
                                       CuRecordDAO cuRecordDAO,
                                       F24Service f24Service,
                                       SettlementService settlementService,
                                       PlatformTransactionManager transactionManager) {
        this.f24Service = f24Service;
        this.settlementService = settlementService;
        this.f24RecordDAO = f24RecordDAO;
        this.settlementBookingDAO = settlementBookingDAO;
        this.settlementDAO = settlementDAO;
        this.cuRecordDAO = cuRecordDAO;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.bookingDAO = bookingDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.ndcService = ndcService;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Serie di numerazione da bloccare (stesso lock dell'emissione). */
    private record Serie(Integer tipoId, Integer anno) { }

    /** Esito della valutazione: intervallo, blocchi, avvisi. */
    private static final class Valutazione {
        LocalDate min;
        LocalDate max;
        boolean ndc;
        boolean ricevuta;
        FiscalDocument fattura;
        /** Ricevuta: riga di ritenuta del documento. */
        WithholdingLedger riga;
        /** Ricevuta: F24 ready da cui la ritenuta esce se cambia mese. */
        F24Record f24;
        /** Ricevuta: rendiconto pending/calculated da cui la prenotazione esce se cambia mese. */
        Settlement settlement;
        /** Ricevuta con data in un altro mese: avvisi di scadenza F24 superata da confermare. */
        boolean richiedeConfermaScadenza;
        final List<String> blocchi = new ArrayList<>();
        final List<Avviso> avvisi = new ArrayList<>();
    }

    // ── API ──────────────────────────────────────────────────────────────────

    /**
     * Controllo preventivo: senza data valuta solo il documento e l'intervallo, con la data anche
     * anno, intervallo e avvisi legati alla data. Non scrive nulla.
     *
     * @throws NoSuchElementException documento inesistente o di altro tenant (404)
     */
    public CambioDataControlloDTO controllo(Integer tenantId, Integer documentId, LocalDate data) {
        FiscalDocument doc = carica(tenantId, documentId);
        Valutazione v = valuta(tenantId, doc, data);
        if (data != null && data.equals(doc.getIssueDate())) {
            v.blocchi.add("La data è già quella indicata");
        }
        return CambioDataControlloDTO.builder()
                .consentito(v.blocchi.isEmpty())
                .blocchi(v.blocchi)
                .avvisi(v.avvisi)
                .dataMinima(v.min)
                .dataMassima(v.max)
                .dataAttuale(doc.getIssueDate())
                // Con le regole attuali ogni stato non ammesso blocca del tutto: niente limite al mese
                .limitatoAlMese(v.ricevuta ? Boolean.FALSE : null)
                .richiedeConfermaScadenza(v.ricevuta ? v.richiedeConfermaScadenza : null)
                .build();
    }

    /**
     * Cambia la data. Lock di numerazione sulla serie (e su quella delle fatture per la NDC),
     * ricalcolo dentro il lock, UPDATE condizionale.
     *
     * @throws IllegalArgumentException          data mancante o invariata, motivo non valido (400)
     * @throws NoSuchElementException            documento inesistente o di altro tenant (404)
     * @throws CambioDataNonConsentitoException  blocchi, scadenza non confermata o documento cambiato (422)
     */
    public void modifica(Integer tenantId, Integer documentId, LocalDate nuovaData, String motivo,
                         boolean confermaScadenza) {
        if (nuovaData == null) {
            throw new IllegalArgumentException("Data obbligatoria");
        }
        String m = motivo != null ? motivo.trim() : "";
        if (m.length() < MOTIVO_MIN || m.length() > MOTIVO_MAX) {
            throw new IllegalArgumentException("Motivo obbligatorio, da " + MOTIVO_MIN + " a " + MOTIVO_MAX + " caratteri");
        }
        FiscalDocument preliminare = carica(tenantId, documentId);
        if (nuovaData.equals(preliminare.getIssueDate())) {
            throw new IllegalArgumentException("La data è già quella indicata");
        }
        List<Serie> serie = serie(preliminare);

        transactionTemplate.executeWithoutResult(status -> {
            // Ordine fisso (tipo, anno), stesso lock dell'emissione
            serie.forEach(s -> fiscalDocumentDAO.lockNumerazione(tenantId, s.tipoId(), s.anno()));

            FiscalDocument doc = carica(tenantId, documentId);
            LocalDate dataPrecedente = doc.getIssueDate();
            if (nuovaData.equals(dataPrecedente)) {
                throw new CambioDataNonConsentitoException(List.of("La data è già quella indicata"));
            }
            // Ricevuta: lock di riga su F24 e rendiconto collegati prima di rivalutare, così
            // "segna pagato" / "approva" non possono cambiarne lo stato fino al commit
            if (tipoId(CODICE_RICEVUTA).equals(doc.getFkTipoDocumentoId())) {
                withholdingLedgerDAO.findByFiscalDocumentId(doc.getId())
                        .map(WithholdingLedger::getFkF24RecordId)
                        .ifPresent(f24RecordDAO::lockById);
                if (doc.getFkBookingId() != null) {
                    settlementBookingDAO.findSettlementIdByBookingId(doc.getFkBookingId())
                            .ifPresent(settlementDAO::lockById);
                }
            }
            Valutazione v = valuta(tenantId, doc, nuovaData);
            if (!v.blocchi.isEmpty()) {
                throw new CambioDataNonConsentitoException(v.blocchi);
            }
            if (v.richiedeConfermaScadenza && !confermaScadenza) {
                throw new CambioDataNonConsentitoException(List.of(
                        "Scadenza F24 superata: per procedere va confermata esplicitamente (confermaScadenza)"));
            }
            if (fiscalDocumentDAO.updateIssueDate(doc.getId(), nuovaData) != 1) {
                throw new CambioDataNonConsentitoException(List.of(
                        "Il documento è cambiato nel frattempo: data non modificata"));
            }
            // Ricevuta: la riga di ritenuta segue la data (data_evento sempre, periodo se cambia mese)
            String testoLedger = "";
            String testoF24 = "";
            String testoRendiconto = "";
            String testoAvvisi = "";
            if (v.ricevuta) {
                WithholdingLedger riga = v.riga;
                boolean cambiaMese = nuovaData.getMonthValue() != dataPrecedente.getMonthValue();
                if (cambiaMese) {
                    // 1. Fuori dall'F24 ready (torna da_versare) e ricalcolo del suo totale/saldo
                    if (v.f24 != null) {
                        F24Record prima = v.f24;
                        if (withholdingLedgerDAO.sganciaDaF24Ready(riga.getId(), prima.getId()) != 1) {
                            throw new CambioDataNonConsentitoException(List.of(
                                    "La ritenuta o l'F24 sono cambiati nel frattempo: data non modificata"));
                        }
                        f24Service.ricalcolaTotale(prima.getId());
                        F24Record dopo = f24RecordDAO.findById(prima.getId()).orElse(prima);
                        boolean creditiRilasciati = nz(prima.getImportoCredito()).signum() > 0
                                && nz(dopo.getImportoCredito()).signum() == 0;
                        testoF24 = " F24 id=" + prima.getId() + " (" + periodo(prima.getPeriodoMese(), prima.getPeriodoAnno())
                                + (prima.isIntegrativo() ? ", integrativo" : "") + ") sganciato: totale € "
                                + nz(prima.getTotalAmount()) + " → € " + nz(dopo.getTotalAmount())
                                + ", saldo € " + nz(prima.getSaldoNetto()) + " → € " + nz(dopo.getSaldoNetto())
                                + (creditiRilasciati ? ", crediti compensati rilasciati" : "") + ".";
                    }
                    // 2. Periodo e data_evento (riga ora da_versare e fuori da qualsiasi F24)
                    if (withholdingLedgerDAO.spostaPeriodo(riga.getId(), nuovaData.getMonthValue(), nuovaData) != 1) {
                        throw new CambioDataNonConsentitoException(List.of(
                                "La riga di ritenuta è cambiata nel frattempo: data non modificata"));
                    }
                    // 3. Fuori dal rendiconto pending/calculated (rientrerà in quello del nuovo periodo)
                    if (v.settlement != null && esceDalRendiconto(v.settlement, riga.getPeriodoAnno(), nuovaData.getMonthValue())) {
                        Settlement s = v.settlement;
                        if (settlementBookingDAO.deleteByBookingIdInSettlement(doc.getFkBookingId(), s.getId()) != 1) {
                            throw new CambioDataNonConsentitoException(List.of(
                                    "Il rendiconto " + s.getPeriod() + " è cambiato nel frattempo: data non modificata"));
                        }
                        settlementService.ricalcolaTotali(s.getId(), tenantId);
                        boolean eliminato = settlementDAO.findById(s.getId()).isEmpty();
                        testoRendiconto = " Prenotazione tolta dal rendiconto id=" + s.getId() + " (" + s.getPeriod()
                                + ", " + s.getStato() + ")" + (eliminato ? ": rendiconto eliminato perché vuoto" : ": totali ricalcolati")
                                + ".";
                    }
                    testoAvvisi = v.avvisi.isEmpty() ? "" : " Avvisi confermati: " + v.avvisi.stream()
                            .map(a -> a.getCodice() + " (" + a.getMessaggio() + ")")
                            .collect(Collectors.joining("; ")) + "."
                            + (v.richiedeConfermaScadenza ? " Scadenza superata confermata esplicitamente." : "");
                } else if (withholdingLedgerDAO.updateDataEvento(riga.getId(), nuovaData) != 1) {
                    throw new CambioDataNonConsentitoException(List.of(
                            "La riga di ritenuta è cambiata nel frattempo: data non modificata"));
                }
                String periodoPrima = String.format("%02d/%d", riga.getPeriodoMese(), riga.getPeriodoAnno());
                String periodoDopo = String.format("%02d/%d", nuovaData.getMonthValue(), riga.getPeriodoAnno());
                testoLedger = " Ritenuta: riga registro id=" + riga.getId()
                        + ", data_evento dal " + (riga.getDataEvento() != null ? riga.getDataEvento().format(DATA_IT) : "—")
                        + " al " + nuovaData.format(DATA_IT)
                        + (cambiaMese ? ", periodo " + periodoPrima + " → " + periodoDopo : ", periodo invariato " + periodoPrima)
                        + ".";
            }
            String avvisoCredito = v.avvisi.stream()
                    .filter(a -> "NDC_CREDITO_PERIODO".equals(a.getCodice()))
                    .map(Avviso::getMessaggio).findFirst().orElse(null);
            auditService.log("document.data_modifica", "FiscalDocument", doc.getId(),
                    "Cambiata la data di " + doc.getDocumentNumber() + " dal " + dataPrecedente.format(DATA_IT)
                            + " al " + nuovaData.format(DATA_IT) + ". Motivo: " + m + "."
                            + testoLedger + testoF24 + testoRendiconto + testoAvvisi
                            + (avvisoCredito != null ? " Avviso confermato: " + avvisoCredito : ""));
            log.info("DocumentDataModificaService.modifica() - tenant={} doc={} {} → {}",
                    tenantId, doc.getDocumentNumber(), dataPrecedente, nuovaData);
        });
    }

    // ── valutazione ──────────────────────────────────────────────────────────

    private Valutazione valuta(Integer tenantId, FiscalDocument doc, LocalDate data) {
        Valutazione v = new Valutazione();
        Integer tipoFatturaId = tipoId(CODICE_FATTURA);
        Integer tipoNdcId = tipoId(CODICE_NOTA_CREDITO);
        boolean fattura = tipoFatturaId.equals(doc.getFkTipoDocumentoId());
        v.ndc = tipoNdcId.equals(doc.getFkTipoDocumentoId());
        v.ricevuta = tipoId(CODICE_RICEVUTA).equals(doc.getFkTipoDocumentoId());
        if (!fattura && !v.ndc && !v.ricevuta) {
            v.blocchi.add("Si cambia la data solo di fatture PM, ricevute e note di credito");
            return v;
        }

        // Stato e SDI
        String stato = statoDocumentoDAO.findById(doc.getFkStatoDocumentoId())
                .map(StatoDocumento::getCodice).orElse(null);
        if (!STATI_MODIFICABILI.contains(stato)) {
            blocchi(v, "Documento in stato '" + stato + "': la data si cambia solo in bozza o pronto"
                    + ("sent_sdi".equals(stato) && !notBlank(doc.getSdiProgressivo())
                        ? " (se l'invio era manuale, usare prima \"Annulla invio manuale\")" : ""));
        }
        if (notBlank(doc.getSdiProgressivo()) || notBlank(doc.getSdiFilePath())) {
            blocchi(v, "File SDI già generato"
                    + (notBlank(doc.getSdiProgressivo()) ? " (progressivo " + doc.getSdiProgressivo() + ")" : "")
                    + ": data non modificabile");
        }
        if (fattura) {
            TipoDocumento tipoNdc = tipoDocumentoDAO.findById(tipoNdcId).orElseThrow();
            List<FiscalDocument> attive = ndcService.ndcAttive(doc.getId(), tipoNdc);
            if (!attive.isEmpty()) {
                blocchi(v, "Nota di credito attiva collegata (" + attive.stream()
                        .map(FiscalDocument::getDocumentNumber).collect(Collectors.joining(", "))
                        + "): data non modificabile");
            }
        }

        // Intervallo nella serie: tra il precedente e il successivo, anno invariato, non oltre oggi
        int anno = doc.getIssueDate().getYear();
        Integer progressivo = progressivo(doc);
        if (progressivo == null) {
            blocchi(v, "Numero documento non standard (" + doc.getDocumentNumber() + "): data non modificabile");
            return v;
        }
        Optional<FiscalDocument> prec = fiscalDocumentDAO.findAdiacenteInSerie(
                tenantId, doc.getFkTipoDocumentoId(), anno, progressivo, false);
        Optional<FiscalDocument> succ = fiscalDocumentDAO.findAdiacenteInSerie(
                tenantId, doc.getFkTipoDocumentoId(), anno, progressivo, true);
        LocalDate oggi = LocalDate.now();

        v.min = prec.map(FiscalDocument::getIssueDate).orElse(LocalDate.of(anno, 1, 1));
        String fonteMin = prec.map(p -> p.getDocumentNumber() + " del " + p.getIssueDate().format(DATA_IT))
                .orElse("1 gennaio " + anno);
        if (v.ndc) {
            v.fattura = doc.getFkDocumentoCollegatoId() != null
                    ? fiscalDocumentDAO.findById(doc.getFkDocumentoCollegatoId()).orElse(null) : null;
            if (v.fattura != null && v.fattura.getIssueDate() != null && v.fattura.getIssueDate().isAfter(v.min)) {
                v.min = v.fattura.getIssueDate();
                fonteMin = "fattura collegata " + v.fattura.getDocumentNumber() + " del " + v.fattura.getIssueDate().format(DATA_IT);
            }
        }
        LocalDate fineAnno = LocalDate.of(anno, 12, 31);
        v.max = oggi.isBefore(fineAnno) ? oggi : fineAnno;
        String fonteMax = oggi.isBefore(fineAnno) ? "oggi (" + oggi.format(DATA_IT) + ")" : "31 dicembre " + anno;
        if (succ.isPresent() && succ.get().getIssueDate().isBefore(v.max)) {
            v.max = succ.get().getIssueDate();
            fonteMax = succ.get().getDocumentNumber() + " del " + v.max.format(DATA_IT);
        }
        if (v.ricevuta) {
            controlliRicevuta(tenantId, doc, v);
        }
        if (v.min.isAfter(v.max)) {
            blocchi(v, "Nessuna data ammessa: il limite inferiore è " + fonteMin
                    + " e quello superiore " + fonteMax + " (date della serie fuori ordine)");
        }

        // Avvisi indipendenti dalla data
        if (doc.getEmailInviataAt() != null) {
            v.avvisi.add(avviso("EMAIL_GIA_INVIATA", doc.getDocumentNumber() + " è già stato inviato via email a "
                    + (doc.getEmailInviataA() != null ? doc.getEmailInviataA() : "—") + " il "
                    + doc.getEmailInviataAt().format(DATA_ORA_IT) + ": il PDF inviato riporta la data precedente"));
        }
        if (v.ndc) {
            List<WithholdingLedger> crediti = withholdingLedgerDAO.findByNdcId(doc.getId()).stream()
                    .filter(w -> STATI_CREDITO.contains(w.getStato()))
                    .toList();
            if (!crediti.isEmpty()) {
                String periodi = crediti.stream()
                        .map(w -> String.format("%02d/%d", w.getPeriodoMese(), w.getPeriodoAnno()))
                        .distinct().sorted().collect(Collectors.joining(", "));
                v.avvisi.add(avviso("NDC_CREDITO_PERIODO", "Il credito d'imposta della nota di credito resta "
                        + "registrato nel periodo " + periodi + " con la data precedente: importi di F24 e CU non cambiano."));
            }
        }

        // Controlli e avvisi sulla data richiesta
        if (data != null) {
            if (data.getYear() != anno) {
                blocchi(v, "La data deve restare nell'anno " + anno + " del numero " + doc.getDocumentNumber()
                        + ": niente rinumerazione");
            } else if (data.isBefore(v.min) || data.isAfter(v.max)) {
                blocchi(v, "Data fuori intervallo: ammessa dal " + v.min.format(DATA_IT) + " (" + fonteMin
                        + ") al " + v.max.format(DATA_IT) + " (" + fonteMax + ")");
            }
            if (v.ricevuta && v.riga != null && data.getYear() == anno
                    && data.getMonthValue() != doc.getIssueDate().getMonthValue()) {
                valutaCambioMese(tenantId, v, data, oggi);
            }
            long giorni = ChronoUnit.DAYS.between(data, oggi);
            // Le ricevute non vanno allo SDI: nessun avviso di invio tardivo
            if (!v.ricevuta && giorni > DocumentGenerationService.GIORNI_INVIO_SDI) {
                v.avvisi.add(avviso("INVIO_SDI_TARDIVO", (v.ndc ? "La nota di credito" : "La fattura immediata")
                        + " va trasmessa allo SDI entro " + DocumentGenerationService.GIORNI_INVIO_SDI
                        + " giorni dalla sua data: con il " + data.format(DATA_IT) + " l'invio sarà tardivo di "
                        + (giorni - DocumentGenerationService.GIORNI_INVIO_SDI)
                        + " giorni. Verificare con il commercialista."));
            }
            if (fattura && doc.getFkBookingId() != null) {
                LocalDate checkout = bookingDAO.findById(doc.getFkBookingId())
                        .map(Booking::getCheckoutDate).orElse(null);
                if (DocumentGenerationService.precedeCheckout(data, checkout)) {
                    v.avvisi.add(avviso("DATA_PRECEDE_CHECKOUT", "La data (" + data.format(DATA_IT)
                            + ") precede il check-out (" + checkout.format(DATA_IT)
                            + "): la prestazione non risulta ancora conclusa."));
                }
            }
        }
        return v;
    }

    // ── helper ───────────────────────────────────────────────────────────────

    /**
     * Ricevuta: blocchi comuni con l'eliminazione (riga di ritenuta assente o non ammessa, F24
     * sent/paid, rendiconto approved/paid, CU sent/delivered). Registra in v l'F24 ready e il
     * rendiconto pending/calculated da cui la ricevuta esce se cambia mese.
     */
    private void controlliRicevuta(Integer tenantId, FiscalDocument ric, Valutazione v) {
        WithholdingLedger riga = withholdingLedgerDAO.findByFiscalDocumentId(ric.getId()).orElse(null);
        Integer annoCu = ric.getIssueDate().getYear();
        if (riga == null) {
            blocchi(v, "Nessuna riga di ritenuta trovata nel registro: data non modificabile");
        } else {
            v.riga = riga;
            annoCu = riga.getPeriodoAnno() != null ? riga.getPeriodoAnno() : annoCu;
            F24Record f24 = riga.getFkF24RecordId() != null
                    ? f24RecordDAO.findById(riga.getFkF24RecordId()).orElse(null) : null;
            if (riga.getFkNdcId() != null) {
                blocchi(v, "Ritenuta collegata a una nota di credito: data non modificabile");
            } else if (f24 != null && STATI_F24_VERSATO.contains(f24.getStato())) {
                blocchi(v, "Ritenuta già in un F24 " + f24.getStato() + " (periodo " + f24.getPeriod()
                        + "): data non modificabile");
            } else if (f24 != null && F24_READY.equals(f24.getStato())
                    && (LEDGER_VERSATA.equals(riga.getStato()) || LEDGER_DA_VERSARE.equals(riga.getStato()))) {
                v.f24 = f24;
            } else if (f24 == null && LEDGER_DA_VERSARE.equals(riga.getStato())) {
                // ritenuta non ancora in un F24: nessun effetto sull'F24
            } else {
                blocchi(v, "Ritenuta in stato '" + riga.getStato() + "'"
                        + (f24 != null ? " nell'F24 " + f24.getPeriod() + " (" + f24.getStato() + ")" : "")
                        + ": data non modificabile");
            }
        }
        if (ric.getFkBookingId() != null) {
            settlementBookingDAO.findSettlementIdByBookingId(ric.getFkBookingId())
                    .flatMap(settlementDAO::findById)
                    .filter(s -> tenantId.equals(s.getFkTenantId()))
                    .ifPresent(s -> {
                        if (STATI_SETTLEMENT_BLOCCATI.contains(s.getStato())) {
                            blocchi(v, "Prenotazione nel rendiconto " + s.getPeriod() + " già "
                                    + ("paid".equals(s.getStato()) ? "pagato" : "approvato") + ": data non modificabile");
                        } else {
                            v.settlement = s;
                        }
                    });
        }
        if (ric.getFkOwnerId() != null) {
            Integer anno = annoCu;
            cuRecordDAO.findByTenantOwnerYear(tenantId, ric.getFkOwnerId(), anno)
                    .filter(cu -> STATI_CU_CONSEGNATA.contains(cu.getStato()))
                    .ifPresent(cu -> blocchi(v, "CU " + anno + " del proprietario già "
                            + ("sent".equals(cu.getStato()) ? "inviata" : "consegnata") + ": data non modificabile"));
        }
    }

    /**
     * Ricevuta con la data in un altro mese dello stesso anno: effetti sul periodo di origine (F24
     * ready ricalcolato, uscita dal rendiconto) e controlli sul periodo di destinazione (F24 sent o
     * pagato oltre scadenza = blocco, altri stati = avviso; rendiconto del proprietario; scadenze).
     */
    private void valutaCambioMese(Integer tenantId, Valutazione v, LocalDate data, LocalDate oggi) {
        WithholdingLedger riga = v.riga;
        int anno = riga.getPeriodoAnno() != null ? riga.getPeriodoAnno() : data.getYear();
        int meseOrig = riga.getPeriodoMese() != null ? riga.getPeriodoMese() : data.getMonthValue();
        int meseDest = data.getMonthValue();
        String pOrig = periodo(meseOrig, anno);
        String pDest = periodo(meseDest, anno);

        // Origine: F24 ready ricalcolato (stessi testi dell'eliminazione)
        if (v.f24 != null) {
            v.avvisi.add(avvisoF24Ricalcolato(v.f24, riga));
        }
        // Origine: rendiconto da cui la prenotazione esce
        if (v.settlement != null && esceDalRendiconto(v.settlement, anno, meseDest)) {
            Settlement s = v.settlement;
            boolean unica = settlementBookingDAO.findBySettlementId(s.getId()).size() <= 1;
            v.avvisi.add(avviso("RENDICONTO_RICALCOLATO", "La prenotazione esce dal rendiconto "
                    + periodoDaPeriod(s.getPeriod()) + " (" + labelSettlement(s.getStato()) + ")"
                    + (unica ? ": il rendiconto verrà eliminato (resta vuoto)"
                        : ": i totali verranno ricalcolati" + (SETTLEMENT_PENDING.equals(s.getStato())
                            ? " e il rendiconto passerà da 'in attesa' a 'calcolato'" : ""))
                    + ". Rientrerà nel rendiconto del periodo " + pDest + " al suo calcolo."));
        }
        v.avvisi.add(avviso("RICEVUTA_PERIODO_SPOSTATO", "La ritenuta passa dal periodo " + pOrig + " al "
                + pDest + " e resta da versare nel periodo " + pDest
                + ": va inclusa generando o ricalcolando l'F24 di quel periodo."));

        // Destinazione: F24 del periodo
        List<F24Record> dest = f24RecordDAO.findByTenantAndPeriodo(tenantId, anno, meseDest);
        F24Record principale = dest.stream().filter(f -> !f.isIntegrativo()).findFirst().orElse(null);
        LocalDate scadDest = principale != null && principale.getDeadlineDate() != null
                ? principale.getDeadlineDate() : scadenzaF24(anno, meseDest);
        boolean destScaduta = oggi.isAfter(scadDest);
        boolean avvisoScadenzaDest = false;
        if (principale == null) {
            avvisoScadenzaDest = destScaduta;
        } else if (F24_SENT.equals(principale.getStato())) {
            blocchi(v, "L'F24 " + pDest + " è già inviato: la ritenuta non può entrarvi. Cambio di mese verso "
                    + pDest + " non consentito");
        } else if (F24_PAID.equals(principale.getStato())) {
            F24Record integrativo = dest.stream().filter(F24Record::isIntegrativo)
                    .filter(f -> !F24_PAID.equals(f.getStato())).findFirst().orElse(null);
            if (destScaduta) {
                blocchi(v, "L'F24 " + pDest + " è già pagato e la scadenza del " + scadDest.format(DATA_IT)
                        + " è superata: l'integrativo non è più generabile (serve il commercialista). Cambio di mese verso "
                        + pDest + " non consentito");
            } else if (integrativo != null && F24_SENT.equals(integrativo.getStato())) {
                blocchi(v, "L'F24 integrativo " + pDest + " è già inviato: la ritenuta non può entrarvi. Cambio di mese verso "
                        + pDest + " non consentito");
            } else if (integrativo != null) {
                v.avvisi.add(avviso("F24_DESTINAZIONE", "L'F24 " + pDest + " è già pagato ed esiste un integrativo ("
                        + integrativo.getStato() + "): la ritenuta non vi entra da sola, va ricalcolato entro il "
                        + scadDest.format(DATA_IT) + "."));
            } else {
                v.avvisi.add(avviso("F24_DESTINAZIONE", "L'F24 " + pDest + " è già pagato: per la ritenuta servirà un "
                        + "F24 integrativo, da generare entro il " + scadDest.format(DATA_IT) + "."));
            }
        } else {
            v.avvisi.add(avviso("F24_DESTINAZIONE", "Esiste già l'F24 " + pDest + " (" + principale.getStato()
                    + "): la ritenuta non vi entra da sola, va ricalcolato (scadenza " + scadDest.format(DATA_IT) + ")."));
            avvisoScadenzaDest = destScaduta;
        }
        if (avvisoScadenzaDest) {
            v.avvisi.add(avviso("SCADENZA_F24_SUPERATA", "La scadenza F24 del periodo " + pDest + " ("
                    + scadDest.format(DATA_IT) + ") è già superata: il versamento della ritenuta sarà tardivo "
                    + "(ravvedimento). Verificare con il commercialista."));
        }

        // Origine: scadenza già superata (la ritenuta era dovuta entro quella data)
        LocalDate scadOrig = f24RecordDAO.findPrincipaleByPeriodo(tenantId, anno, meseOrig)
                .map(F24Record::getDeadlineDate)
                .orElse(scadenzaF24(anno, meseOrig));
        if (oggi.isAfter(scadOrig)) {
            v.avvisi.add(avviso("SCADENZA_ORIGINE_SUPERATA", "La ritenuta è di competenza del periodo " + pOrig
                    + ", con scadenza di versamento il " + scadOrig.format(DATA_IT) + " già superata: spostandola al "
                    + pDest + " la scadenza diventa il " + scadDest.format(DATA_IT)
                    + ". Verificare con il commercialista."));
        }

        // Destinazione: rendiconto del proprietario per quel periodo (diverso da quello attuale)
        Integer ownerId = riga.getFkOwnerId();
        if (ownerId != null) {
            settlementDAO.findByOwnerAndPeriod(tenantId, ownerId, String.format("%d-%02d", anno, meseDest))
                    .filter(s -> v.settlement == null || !s.getId().equals(v.settlement.getId()))
                    .ifPresent(s -> {
                        String msg;
                        if ("paid".equals(s.getStato())) {
                            msg = "Il rendiconto " + pDest + " del proprietario è già pagato: la prenotazione non vi "
                                    + "entrerà e verrà liquidata come arretrato nel primo rendiconto successivo calcolato.";
                        } else if ("approved".equals(s.getStato())) {
                            msg = "Il rendiconto " + pDest + " del proprietario è già approvato: ricalcolando le "
                                    + "liquidazioni del periodo la prenotazione vi entrerà e il rendiconto tornerà "
                                    + "'calcolato' (da riapprovare).";
                        } else {
                            msg = "Il rendiconto " + pDest + " del proprietario (" + labelSettlement(s.getStato())
                                    + ") non include la prenotazione: vi entrerà ricalcolando le liquidazioni del periodo.";
                        }
                        v.avvisi.add(avviso("RENDICONTO_DESTINAZIONE", msg));
                    });
        }

        v.richiedeConfermaScadenza = v.avvisi.stream().anyMatch(a -> CODICI_SCADENZA.contains(a.getCodice()));
    }

    /** F24_RICALCOLATO, con "resterà a zero" e "crediti rilasciati" come nell'eliminazione. */
    private static Avviso avvisoF24Ricalcolato(F24Record f24, WithholdingLedger riga) {
        BigDecimal ritenuta = nz(riga.getRitenutaAmount());
        BigDecimal nuovoTotale = nz(f24.getTotalAmount()).subtract(ritenuta);
        int righeResidue = (f24.getWithholdingsCount() != null ? f24.getWithholdingsCount() : 0) - 1;
        BigDecimal credito = nz(f24.getImportoCredito());
        StringBuilder msg = new StringBuilder("L'F24 ")
                .append(periodo(f24.getPeriodoMese(), f24.getPeriodoAnno()))
                .append(f24.isIntegrativo() ? " (integrativo, ready)" : " (ready)")
                .append(" verrà ricalcolato senza questa ritenuta (€ ").append(ritenuta)
                .append("): nuovo totale € ").append(nuovoTotale.max(BigDecimal.ZERO));
        if (righeResidue <= 0 || nuovoTotale.signum() <= 0) {
            msg.append("; l'F24 resterà a zero");
        }
        if (credito.signum() > 0 && credito.compareTo(nuovoTotale.max(BigDecimal.ZERO)) > 0) {
            msg.append("; i crediti compensati (€ ").append(credito).append(") verranno rilasciati");
        }
        return avviso("F24_RICALCOLATO", msg.toString());
    }

    /** La prenotazione esce dal rendiconto solo se il suo periodo è diverso da quello di destinazione. */
    private static boolean esceDalRendiconto(Settlement s, Integer anno, int meseDest) {
        return !String.format("%d-%02d", anno, meseDest).equals(s.getPeriod());
    }

    /** Scadenza F24 del periodo: giorno 16 del mese successivo (stessa regola di F24Service.generaF24). */
    private static LocalDate scadenzaF24(int anno, int mese) {
        return LocalDate.of(anno, mese, 16).plusMonths(1);
    }

    private static String periodo(Integer mese, Integer anno) {
        return String.format("%02d/%d", mese, anno);
    }

    /** "2026-10" → "10/2026". */
    private static String periodoDaPeriod(String period) {
        return period != null && period.length() == 7 ? period.substring(5, 7) + "/" + period.substring(0, 4) : period;
    }

    private static String labelSettlement(String stato) {
        return switch (stato != null ? stato : "") {
            case "pending" -> "in attesa";
            case "calculated" -> "calcolato";
            case "approved" -> "approvato";
            case "paid" -> "pagato";
            default -> stato;
        };
    }

    private static BigDecimal nz(BigDecimal b) {
        return b != null ? b : BigDecimal.ZERO;
    }

    /** Serie da bloccare: quella del documento e, per la NDC, quella delle fatture (ordine fisso). */
    private List<Serie> serie(FiscalDocument doc) {
        List<Serie> serie = new ArrayList<>();
        serie.add(new Serie(doc.getFkTipoDocumentoId(), doc.getIssueDate().getYear()));
        if (tipoId(CODICE_NOTA_CREDITO).equals(doc.getFkTipoDocumentoId()) && doc.getFkDocumentoCollegatoId() != null) {
            fiscalDocumentDAO.findById(doc.getFkDocumentoCollegatoId())
                    .ifPresent(f -> serie.add(new Serie(f.getFkTipoDocumentoId(), f.getIssueDate().getYear())));
        }
        return serie.stream().distinct()
                .sorted(Comparator.comparing(Serie::tipoId).thenComparing(Serie::anno))
                .toList();
    }

    private Integer progressivo(FiscalDocument d) {
        String[] parti = d.getDocumentNumber() != null ? d.getDocumentNumber().split("-") : new String[0];
        if (parti.length < 3) return null;
        try {
            return Integer.parseInt(parti[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private FiscalDocument carica(Integer tenantId, Integer documentId) {
        return fiscalDocumentDAO.findById(documentId)
                .filter(d -> tenantId.equals(d.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Documento non trovato: id=" + documentId));
    }

    private Integer tipoId(String codice) {
        return tipoDocumentoDAO.findByCodice(codice).map(TipoDocumento::getId)
                .orElseThrow(() -> new IllegalStateException("Tipo documento lookup non trovato: " + codice));
    }

    private static void blocchi(Valutazione v, String msg) {
        v.blocchi.add(msg);
    }

    private static Avviso avviso(String codice, String messaggio) {
        return Avviso.builder().codice(codice).messaggio(messaggio).build();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

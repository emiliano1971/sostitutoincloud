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
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.model.WithholdingLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 * (tenant, tipo, anno, annullati compresi), e non oltre oggi. Ricevute escluse.
 *
 * <p>Non tocca ledger, F24, CU, liquidazione, emissione, invio SDI né NdcService.
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
                                       PlatformTransactionManager transactionManager) {
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
        /** Ricevuta: cambio di mese non consentito (intervallo limitato al mese attuale) e motivo concreto. */
        boolean limitatoAlMese;
        String motivoLimiteMese;
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
                .limitatoAlMese(v.ricevuta ? v.limitatoAlMese : null)
                .motivoLimiteMese(v.motivoLimiteMese)
                .build();
    }

    /**
     * Cambia la data. Lock di numerazione sulla serie (e su quella delle fatture per la NDC),
     * ricalcolo dentro il lock, UPDATE condizionale.
     *
     * @throws IllegalArgumentException          data mancante o invariata, motivo non valido (400)
     * @throws NoSuchElementException            documento inesistente o di altro tenant (404)
     * @throws CambioDataNonConsentitoException  blocchi o documento cambiato (422)
     */
    public void modifica(Integer tenantId, Integer documentId, LocalDate nuovaData, String motivo) {
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
            Valutazione v = valuta(tenantId, doc, nuovaData);
            if (!v.blocchi.isEmpty()) {
                throw new CambioDataNonConsentitoException(v.blocchi);
            }
            if (fiscalDocumentDAO.updateIssueDate(doc.getId(), nuovaData) != 1) {
                throw new CambioDataNonConsentitoException(List.of(
                        "Il documento è cambiato nel frattempo: data non modificata"));
            }
            // Ricevuta: la riga di ritenuta segue la data (data_evento sempre, periodo se cambia mese)
            String testoLedger = "";
            if (v.ricevuta) {
                WithholdingLedger riga = v.riga;
                boolean cambiaMese = nuovaData.getMonthValue() != dataPrecedente.getMonthValue();
                int righe = cambiaMese
                        ? withholdingLedgerDAO.spostaPeriodo(riga.getId(), nuovaData.getMonthValue(), nuovaData)
                        : withholdingLedgerDAO.updateDataEvento(riga.getId(), nuovaData);
                if (righe != 1) {
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
                            + testoLedger
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
            if (v.limitatoAlMese) {
                LocalDate inizioMese = doc.getIssueDate().withDayOfMonth(1);
                LocalDate fineMese = doc.getIssueDate().withDayOfMonth(doc.getIssueDate().lengthOfMonth());
                if (inizioMese.isAfter(v.min)) {
                    v.min = inizioMese;
                    fonteMin = "inizio del mese";
                }
                if (fineMese.isBefore(v.max)) {
                    v.max = fineMese;
                    fonteMax = "fine del mese";
                }
            }
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
            } else if ((data.isBefore(v.min) || data.isAfter(v.max))
                    // Ricevuta limitata al mese con data in un altro mese: basta il blocco del mese (sotto)
                    && !(v.ricevuta && v.limitatoAlMese && data.getMonthValue() != doc.getIssueDate().getMonthValue())) {
                blocchi(v, "Data fuori intervallo: ammessa dal " + v.min.format(DATA_IT) + " (" + fonteMin
                        + ") al " + v.max.format(DATA_IT) + " (" + fonteMax + ")");
            }
            if (v.ricevuta && data.getYear() == anno && data.getMonthValue() != doc.getIssueDate().getMonthValue()) {
                if (v.limitatoAlMese) {
                    blocchi(v, v.motivoLimiteMese);
                } else if (v.riga != null) {
                    v.avvisi.add(avviso("RICEVUTA_PERIODO_SPOSTATO", "La ritenuta passa dal periodo "
                            + String.format("%02d/%d", v.riga.getPeriodoMese(), v.riga.getPeriodoAnno()) + " a "
                            + String.format("%02d/%d", data.getMonthValue(), v.riga.getPeriodoAnno())));
                }
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
     * sent/paid, liquidazione approved/paid, CU sent/delivered) e limite al mese attuale quando il
     * periodo non si può spostare (riga in un F24 qualsiasi, prenotazione in un rendiconto, riga
     * non da_versare). Le stesse informazioni servono a esegui per aggiornare il ledger.
     */
    private void controlliRicevuta(Integer tenantId, FiscalDocument ric, Valutazione v) {
        List<String> motiviMese = new ArrayList<>();
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
                motiviMese.add("la ritenuta è nell'F24 " + f24.getPeriod() + " (ready)");
            } else if (f24 == null && LEDGER_DA_VERSARE.equals(riga.getStato())) {
                // unico caso in cui il periodo si può spostare (salvo rendiconto)
            } else {
                blocchi(v, "Ritenuta in stato '" + riga.getStato() + "'"
                        + (f24 != null ? " nell'F24 " + f24.getPeriod() + " (" + f24.getStato() + ")" : "")
                        + ": data non modificabile");
            }
            if (!LEDGER_DA_VERSARE.equals(riga.getStato()) && motiviMese.isEmpty()) {
                motiviMese.add("la ritenuta è in stato '" + riga.getStato() + "'");
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
                            motiviMese.add("la prenotazione è nel rendiconto " + s.getPeriod() + " (" + s.getStato() + ")");
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
        if (!motiviMese.isEmpty()) {
            v.limitatoAlMese = true;
            v.motivoLimiteMese = "Cambio di mese non consentito: " + String.join("; ", motiviMese)
                    + ". La data si può spostare solo nel mese attuale.";
        }
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

package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.SettlementBookingDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.document.EliminazioneControlloDTO;
import it.gavia.sostitutoincloud.dto.document.EliminazioneControlloDTO.Avviso;
import it.gavia.sostitutoincloud.dto.document.EliminazioneControlloDTO.DocumentoCoinvolto;
import it.gavia.sostitutoincloud.dto.document.EliminazioneEsitoDTO;
import it.gavia.sostitutoincloud.model.CuRecord;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Eliminazione FISICA di una fattura PM insieme alla ricevuta owner della stessa prenotazione,
 * oppure di una ricevuta senza fattura. Nessun annullamento: è consentita solo sull'ultimo
 * documento della serie (tenant, tipo, anno di issue_date, annullati compresi), così il
 * numero torna libero senza lasciare buchi.
 *
 * <p>Fattura e ricevuta si eliminano in blocco, nella stessa transazione: se uno dei due è
 * bloccato non si elimina nulla. Il controllo è ripetuto dentro il lock di numerazione di
 * ogni serie coinvolta (stesso lock dell'emissione).
 *
 * <p>Le note di credito hanno il loro flusso (NdcService) e restano fuori.
 */
@Service
@Log4j2
public class DocumentEliminazioneService {

    private static final String CODICE_FATTURA = "fattura";
    private static final String CODICE_RICEVUTA = "ricevuta";
    private static final String CODICE_NOTA_CREDITO = "nota_credito";
    private static final String STATO_ANNULLATA = "annullata";
    private static final Set<String> STATI_ELIMINABILI = Set.of("draft", "ready");

    private static final String LEDGER_DA_VERSARE = "da_versare";
    private static final String LEDGER_VERSATA = "versata";
    private static final String F24_READY = "ready";
    private static final Set<String> STATI_F24_VERSATO = Set.of("sent", "paid");
    private static final Set<String> STATI_SETTLEMENT_BLOCCATI = Set.of("approved", "paid");
    private static final Set<String> STATI_CU_CONSEGNATA = Set.of("sent", "delivered");

    private static final int MOTIVO_MIN = 5;
    private static final int MOTIVO_MAX = 500;
    private static final DateTimeFormatter FMT_EMAIL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final F24RecordDAO f24RecordDAO;
    private final F24Service f24Service;
    private final SettlementBookingDAO settlementBookingDAO;
    private final SettlementDAO settlementDAO;
    private final SettlementService settlementService;
    private final CuRecordDAO cuRecordDAO;
    private final BookingService bookingService;
    private final NdcService ndcService;
    private final VociFatturaPmService vociFatturaPmService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public DocumentEliminazioneService(FiscalDocumentDAO fiscalDocumentDAO,
                                       TipoDocumentoDAO tipoDocumentoDAO,
                                       StatoDocumentoDAO statoDocumentoDAO,
                                       WithholdingLedgerDAO withholdingLedgerDAO,
                                       F24RecordDAO f24RecordDAO,
                                       F24Service f24Service,
                                       SettlementBookingDAO settlementBookingDAO,
                                       SettlementDAO settlementDAO,
                                       SettlementService settlementService,
                                       CuRecordDAO cuRecordDAO,
                                       BookingService bookingService,
                                       NdcService ndcService,
                                       VociFatturaPmService vociFatturaPmService,
                                       AuditService auditService,
                                       PlatformTransactionManager transactionManager) {
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.f24RecordDAO = f24RecordDAO;
        this.f24Service = f24Service;
        this.settlementBookingDAO = settlementBookingDAO;
        this.settlementDAO = settlementDAO;
        this.settlementService = settlementService;
        this.cuRecordDAO = cuRecordDAO;
        this.bookingService = bookingService;
        this.ndcService = ndcService;
        this.vociFatturaPmService = vociFatturaPmService;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Serie di numerazione: lock e controllo "ultimo della serie". */
    private record Serie(Integer tipoId, Integer anno) { }

    /** Esito della valutazione: cosa si elimina, cosa si tocca, blocchi e avvisi. */
    private static final class Piano {
        FiscalDocument fattura;
        FiscalDocument ricevuta;
        WithholdingLedger riga;
        F24Record f24;
        Settlement settlement;
        final List<String> blocchi = new ArrayList<>();
        final List<Avviso> avvisi = new ArrayList<>();

        List<FiscalDocument> documenti() {
            List<FiscalDocument> l = new ArrayList<>();
            if (fattura != null) l.add(fattura);
            if (ricevuta != null) l.add(ricevuta);
            return l;
        }
    }

    // ── API ──────────────────────────────────────────────────────────────────

    /**
     * Controllo preventivo: non scrive nulla.
     *
     * @throws NoSuchElementException documento inesistente o di altro tenant (404)
     */
    public EliminazioneControlloDTO controllo(Integer tenantId, Integer documentId) {
        Piano piano = valuta(tenantId, carica(tenantId, documentId));
        return EliminazioneControlloDTO.builder()
                .consentito(piano.blocchi.isEmpty())
                .blocchi(piano.blocchi)
                .avvisi(piano.avvisi)
                .documenti(coinvolti(piano))
                .build();
    }

    /**
     * Elimina fisicamente il documento (e la ricevuta collegata, se si parte dalla fattura).
     *
     * @throws IllegalArgumentException          motivo mancante o fuori misura (400)
     * @throws NoSuchElementException            documento inesistente o di altro tenant (404)
     * @throws EliminazioneNonConsentitaException blocchi ricalcolati dentro il lock (422)
     */
    public EliminazioneEsitoDTO elimina(Integer tenantId, Integer documentId, String motivo) {
        String motivoValido = validaMotivo(motivo);
        // Prima valutazione fuori dal lock: serve solo a sapere quali serie bloccare
        Piano preliminare = valuta(tenantId, carica(tenantId, documentId));
        List<Serie> serieDaBloccare = serie(preliminare);

        return transactionTemplate.execute(status -> {
            // Ordine fisso (tipo, anno) per evitare deadlock con altre eliminazioni/emissioni
            serieDaBloccare.forEach(s -> fiscalDocumentDAO.lockNumerazione(tenantId, s.tipoId(), s.anno()));

            // Dentro il lock: si ricalcola tutto, il controllo fatto prima non vale
            Piano piano = valuta(tenantId, carica(tenantId, documentId));
            if (!piano.blocchi.isEmpty()) {
                throw new EliminazioneNonConsentitaException(piano.blocchi);
            }
            if (!serieDaBloccare.containsAll(serie(piano))) {
                throw new EliminazioneNonConsentitaException(List.of(
                        "I documenti della prenotazione sono cambiati durante l'operazione: riprovare"));
            }
            return esegui(tenantId, piano, motivoValido);
        });
    }

    // ── valutazione ──────────────────────────────────────────────────────────

    private Piano valuta(Integer tenantId, FiscalDocument doc) {
        Piano piano = new Piano();
        Integer tipoFatturaId = tipo(CODICE_FATTURA).getId();
        Integer tipoRicevutaId = tipo(CODICE_RICEVUTA).getId();
        Integer annullataId = stato(STATO_ANNULLATA).getId();

        boolean isFattura = tipoFatturaId.equals(doc.getFkTipoDocumentoId());
        boolean isRicevuta = tipoRicevutaId.equals(doc.getFkTipoDocumentoId());
        if (!isFattura && !isRicevuta) {
            piano.blocchi.add("Si eliminano solo fatture PM e ricevute: le note di credito si annullano dal loro dettaglio");
            return piano;
        }

        List<FiscalDocument> docsBooking = doc.getFkBookingId() != null
                ? fiscalDocumentDAO.findByBookingId(doc.getFkBookingId())
                : List.of(doc);
        List<FiscalDocument> ricevuteAttive = docsBooking.stream()
                .filter(d -> tipoRicevutaId.equals(d.getFkTipoDocumentoId()))
                .filter(d -> !annullataId.equals(d.getFkStatoDocumentoId()))
                .toList();
        boolean ricevuteMultiple = false;

        if (isFattura) {
            piano.fattura = doc;
            // La ricevuta si cerca dalla prenotazione (non solo da fk_documento_collegato_id)
            if (ricevuteAttive.size() > 1) {
                ricevuteMultiple = true;
                piano.blocchi.add("La prenotazione ha più ricevute attive (" + numeri(ricevuteAttive)
                        + "): eliminazione non consentita");
            } else if (ricevuteAttive.size() == 1) {
                piano.ricevuta = ricevuteAttive.get(0);
            }
        } else {
            List<FiscalDocument> fattureAttive = docsBooking.stream()
                    .filter(d -> tipoFatturaId.equals(d.getFkTipoDocumentoId()))
                    .filter(d -> !annullataId.equals(d.getFkStatoDocumentoId()))
                    .toList();
            if (!fattureAttive.isEmpty()) {
                piano.blocchi.add("La prenotazione ha la fattura " + numeri(fattureAttive)
                        + ": eliminare dal dettaglio della fattura, che elimina anche la ricevuta");
            }
            piano.ricevuta = doc;
        }

        // Controlli della fattura
        List<FiscalDocument> ndcAttive = List.of();
        if (piano.fattura != null) {
            controlliComuni(tenantId, piano.fattura, "", piano);
            ndcAttive = ndcService.ndcAttive(piano.fattura.getId(), tipo(CODICE_NOTA_CREDITO));
            if (!ndcAttive.isEmpty()) {
                piano.blocchi.add("Nota di credito attiva collegata (" + numeri(ndcAttive)
                        + "): eliminazione non consentita");
            }
        }

        // Controlli della ricevuta: prefissati col numero se si parte dalla fattura
        if (piano.ricevuta != null) {
            String prefisso = piano.fattura != null ? piano.ricevuta.getDocumentNumber() + ": " : "";
            controlliComuni(tenantId, piano.ricevuta, prefisso, piano);
            controlliRicevuta(tenantId, piano, prefisso);
        }

        // Altri documenti sulla prenotazione (NDC o ricevute annullate, ...): resterebbero
        // dopo l'eliminazione, il booking resterebbe doc_issued e lo split bloccato
        Set<Integer> inPiano = piano.documenti().stream().map(FiscalDocument::getId).collect(Collectors.toSet());
        Set<Integer> giaSegnalati = ndcAttive.stream().map(FiscalDocument::getId)
                .collect(Collectors.toCollection(HashSet::new));
        if (ricevuteMultiple) ricevuteAttive.forEach(r -> giaSegnalati.add(r.getId()));
        if (piano.ricevuta != null && piano.fattura == null) {
            // Dal dettaglio ricevuta la fattura attiva è già segnalata sopra
            docsBooking.stream()
                    .filter(d -> tipoFatturaId.equals(d.getFkTipoDocumentoId()))
                    .filter(d -> !annullataId.equals(d.getFkStatoDocumentoId()))
                    .forEach(d -> giaSegnalati.add(d.getId()));
        }
        List<FiscalDocument> altri = docsBooking.stream()
                .filter(d -> !inPiano.contains(d.getId()) && !giaSegnalati.contains(d.getId()))
                .toList();
        if (!altri.isEmpty()) {
            piano.blocchi.add("La prenotazione ha altri documenti (" + altri.stream()
                    .map(d -> d.getDocumentNumber() + " " + codiceStato(d.getFkStatoDocumentoId()))
                    .collect(Collectors.joining(", ")) + "): eliminazione non consentita");
        }

        // Bollo addebitato al proprietario: una sola volta per prenotazione
        if (doc.getFkBookingId() != null && vociFatturaPmService.vociDaSplit(doc.getFkBookingId()).stream()
                .anyMatch(VociFatturaPmService.VoceFatturaPm::fuoriCampo)) {
            piano.avvisi.add(Avviso.builder().codice("BOLLO_ADDEBITATO")
                    .messaggio("Lo split ha la voce bollo addebitata al proprietario: il canone della ricevuta "
                            + "ne tiene conto. Alla riemissione tenere coerenti fattura e ricevuta")
                    .build());
        }
        return piano;
    }

    /** Stato, file SDI, ultimo della serie, email già inviata. */
    private void controlliComuni(Integer tenantId, FiscalDocument d, String prefisso, Piano piano) {
        List<String> blocchi = piano.blocchi;
        if (d.getEmailInviataAt() != null) {
            piano.avvisi.add(Avviso.builder().codice("EMAIL_GIA_INVIATA")
                    .messaggio(d.getDocumentNumber() + " è già stato inviato via email a "
                            + (d.getEmailInviataA() != null ? d.getEmailInviataA() : "—")
                            + " il " + d.getEmailInviataAt().format(FMT_EMAIL)
                            + ": l'eliminazione non lo ritira dal destinatario")
                    .build());
        }
        String stato = codiceStato(d.getFkStatoDocumentoId());
        if (!STATI_ELIMINABILI.contains(stato)) {
            blocchi.add(prefisso + "documento in stato '" + stato + "': eliminabile solo in bozza o pronto");
        }
        if (notBlank(d.getSdiProgressivo()) || notBlank(d.getSdiFilePath())) {
            blocchi.add(prefisso + "file SDI già generato"
                    + (notBlank(d.getSdiProgressivo()) ? " (progressivo " + d.getSdiProgressivo() + ")" : "")
                    + ": eliminazione non consentita");
        }
        String ultimo = ultimoDellaSerie(tenantId, d);
        if (ultimo != null) {
            String nomeSerie = tipo(CODICE_FATTURA).getId().equals(d.getFkTipoDocumentoId()) ? "fattura" : "ricevuta";
            blocchi.add(prefisso + "non è l'ultima " + nomeSerie + " del " + d.getIssueDate().getYear()
                    + ": l'ultima è " + ultimo);
        }
    }

    /** Ritenuta, F24, liquidazione e CU della ricevuta. */
    private void controlliRicevuta(Integer tenantId, Piano piano, String prefisso) {
        FiscalDocument ric = piano.ricevuta;
        WithholdingLedger riga = withholdingLedgerDAO.findByFiscalDocumentId(ric.getId()).orElse(null);
        if (riga == null) {
            piano.blocchi.add(prefisso + "nessuna riga di ritenuta trovata nel registro: verificare prima di eliminare");
        } else {
            piano.riga = riga;
            F24Record f24 = riga.getFkF24RecordId() != null
                    ? f24RecordDAO.findById(riga.getFkF24RecordId()).orElse(null) : null;
            if (riga.getFkNdcId() != null) {
                piano.blocchi.add(prefisso + "ritenuta collegata a una nota di credito: eliminazione non consentita");
            } else if (f24 != null && STATI_F24_VERSATO.contains(f24.getStato())) {
                piano.blocchi.add(prefisso + "ritenuta già in un F24 " + f24.getStato()
                        + " (periodo " + f24.getPeriod() + "): eliminazione non consentita");
            } else if (f24 != null && F24_READY.equals(f24.getStato())
                    && (LEDGER_VERSATA.equals(riga.getStato()) || LEDGER_DA_VERSARE.equals(riga.getStato()))) {
                piano.f24 = f24;
                piano.avvisi.add(avvisoF24(f24, riga));
            } else if (f24 == null && LEDGER_DA_VERSARE.equals(riga.getStato())) {
                // ritenuta non ancora in un F24: nessun effetto sull'F24
            } else {
                piano.blocchi.add(prefisso + "ritenuta in stato '" + riga.getStato() + "'"
                        + (f24 != null ? " nell'F24 " + f24.getPeriod() + " (" + f24.getStato() + ")" : "")
                        + ": eliminazione non consentita");
            }
        }

        // Liquidazione della prenotazione
        if (ric.getFkBookingId() != null) {
            settlementBookingDAO.findSettlementIdByBookingId(ric.getFkBookingId())
                    .flatMap(settlementDAO::findById)
                    .filter(s -> tenantId.equals(s.getFkTenantId()))
                    .ifPresent(s -> {
                        if (STATI_SETTLEMENT_BLOCCATI.contains(s.getStato())) {
                            piano.blocchi.add(prefisso + "prenotazione nel rendiconto " + s.getPeriod()
                                    + " già " + ("paid".equals(s.getStato()) ? "pagato" : "approvato")
                                    + ": eliminazione non consentita");
                        } else {
                            piano.settlement = s;
                            boolean unica = settlementBookingDAO.findBySettlementId(s.getId()).size() <= 1;
                            piano.avvisi.add(Avviso.builder().codice("LIQUIDAZIONE_DA_RICALCOLARE")
                                    .messaggio("La prenotazione è nel rendiconto " + s.getPeriod() + " (" + s.getStato()
                                            + "): verrà tolta e il rendiconto ricalcolato"
                                            + (unica ? "; senza altre prenotazioni il rendiconto verrà eliminato" : ""))
                                    .build());
                        }
                    });
        }

        // CU del proprietario per l'anno della ritenuta
        Integer annoCu = riga != null && riga.getPeriodoAnno() != null ? riga.getPeriodoAnno() : ric.getIssueDate().getYear();
        if (ric.getFkOwnerId() != null) {
            cuRecordDAO.findByTenantOwnerYear(tenantId, ric.getFkOwnerId(), annoCu).ifPresent((CuRecord cu) -> {
                if (STATI_CU_CONSEGNATA.contains(cu.getStato())) {
                    piano.blocchi.add(prefisso + "CU " + annoCu + " del proprietario già "
                            + ("sent".equals(cu.getStato()) ? "inviata" : "consegnata")
                            + ": eliminazione non consentita");
                } else if (ritenuteResidue(tenantId, ric.getFkOwnerId(), annoCu, riga) == 0) {
                    // Ultima ritenuta valida dell'anno: la rigenerazione eliminerà la CU (CuService)
                    piano.avvisi.add(Avviso.builder().codice("CU_VERRA_ELIMINATA")
                            .messaggio("La CU " + annoCu + " del proprietario verrà eliminata alla "
                                    + "rigenerazione perché non restano ritenute")
                            .build());
                } else {
                    piano.avvisi.add(Avviso.builder().codice("CU_DA_RIGENERARE")
                            .messaggio("La CU " + annoCu + " del proprietario (" + cu.getStato()
                                    + ") resta con gli importi attuali finché non viene rigenerata")
                            .build());
                }
            });
        }
    }

    /**
     * Ritenute valide (fk_ndc_id IS NULL, stesso filtro della CU) del proprietario nell'anno che
     * resterebbero dopo questa operazione: si tolgono le righe eliminate dalla stessa operazione.
     * Fattura + ricevuta: la fattura non ha righe di registro, quindi l'unica tolta è quella
     * della ricevuta.
     */
    private int ritenuteResidue(Integer tenantId, Integer ownerId, Integer anno, WithholdingLedger rigaEliminata) {
        int valide = cuRecordDAO.countRitenuteValide(tenantId, ownerId, anno);
        boolean rigaConta = rigaEliminata != null
                && rigaEliminata.getFkNdcId() == null
                && ownerId.equals(rigaEliminata.getFkOwnerId())
                && anno.equals(rigaEliminata.getPeriodoAnno());
        return Math.max(0, valide - (rigaConta ? 1 : 0));
    }

    /** F24_AGGIORNATO, con "resterà a zero" e "crediti rilasciati" quando applicabile. */
    private Avviso avvisoF24(F24Record f24, WithholdingLedger riga) {
        BigDecimal ritenuta = nz(riga.getRitenutaAmount());
        BigDecimal nuovoTotale = nz(f24.getTotalAmount()).subtract(ritenuta);
        int righeResidue = (f24.getWithholdingsCount() != null ? f24.getWithholdingsCount() : 0) - 1;
        BigDecimal credito = nz(f24.getImportoCredito());
        StringBuilder msg = new StringBuilder("La ritenuta di € ").append(ritenuta)
                .append(" è nell'F24 ").append(f24.getPeriod())
                .append(" (ready): il totale dell'F24 verrà aggiornato automaticamente");
        if (righeResidue <= 0 || nuovoTotale.signum() <= 0) {
            msg.append("; l'F24 resterà a zero");
        }
        if (credito.signum() > 0 && credito.compareTo(nuovoTotale.max(BigDecimal.ZERO)) > 0) {
            msg.append("; i crediti compensati (€ ").append(credito).append(") verranno rilasciati");
        }
        return Avviso.builder().codice("F24_AGGIORNATO").messaggio(msg.toString())
                .f24Id(f24.getId()).f24Anno(f24.getPeriodoAnno()).f24Mese(f24.getPeriodoMese())
                .build();
    }

    /**
     * null se il documento è l'ultimo della sua serie (progressivo più alto per tenant, tipo e
     * anno di issue_date, annullati compresi), altrimenti il numero dell'ultimo.
     */
    private String ultimoDellaSerie(Integer tenantId, FiscalDocument d) {
        int anno = d.getIssueDate().getYear();
        int ultimo = fiscalDocumentDAO.getNextProgressiveNumber(tenantId, d.getFkTipoDocumentoId(), anno) - 1;
        String[] parti = d.getDocumentNumber() != null ? d.getDocumentNumber().split("-") : new String[0];
        Integer progressivo = null;
        if (parti.length >= 3) {
            try {
                progressivo = Integer.parseInt(parti[2]);
            } catch (NumberFormatException e) {
                log.warn("DocumentEliminazioneService - numero documento non standard: {}", d.getDocumentNumber());
            }
        }
        if (progressivo != null && progressivo == ultimo) return null;
        return parti.length >= 3 ? parti[0] + "-" + anno + "-" + String.format("%04d", ultimo) : "n/d";
    }

    // ── esecuzione (dentro transazione e lock) ───────────────────────────────

    private EliminazioneEsitoDTO esegui(Integer tenantId, Piano piano, String motivo) {
        List<DocumentoCoinvolto> eliminati = coinvolti(piano);
        Integer bookingId = piano.documenti().get(0).getFkBookingId();

        // Ricevuta: ritenuta, F24, rendiconto, poi il documento
        if (piano.ricevuta != null) {
            FiscalDocument ric = piano.ricevuta;
            WithholdingLedger riga = piano.riga;
            // a+b. La riga di ledger (FK RESTRICT) va cancellata prima del documento; se era in un
            //      F24 ready, il totale si ricalcola subito dopo sulle righe rimaste agganciate
            withholdingLedgerDAO.deleteById(riga.getId());
            if (piano.f24 != null) {
                f24Service.ricalcolaTotale(piano.f24.getId());
            }
            // c. Rendiconto pending/calculated: la prenotazione esce e si ricalcola (come emettiNdc)
            if (piano.settlement != null) {
                settlementBookingDAO.deleteByBookingId(ric.getFkBookingId());
                settlementService.ricalcolaTotali(piano.settlement.getId(), tenantId);
            }
            // d. Documento ricevuta
            fiscalDocumentDAO.deleteById(ric.getId());
            auditService.log("document.delete", "FiscalDocument", null,
                    testoAudit(ric, motivo)
                            + " Ritenuta: riga registro id=" + riga.getId()
                            + ", proprietario id=" + riga.getFkOwnerId()
                            + ", periodo " + String.format("%02d", riga.getPeriodoMese()) + "/" + riga.getPeriodoAnno()
                            + ", importo € " + nz(riga.getRitenutaAmount())
                            + (piano.f24 != null ? ", F24 id=" + piano.f24.getId() + " ricalcolato" : ", nessun F24")
                            + (piano.settlement != null ? ", tolta dal rendiconto id=" + piano.settlement.getId() : "")
                            + ".");
        }

        // Fattura
        if (piano.fattura != null) {
            fiscalDocumentDAO.deleteById(piano.fattura.getId());
            auditService.log("document.delete", "FiscalDocument", null, testoAudit(piano.fattura, motivo));
        }

        if (bookingId != null) {
            bookingService.aggiornaStato(bookingId);
        }
        log.info("DocumentEliminazioneService.elimina() - tenant={} booking={} eliminati={}",
                tenantId, bookingId, eliminati.stream().map(DocumentoCoinvolto::getNumero).toList());
        return EliminazioneEsitoDTO.builder()
                .bookingId(bookingId)
                .eliminati(eliminati)
                .avvisi(piano.avvisi)
                .build();
    }

    private String testoAudit(FiscalDocument d, String motivo) {
        return "Eliminato documento " + d.getDocumentNumber() + " (id=" + d.getId() + ", "
                + nomeTipo(d) + ", eliminazione fisica, numero liberato). Motivo: " + motivo + "."
                + (d.getEmailInviataAt() != null
                    ? " Email già inviata a " + (d.getEmailInviataA() != null ? d.getEmailInviataA() : "—")
                        + " il " + d.getEmailInviataAt().format(FMT_EMAIL) + "."
                    : "");
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private FiscalDocument carica(Integer tenantId, Integer documentId) {
        return fiscalDocumentDAO.findById(documentId)
                .filter(d -> tenantId.equals(d.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Documento non trovato: id=" + documentId));
    }

    private String validaMotivo(String motivo) {
        String m = motivo != null ? motivo.trim() : "";
        if (m.length() < MOTIVO_MIN || m.length() > MOTIVO_MAX) {
            throw new IllegalArgumentException("Motivo obbligatorio, da " + MOTIVO_MIN + " a " + MOTIVO_MAX + " caratteri");
        }
        return m;
    }

    /** Serie coinvolte, in ordine fisso (tipo, anno). */
    private List<Serie> serie(Piano piano) {
        return piano.documenti().stream()
                .map(d -> new Serie(d.getFkTipoDocumentoId(), d.getIssueDate().getYear()))
                .distinct()
                .sorted(Comparator.comparing(Serie::tipoId).thenComparing(Serie::anno))
                .toList();
    }

    private List<DocumentoCoinvolto> coinvolti(Piano piano) {
        return piano.documenti().stream()
                .map(d -> DocumentoCoinvolto.builder().id(d.getId()).numero(d.getDocumentNumber())
                        .tipo(nomeTipo(d)).build())
                .toList();
    }

    /** Numeri documento separati da virgola, per i messaggi. */
    private String numeri(List<FiscalDocument> docs) {
        return docs.stream().map(FiscalDocument::getDocumentNumber).filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private String nomeTipo(FiscalDocument d) {
        return tipo(CODICE_FATTURA).getId().equals(d.getFkTipoDocumentoId()) ? CODICE_FATTURA : CODICE_RICEVUTA;
    }

    private TipoDocumento tipo(String codice) {
        return tipoDocumentoDAO.findByCodice(codice)
                .orElseThrow(() -> new IllegalStateException("Tipo documento lookup non trovato: " + codice));
    }

    private StatoDocumento stato(String codice) {
        return statoDocumentoDAO.findByCodice(codice)
                .orElseThrow(() -> new IllegalStateException("Stato documento lookup non trovato: " + codice));
    }

    private String codiceStato(Integer statoId) {
        return statoId == null ? null : statoDocumentoDAO.findById(statoId).map(StatoDocumento::getCodice).orElse(null);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

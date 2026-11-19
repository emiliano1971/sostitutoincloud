package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dto.document.AnnulloInvioManualeControlloDTO;
import it.gavia.sostitutoincloud.dto.document.AnnulloInvioManualeControlloDTO.Avviso;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Annullo di un invio manuale allo SDI marcato per errore: fattura PM o nota di credito in
 * 'sent_sdi' SENZA progressivo né file (unico modo per distinguere un invio manuale, dal
 * pulsante "Inviata manualmente" o da "Scarica e marca come inviati", da un invio vero) torna
 * 'ready'. Non tocca l'invio SDI vero (SdiXmlService) né NdcService.
 */
@Service
@Log4j2
public class SdiManualeAnnulloService {

    private static final String CODICE_FATTURA = "fattura";
    private static final String CODICE_NOTA_CREDITO = "nota_credito";
    private static final String STATO_SENT_SDI = "sent_sdi";
    private static final int MOTIVO_MIN = 5;
    private static final int MOTIVO_MAX = 500;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final NdcService ndcService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public SdiManualeAnnulloService(FiscalDocumentDAO fiscalDocumentDAO,
                                    TipoDocumentoDAO tipoDocumentoDAO,
                                    StatoDocumentoDAO statoDocumentoDAO,
                                    NdcService ndcService,
                                    AuditService auditService,
                                    PlatformTransactionManager transactionManager) {
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.ndcService = ndcService;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Controllo preventivo: non scrive nulla.
     *
     * @throws NoSuchElementException documento inesistente o di altro tenant (404)
     */
    public AnnulloInvioManualeControlloDTO controllo(Integer tenantId, Integer documentId) {
        FiscalDocument doc = carica(tenantId, documentId);
        List<String> blocchi = blocchi(doc);
        return AnnulloInvioManualeControlloDTO.builder()
                .consentito(blocchi.isEmpty())
                .blocchi(blocchi)
                .avvisi(blocchi.isEmpty() ? avvisi(doc) : List.of())
                .inviatoManualmenteAt(doc.getSdiSentAt())
                .build();
    }

    /**
     * Riporta il documento a 'ready'. Blocchi ricalcolati nella transazione; UPDATE condizionale
     * (0 righe = documento cambiato nel frattempo → 422).
     *
     * @throws IllegalArgumentException      motivo mancante o fuori misura (400)
     * @throws NoSuchElementException        documento inesistente o di altro tenant (404)
     * @throws AnnulloNonConsentitoException blocchi o documento cambiato (422)
     */
    public void annulla(Integer tenantId, Integer documentId, String motivo) {
        String m = motivo != null ? motivo.trim() : "";
        if (m.length() < MOTIVO_MIN || m.length() > MOTIVO_MAX) {
            throw new IllegalArgumentException("Motivo obbligatorio, da " + MOTIVO_MIN + " a " + MOTIVO_MAX + " caratteri");
        }
        transactionTemplate.executeWithoutResult(status -> {
            FiscalDocument doc = carica(tenantId, documentId);
            List<String> blocchi = blocchi(doc);
            if (!blocchi.isEmpty()) {
                throw new AnnulloNonConsentitoException(blocchi);
            }
            if (fiscalDocumentDAO.annullaSdiManuale(doc.getId()) != 1) {
                throw new AnnulloNonConsentitoException(List.of(
                        "Il documento è cambiato nel frattempo: annullo non eseguito"));
            }
            auditService.log("sdi.manuale.annulla", "FiscalDocument", doc.getId(),
                    "Annullato l'invio manuale allo SDI di " + doc.getDocumentNumber()
                            + " (marcato il " + (doc.getSdiSentAt() != null ? doc.getSdiSentAt().format(FMT) : "—")
                            + "): documento di nuovo pronto. Motivo: " + m + ".");
            log.info("SdiManualeAnnulloService.annulla() - tenant={} doc={} numero={}",
                    tenantId, doc.getId(), doc.getDocumentNumber());
        });
    }

    // ── regole ───────────────────────────────────────────────────────────────

    private List<String> blocchi(FiscalDocument doc) {
        List<String> blocchi = new ArrayList<>();
        boolean fattura = tipoId(CODICE_FATTURA).equals(doc.getFkTipoDocumentoId());
        boolean ndc = tipoId(CODICE_NOTA_CREDITO).equals(doc.getFkTipoDocumentoId());
        if (!fattura && !ndc) {
            blocchi.add("L'annullo dell'invio manuale vale solo per fatture PM e note di credito");
            return blocchi;
        }
        String stato = statoDocumentoDAO.findById(doc.getFkStatoDocumentoId())
                .map(StatoDocumento::getCodice).orElse(null);
        if (!STATO_SENT_SDI.equals(stato)) {
            blocchi.add("Documento in stato '" + stato + "': si annulla solo un documento marcato come inviato");
        }
        if (notBlank(doc.getSdiProgressivo()) || notBlank(doc.getSdiFilePath())) {
            blocchi.add("Il documento ha un file SDI generato dall'applicazione"
                    + (notBlank(doc.getSdiProgressivo()) ? " (progressivo " + doc.getSdiProgressivo() + ")" : "")
                    + ": non è un invio manuale e non si annulla");
        }
        if (fattura) {
            TipoDocumento tipoNdc = tipoDocumentoDAO.findByCodice(CODICE_NOTA_CREDITO)
                    .orElseThrow(() -> new IllegalStateException("Tipo documento lookup non trovato: nota_credito"));
            List<FiscalDocument> attive = ndcService.ndcAttive(doc.getId(), tipoNdc);
            if (!attive.isEmpty()) {
                blocchi.add("Nota di credito attiva collegata (" + attive.stream()
                        .map(FiscalDocument::getDocumentNumber).collect(Collectors.joining(", "))
                        + "): annullo non consentito");
            }
        }
        return blocchi;
    }

    private List<Avviso> avvisi(FiscalDocument doc) {
        List<Avviso> avvisi = new ArrayList<>();
        boolean ndc = tipoId(CODICE_NOTA_CREDITO).equals(doc.getFkTipoDocumentoId());
        if (ndc) {
            avvisi.add(Avviso.builder().codice("NDC_RIAPERTA")
                    .messaggio("Tornata pronta, la nota di credito potrà essere reinviata oppure annullata: "
                            + "annullarla ripristina le ritenute e la prenotazione. Se è stata trasmessa davvero "
                            + "all'Agenzia delle Entrate, annullarla rende i dati incoerenti con quanto inviato.")
                    .build());
        }
        avvisi.add(Avviso.builder().codice("RIAPERTURA")
                .messaggio("Se " + (ndc ? "la nota di credito" : "la fattura") + " è stata trasmessa davvero fuori "
                        + "dall'applicazione (anche dopo \"Scarica e marca come inviati\"), non tornare indietro: "
                        + (ndc ? "lasciala inviata." : "emetti una nota di credito."))
                .build());
        return avvisi;
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

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.booking.BookingDetailDTO;
import it.gavia.sostitutoincloud.dto.fiscal.WithholdingLedgerDTO;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.model.WithholdingLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Servizio del registro analitico delle ritenute d'acconto (withholding_ledger).
 * Registra la singola ritenuta operata all'emissione della ricevuta owner e
 * fornisce le aggregazioni per periodo usate dal flusso F24.
 */
@Service
@Log4j2
public class WithholdingLedgerService {

    private static final String CODICE_RICEVUTA = "ricevuta";
    private static final String STATO_DA_VERSARE = "da_versare";
    private static final String STATO_STORNATA = "stornata";
    private static final String STATO_CREDITO_IMPOSTA = "credito_imposta";

    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final BookingService bookingService;
    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final AuditService auditService;

    public WithholdingLedgerService(WithholdingLedgerDAO withholdingLedgerDAO,
                                    BookingService bookingService,
                                    FiscalDocumentDAO fiscalDocumentDAO,
                                    TipoDocumentoDAO tipoDocumentoDAO,
                                    AuditService auditService) {
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.bookingService = bookingService;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.auditService = auditService;
    }

    /**
     * Registra la ritenuta d'acconto generata da una ricevuta owner.
     * Una sola ritenuta per documento fiscale (vincolo uq_withholding_per_document).
     */
    public WithholdingLedger registraRitenuta(Integer tenantId, Integer bookingId, Integer fiscalDocumentId) {
        // 1. Idempotenza: niente doppia registrazione per lo stesso documento
        if (withholdingLedgerDAO.findByFiscalDocumentId(fiscalDocumentId).isPresent()) {
            throw new IllegalStateException("Ritenuta già registrata per questo documento");
        }

        // 2. Carica booking (filtrato per tenant) e documento fiscale
        BookingDetailDTO booking = bookingService.findById(tenantId, bookingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Prenotazione non trovata per questo tenant: id=" + bookingId));
        FiscalDocument document = fiscalDocumentDAO.findById(fiscalDocumentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Documento fiscale non trovato: id=" + fiscalDocumentId));

        // 3. Solo le ricevute (ricevuta owner) generano ritenute
        TipoDocumento tipoRicevuta = tipoDocumentoDAO.findByCodice(CODICE_RICEVUTA)
                .orElseThrow(() -> new IllegalArgumentException("Tipo documento lookup non trovato: ricevuta"));
        if (!tipoRicevuta.getId().equals(document.getFkTipoDocumentoId())) {
            throw new IllegalArgumentException("Solo le ricevute generano ritenute");
        }

        // 4. Periodo di competenza dalla data di emissione
        Integer periodoMese = document.getIssueDate().getMonthValue();
        Integer periodoAnno = document.getIssueDate().getYear();

        // 5. Costruisce e salva il record di ledger
        BigDecimal aliquotaRitenuta = booking.getSplitEconomico() != null
                ? booking.getSplitEconomico().getAliquotaRitenuta()
                : null;
        WithholdingLedger ledger = WithholdingLedger.builder()
                .fkTenantId(tenantId)
                .fkOwnerId(booking.getFkOwnerId())
                .fkBookingId(bookingId)
                .fkFiscalDocumentId(fiscalDocumentId)
                .periodoMese(periodoMese)
                .periodoAnno(periodoAnno)
                .canoneLocazione(document.getImponibile())
                .aliquotaRitenuta(aliquotaRitenuta)
                .ritenutaAmount(document.getRitenutaAmount())
                .dataEvento(document.getIssueDate())
                .stato(STATO_DA_VERSARE)
                .build();
        WithholdingLedger saved = withholdingLedgerDAO.insert(ledger);

        log.info("WithholdingLedger - registrata ritenuta tenant={} owner={} booking={} documento={} importo={} periodo={}/{}",
                tenantId, booking.getFkOwnerId(), bookingId, fiscalDocumentId,
                saved.getRitenutaAmount(), periodoMese, periodoAnno);

        auditService.log("withholding.register", "WithholdingLedger", saved.getId(),
                "Registrata ritenuta €" + saved.getRitenutaAmount()
                        + " per booking " + booking.getExternalBookingId()
                        + " periodo " + periodoMese + "/" + periodoAnno);

        return saved;
    }

    // ── storno da nota di credito (migration 026) ─────────────────────────────

    /** Ritenuta non ancora in un F24: stornata e collegata alla NDC. */
    public void stornaRitenuta(Integer tenantId, Integer ledgerId, Integer ndcId) {
        withholdingLedgerDAO.updateStorno(ledgerId, STATO_STORNATA, ndcId, false);
        auditService.log("withholding.storno", "WithholdingLedger", ledgerId,
                "Ritenuta stornata da nota di credito id=" + ndcId);
    }

    /**
     * Ritenuta in un F24 non pagato: stornata e sganciata dall'F24. Il totale dell'F24 va
     * ricalcolato dal chiamante (F24Service.ricalcolaTotale: F24Service dipende già da
     * questo service, il contrario creerebbe un ciclo).
     */
    public void stornaRitenutaDaF24(Integer tenantId, Integer ledgerId, Integer ndcId) {
        withholdingLedgerDAO.updateStorno(ledgerId, STATO_STORNATA, ndcId, true);
        auditService.log("withholding.storno", "WithholdingLedger", ledgerId,
                "Ritenuta stornata e rimossa dall'F24 non pagato, nota di credito id=" + ndcId);
    }

    /**
     * Ritenuta già versata (F24 pagato o inviato): la riga originale resta 'versata' ma viene
     * collegata alla NDC (esce da CU e liquidazioni) e si registra una riga 'credito_imposta'
     * con ritenuta negativa, compensabile in un F24 successivo.
     * Le colonne obbligatorie del ledger arrivano dalla riga originale; periodo e data dalla NDC.
     */
    public WithholdingLedger registraCredito(Integer tenantId, WithholdingLedger originale,
                                             FiscalDocument ndc, BigDecimal importoCredito) {
        withholdingLedgerDAO.updateStorno(originale.getId(), originale.getStato(), ndc.getId(), false);
        WithholdingLedger credito = withholdingLedgerDAO.insert(WithholdingLedger.builder()
                .fkTenantId(tenantId)
                .fkOwnerId(originale.getFkOwnerId())
                .fkBookingId(originale.getFkBookingId())
                .fkFiscalDocumentId(ndc.getId())
                .periodoMese(ndc.getIssueDate().getMonthValue())
                .periodoAnno(ndc.getIssueDate().getYear())
                .canoneLocazione(BigDecimal.ZERO)
                .aliquotaRitenuta(originale.getAliquotaRitenuta())
                .ritenutaAmount(importoCredito)
                .dataEvento(ndc.getIssueDate())
                .stato(STATO_CREDITO_IMPOSTA)
                .fkNdcId(ndc.getId())
                .build());
        auditService.log("withholding.credito", "WithholdingLedger", credito.getId(),
                "Credito d'imposta €" + importoCredito + " da nota di credito " + ndc.getDocumentNumber());
        return credito;
    }

    /**
     * Annullamento della NDC: crediti cancellati, ritenute stornate di nuovo 'da_versare'
     * (senza F24: le riprende generaF24/ricalcola), ritenute versate scollegate dalla NDC.
     */
    public void ripristinaDaNdc(Integer ndcId) {
        for (WithholdingLedger w : withholdingLedgerDAO.findByNdcId(ndcId)) {
            if (STATO_CREDITO_IMPOSTA.equals(w.getStato())) {
                withholdingLedgerDAO.deleteById(w.getId());
            } else if (STATO_STORNATA.equals(w.getStato())) {
                withholdingLedgerDAO.ripristinaDaNdc(w.getId(), STATO_DA_VERSARE);
            } else {
                withholdingLedgerDAO.ripristinaDaNdc(w.getId(), w.getStato());
            }
        }
        log.info("WithholdingLedgerService.ripristinaDaNdc() - ndcId={}", ndcId);
    }

    /** Ritenute del periodo (tutti gli stati). */
    public List<WithholdingLedger> findByPeriodo(Integer tenantId, Integer anno, Integer mese) {
        return withholdingLedgerDAO.findByTenantAndPeriodo(tenantId, anno, mese);
    }

    /** Somma delle ritenute ancora da versare nel periodo. */
    public BigDecimal totalePeriodo(Integer tenantId, Integer anno, Integer mese) {
        return withholdingLedgerDAO.findByTenantAndPeriodo(tenantId, anno, mese).stream()
                .filter(w -> STATO_DA_VERSARE.equals(w.getStato()))
                .map(WithholdingLedger::getRitenutaAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Numero di ritenute ancora da versare nel periodo. */
    public long countDaVersarePeriodo(Integer tenantId, Integer anno, Integer mese) {
        return withholdingLedgerDAO.findByTenantAndPeriodo(tenantId, anno, mese).stream()
                .filter(w -> STATO_DA_VERSARE.equals(w.getStato()))
                .count();
    }

    /**
     * Ritenute del periodo arricchite con i dati di prenotazione, immobile, proprietario
     * e documento. Stessa proiezione del dettaglio F24, in una sola query.
     */
    public List<WithholdingLedgerDTO> findDettaglioByPeriodo(Integer tenantId, Integer anno, Integer mese) {
        List<WithholdingLedgerDTO> righe = withholdingLedgerDAO.findRighePeriodo(tenantId, anno, mese);
        log.debug("WithholdingLedgerService.findDettaglioByPeriodo() - periodo={}/{} righe={}",
                mese, anno, righe.size());
        return righe;
    }

    /**
     * Ritenute collegate a un F24, arricchite per la UI con i dati della prenotazione
     * (ospite, immobile, date) e del documento.
     *
     * <p>Una sola query con JOIN invece dell'enrichment riga per riga di {@link #toDTO}:
     * quello richiamava {@code BookingService.findById()} per ogni ritenuta, che ricalcola
     * l'intero split economico dal contratto e carica documenti e lookup.
     */
    public List<WithholdingLedgerDTO> findDettaglioByF24Record(Integer tenantId, Integer f24RecordId) {
        List<WithholdingLedgerDTO> righe = withholdingLedgerDAO.findRigheF24(f24RecordId, tenantId);
        log.debug("WithholdingLedgerService.findDettaglioByF24Record() - f24RecordId={} righe={}",
                f24RecordId, righe.size());
        return righe;
    }

}

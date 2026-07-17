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
     * Ritenute del periodo arricchite con i dati di owner/booking/documento per la UI.
     * L'enrichment resta nel service (i controller non accedono ai DAO).
     */
    public List<WithholdingLedgerDTO> findDettaglioByPeriodo(Integer tenantId, Integer anno, Integer mese) {
        return withholdingLedgerDAO.findByTenantAndPeriodo(tenantId, anno, mese).stream()
                .map(w -> toDTO(tenantId, w))
                .toList();
    }

    /** Ritenute collegate a un F24, arricchite per la UI. */
    public List<WithholdingLedgerDTO> findDettaglioByF24Record(Integer tenantId, Integer f24RecordId) {
        return withholdingLedgerDAO.findByF24Record(f24RecordId).stream()
                .map(w -> toDTO(tenantId, w))
                .toList();
    }

    private WithholdingLedgerDTO toDTO(Integer tenantId, WithholdingLedger w) {
        String ownerName = null;
        String bookingExternalId = null;
        BookingDetailDTO booking = bookingService.findById(tenantId, w.getFkBookingId()).orElse(null);
        if (booking != null) {
            ownerName = booking.getOwnerName();
            bookingExternalId = booking.getExternalBookingId();
        }
        String documentNumber = fiscalDocumentDAO.findById(w.getFkFiscalDocumentId())
                .map(FiscalDocument::getDocumentNumber)
                .orElse(null);

        return WithholdingLedgerDTO.builder()
                .id(w.getId())
                .ownerName(ownerName)
                .bookingExternalId(bookingExternalId)
                .documentNumber(documentNumber)
                .dataEvento(w.getDataEvento())
                .periodoMese(w.getPeriodoMese())
                .periodoAnno(w.getPeriodoAnno())
                .canoneLocazione(w.getCanoneLocazione())
                .aliquotaRitenuta(w.getAliquotaRitenuta())
                .ritenutaAmount(w.getRitenutaAmount())
                .stato(w.getStato())
                .fkF24RecordId(w.getFkF24RecordId())
                .build();
    }
}

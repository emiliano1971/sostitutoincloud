package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dto.booking.BookingDetailDTO;
import it.gavia.sostitutoincloud.dto.booking.SplitEconomicoDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateRequestDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateResponseDTO;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Log4j2
public class DocumentGenerationService {

    private static final String TIPO_RICEVUTA_OWNER = "ricevuta_owner";
    private static final String TIPO_FATTURA_PM = "fattura_pm";

    private static final BigDecimal IVA_22 = new BigDecimal("0.22");
    private static final BigDecimal RITENUTA_21 = new BigDecimal("0.21");
    private static final BigDecimal SOGLIA_BOLLO = new BigDecimal("77.47");
    private static final BigDecimal IMPORTO_BOLLO = new BigDecimal("2.00");
    private static final String STATO_DRAFT = "draft";

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final BookingService bookingService;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final AuditService auditService;

    public DocumentGenerationService(FiscalDocumentDAO fiscalDocumentDAO,
                                     BookingService bookingService,
                                     TipoDocumentoDAO tipoDocumentoDAO,
                                     StatoDocumentoDAO statoDocumentoDAO,
                                     AuditService auditService) {
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.bookingService = bookingService;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.auditService = auditService;
    }

    public DocumentGenerateResponseDTO generate(Integer tenantId, DocumentGenerateRequestDTO request) {
        if (request.getBookingId() == null) {
            throw new IllegalArgumentException("bookingId obbligatorio");
        }
        String tipo = request.getTipoDocumento();
        if (!TIPO_RICEVUTA_OWNER.equals(tipo) && !TIPO_FATTURA_PM.equals(tipo)) {
            throw new IllegalArgumentException("tipoDocumento non valido: " + tipo);
        }

        // 1. Verifica esistenza booking e appartenenza al tenant (BookingService filtra per tenant)
        BookingDetailDTO booking = bookingService.findById(tenantId, request.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Prenotazione non trovata per questo tenant: id=" + request.getBookingId()));

        // Risoluzione lookup tipo_documento: il request usa termini di dominio (ricevuta_owner/fattura_pm)
        // mentre la tabella lookup ha i codici 'ricevuta'/'fattura'.
        String tipoLookupCodice = TIPO_RICEVUTA_OWNER.equals(tipo) ? "ricevuta" : "fattura";
        String prefix = TIPO_RICEVUTA_OWNER.equals(tipo) ? "RIC" : "FT";
        TipoDocumento tipoDocumento = tipoDocumentoDAO.findByCodice(tipoLookupCodice)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tipo documento lookup non trovato: " + tipoLookupCodice));
        StatoDocumento statoDraft = statoDocumentoDAO.findByCodice(STATO_DRAFT)
                .orElseThrow(() -> new IllegalArgumentException("Stato documento 'draft' non trovato"));

        // 2. Verifica che non esista già un documento per questo booking e tipo
        boolean giaEmesso = fiscalDocumentDAO.findByBookingId(request.getBookingId()).stream()
                .anyMatch(d -> tipoDocumento.getId().equals(d.getFkTipoDocumentoId()));
        if (giaEmesso) {
            throw new IllegalArgumentException("Documento già emesso per questa prenotazione");
        }

        // 3. Calcolo importi
        SplitEconomicoDTO split = booking.getSplitEconomico();
        BigDecimal gross = nz(split.getGrossAmount());
        BigDecimal otaCommission = nz(split.getOtaCommissionAmount());
        BigDecimal cleaning = nz(split.getCleaningAmount());
        BigDecimal pmFee = nz(split.getPmFeeAmount());

        BigDecimal imponibile;
        BigDecimal iva;
        BigDecimal ritenuta;
        BigDecimal bollo;
        BigDecimal importoTotale;

        if (TIPO_RICEVUTA_OWNER.equals(tipo)) {
            BigDecimal canone = gross.subtract(otaCommission).subtract(cleaning).subtract(pmFee)
                    .setScale(2, RoundingMode.HALF_UP);
            bollo = canone.compareTo(SOGLIA_BOLLO) > 0 ? IMPORTO_BOLLO : BigDecimal.ZERO.setScale(2);
            importoTotale = canone.add(bollo).setScale(2, RoundingMode.HALF_UP);
            ritenuta = canone.multiply(RITENUTA_21).setScale(2, RoundingMode.HALF_UP);
            imponibile = canone;
            iva = BigDecimal.ZERO.setScale(2);
        } else { // fattura_pm
            imponibile = otaCommission.add(cleaning).add(pmFee).setScale(2, RoundingMode.HALF_UP);
            iva = imponibile.multiply(IVA_22).setScale(2, RoundingMode.HALF_UP);
            importoTotale = imponibile.add(iva).setScale(2, RoundingMode.HALF_UP);
            ritenuta = nz(split.getWithholdingAmount());
            bollo = BigDecimal.ZERO.setScale(2);
        }

        // 4. Numero documento progressivo
        LocalDate dataEmissione = request.getDataEmissione() != null ? request.getDataEmissione() : LocalDate.now();
        Integer anno = dataEmissione.getYear();
        String documentNumber = fiscalDocumentDAO.generateDocumentNumber(
                tenantId, tipoDocumento.getId(), prefix, anno);

        // 5. Crea e salva FiscalDocument.
        // Le colonne imponibile/ritenuta_amount/bollo_amount/iva_amount sono ora persistite
        // (aggiunte con ALTER TABLE). vat_amount resta allineato a iva per retrocompatibilità.
        FiscalDocument doc = FiscalDocument.builder()
                .fkTenantId(tenantId)
                .fkBookingId(booking.getId())
                .fkTipoDocumentoId(tipoDocumento.getId())
                .fkStatoDocumentoId(statoDraft.getId())
                .documentNumber(documentNumber)
                .issueDate(dataEmissione)
                .recipientName(booking.getGuestName())
                .recipientTaxCode(booking.getGuestTaxCode())
                .totalAmount(importoTotale)
                .vatAmount(iva)
                .imponibile(imponibile)
                .ritenutaAmount(ritenuta)
                .bolloAmount(bollo)
                .ivaAmount(iva)
                .build();
        FiscalDocument saved = fiscalDocumentDAO.insert(doc);

        // 6. NON aggiorna ancora lo stato booking (cambierà alla conferma/invio — futuro)

        log.info("DocumentGenerationService.generate() - tenantId={} booking={} tipo={} number={}",
                tenantId, booking.getId(), tipo, documentNumber);
        auditService.log("document.issue", "FiscalDocument", saved.getId(),
                "Emesso documento " + saved.getDocumentNumber()
                        + " per prenotazione " + booking.getExternalBookingId());

        // 7. Response
        return DocumentGenerateResponseDTO.builder()
                .documentId(saved.getId())
                .documentNumber(saved.getDocumentNumber())
                .tipoDocumento(tipo)
                .dataEmissione(dataEmissione)
                .importoTotale(importoTotale)
                .importoBollo(bollo)
                .imponibile(imponibile)
                .iva(iva)
                .ritenuta(ritenuta)
                .statoDocumento(STATO_DRAFT)
                .bookingExternalId(booking.getExternalBookingId())
                .guestName(booking.getGuestName())
                .ownerName(booking.getOwnerName())
                .propertyName(booking.getPropertyName())
                .build();
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

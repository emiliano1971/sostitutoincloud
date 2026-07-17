package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dto.booking.BookingDetailDTO;
import it.gavia.sostitutoincloud.dto.booking.SplitEconomicoDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateRequestDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateResponseDTO;
import it.gavia.sostitutoincloud.dto.settings.TenantSettingsDTO;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Log4j2
public class DocumentGenerationService {

    private static final String TIPO_RICEVUTA_OWNER = "ricevuta_owner";
    private static final String TIPO_FATTURA_PM = "fattura_pm";

    // Moltiplicatore IVA ordinaria e aliquota IVA % di default (regime ordinario RF01).
    // Soglia/importo bollo e aliquota ritenuta sono ora configurabili nei tenant_settings.
    private static final BigDecimal ALIQUOTA_IVA_22 = new BigDecimal("22.00");
    // Divisore per lo scorporo dell'IVA dal lordo: imponibile = lordo / 1.22
    private static final BigDecimal DIVISORE_IVA_22 = new BigDecimal("1.22");
    private static final BigDecimal CENTO = new BigDecimal("100");
    private static final String REGIME_FORFETTARIO = "RF19";
    private static final String STATO_READY = "ready";
    private static final String STATO_DOC_ISSUED = "doc_issued";

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final BookingService bookingService;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final AuditService auditService;
    private final TenantSettingsService tenantSettingsService;
    private final PropertyDAO propertyDAO;
    private final WithholdingLedgerService withholdingLedgerService;
    private final BookingDAO bookingDAO;
    private final StatoPrenotazioneDAO statoPrenotazioneDAO;

    public DocumentGenerationService(FiscalDocumentDAO fiscalDocumentDAO,
                                     BookingService bookingService,
                                     TipoDocumentoDAO tipoDocumentoDAO,
                                     StatoDocumentoDAO statoDocumentoDAO,
                                     AuditService auditService,
                                     TenantSettingsService tenantSettingsService,
                                     PropertyDAO propertyDAO,
                                     WithholdingLedgerService withholdingLedgerService,
                                     BookingDAO bookingDAO,
                                     StatoPrenotazioneDAO statoPrenotazioneDAO) {
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.bookingService = bookingService;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.auditService = auditService;
        this.tenantSettingsService = tenantSettingsService;
        this.propertyDAO = propertyDAO;
        this.withholdingLedgerService = withholdingLedgerService;
        this.bookingDAO = bookingDAO;
        this.statoPrenotazioneDAO = statoPrenotazioneDAO;
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
        // mentre la tabella lookup ha i codici 'ricevuta'/'fattura'. Servono entrambi per collegare i documenti.
        TipoDocumento tipoFattura = tipoDocumentoDAO.findByCodice("fattura")
                .orElseThrow(() -> new IllegalArgumentException("Tipo documento lookup non trovato: fattura"));
        TipoDocumento tipoRicevuta = tipoDocumentoDAO.findByCodice("ricevuta")
                .orElseThrow(() -> new IllegalArgumentException("Tipo documento lookup non trovato: ricevuta"));
        TipoDocumento tipoDocumento = TIPO_RICEVUTA_OWNER.equals(tipo) ? tipoRicevuta : tipoFattura;
        String prefix = TIPO_RICEVUTA_OWNER.equals(tipo) ? "RIC" : "FT";
        StatoDocumento statoIniziale = statoDocumentoDAO.findByCodice(STATO_READY)
                .orElseThrow(() -> new IllegalArgumentException("Stato documento 'ready' non trovato"));

        // 2. Verifica che non esista già un documento per questo booking e tipo
        boolean giaEmesso = fiscalDocumentDAO.findByBookingId(request.getBookingId()).stream()
                .anyMatch(d -> tipoDocumento.getId().equals(d.getFkTipoDocumentoId()));
        if (giaEmesso) {
            throw new IllegalArgumentException("Documento già emesso per questa prenotazione");
        }

        // 3. Calcolo importi — parametri fiscali configurabili dai settings del tenant
        TenantSettingsDTO settings = tenantSettingsService.getSettings(tenantId);
        BigDecimal bolloSoglia = settings.getBolloSoglia();
        BigDecimal bolloImporto = settings.getBolloImporto();
        // Aliquota ritenuta in base al primo/secondo immobile dell'owner:
        // primo immobile → ritenuta primaria (es. 21%), dal secondo → ritenuta secondaria (es. 26%).
        Property property = propertyDAO.findById(booking.getFkPropertyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Immobile non trovato per la prenotazione: id=" + booking.getFkPropertyId()));
        boolean primoImmobile = Boolean.TRUE.equals(property.getPrimoImmobile());
        BigDecimal aliquotaRitenuta = primoImmobile
                ? settings.getWithholdingRatePrimary().divide(CENTO, 4, RoundingMode.HALF_UP)
                : settings.getWithholdingRateSecondary().divide(CENTO, 4, RoundingMode.HALF_UP);
        boolean forfettario = REGIME_FORFETTARIO.equals(settings.getRegimeFiscalePm());
        boolean bolloAddebitato = Boolean.TRUE.equals(settings.getBolloAddebitatoCliente());

        SplitEconomicoDTO split = booking.getSplitEconomico();
        BigDecimal gross = nz(split.getGrossAmount());
        BigDecimal otaCommission = nz(split.getOtaCommissionAmount());
        BigDecimal cleaning = nz(split.getCleaningAmount());
        BigDecimal pmFee = nz(split.getPmFeeAmount());

        BigDecimal imponibile;
        BigDecimal iva;
        BigDecimal aliquotaIva;
        BigDecimal ritenuta;
        BigDecimal bollo;
        BigDecimal importoTotale;
        BigDecimal canoneLocazione;
        Integer fkDocumentoCollegatoId;

        if (TIPO_RICEVUTA_OWNER.equals(tipo)) {
            // La ricevuta owner è di norma emessa DOPO la fattura PM.
            // Canone = lordo ospite - totale fattura PM (riaddebiti + provvigione + IVA).
            // Se la fattura PM non esiste ancora (ricevuta emessa prima): fallback su owner_net_amount.
            Optional<FiscalDocument> fatturaPM = fiscalDocumentDAO.findByBookingId(request.getBookingId()).stream()
                    .filter(d -> tipoFattura.getId().equals(d.getFkTipoDocumentoId()))
                    .findFirst();
            BigDecimal canone = fatturaPM
                    .map(f -> gross.subtract(nz(f.getTotalAmount())))
                    .orElseGet(() -> split.getOwnerNetAmount() != null
                            ? split.getOwnerNetAmount()
                            : gross.subtract(otaCommission).subtract(cleaning).subtract(pmFee))
                    .setScale(2, RoundingMode.HALF_UP);
            bollo = canone.compareTo(bolloSoglia) > 0 ? bolloImporto : BigDecimal.ZERO.setScale(2);
            // Se il bollo non è addebitato all'ospite resta salvato (tracciabilità) ma non somma al totale.
            importoTotale = (bolloAddebitato ? canone.add(bollo) : canone).setScale(2, RoundingMode.HALF_UP);
            ritenuta = canone.multiply(aliquotaRitenuta).setScale(2, RoundingMode.HALF_UP);
            imponibile = canone;
            iva = BigDecimal.ZERO.setScale(2);
            aliquotaIva = BigDecimal.ZERO.setScale(2);
            canoneLocazione = canone;
            fkDocumentoCollegatoId = fatturaPM.map(FiscalDocument::getId).orElse(null);
        } else { // fattura_pm
            // I valori dei servizi (OTA, pulizie, commissione PM) sono GIÀ LORDI, IVA inclusa.
            // L'IVA va SCORPORATA dal lordo (lordo / 1.22), non aggiunta sopra.
            // Il totale della fattura coincide con il lordo dei servizi.
            BigDecimal lordoServizi = otaCommission.add(cleaning).add(pmFee).setScale(2, RoundingMode.HALF_UP);
            if (forfettario) {
                // Regime forfettario (RF19): nessuno scorporo IVA — imponibile = lordo, IVA = 0.
                imponibile = lordoServizi;
                iva = BigDecimal.ZERO.setScale(2);
                aliquotaIva = BigDecimal.ZERO.setScale(2);
            } else {
                // Regime ordinario (RF01): scorporo IVA dal lordo.
                imponibile = lordoServizi.divide(DIVISORE_IVA_22, 2, RoundingMode.HALF_UP);
                iva = lordoServizi.subtract(imponibile).setScale(2, RoundingMode.HALF_UP);
                aliquotaIva = ALIQUOTA_IVA_22;
            }
            // Totale = lordo servizi (NON imponibile + IVA calcolata sopra).
            importoTotale = lordoServizi;
            ritenuta = nz(split.getWithholdingAmount());
            bollo = BigDecimal.ZERO.setScale(2);
            canoneLocazione = null;
            fkDocumentoCollegatoId = null;
        }

        // 4. Numero documento progressivo
        LocalDate dataEmissione = request.getDataEmissione() != null ? request.getDataEmissione() : LocalDate.now();
        Integer anno = dataEmissione.getYear();
        String documentNumber = fiscalDocumentDAO.generateDocumentNumber(
                tenantId, tipoDocumento.getId(), prefix, anno);

        // 5. Crea e salva FiscalDocument.
        // Le colonne imponibile/ritenuta_amount/bollo_amount sono persistite.
        // vat_amount contiene l'IVA del documento (0 per ricevute fuori campo IVA).
        FiscalDocument doc = FiscalDocument.builder()
                .fkTenantId(tenantId)
                .fkBookingId(booking.getId())
                // Proprietario denormalizzato: risalito da booking → property (property già caricata sopra).
                .fkOwnerId(property.getFkOwnerId())
                .fkTipoDocumentoId(tipoDocumento.getId())
                .fkStatoDocumentoId(statoIniziale.getId())
                .documentNumber(documentNumber)
                .issueDate(dataEmissione)
                .recipientName(booking.getGuestName())
                .recipientTaxCode(booking.getGuestTaxCode())
                .totalAmount(importoTotale)
                .vatAmount(iva)
                .aliquotaIva(aliquotaIva)
                .imponibile(imponibile)
                .ritenutaAmount(ritenuta)
                .bolloAmount(bollo)
                .canoneLocazione(canoneLocazione)
                .fkDocumentoCollegatoId(fkDocumentoCollegatoId)
                .build();
        FiscalDocument saved = fiscalDocumentDAO.insert(doc);

        // 6. Collegamento ricevuta <-> fattura PM.
        // Se sto emettendo la fattura e la ricevuta esiste già (ricevuta emessa prima della fattura),
        // aggiorno il riferimento della ricevuta con l'id della fattura appena creata.
        if (TIPO_FATTURA_PM.equals(tipo)) {
            fiscalDocumentDAO.findByBookingId(request.getBookingId()).stream()
                    .filter(d -> tipoRicevuta.getId().equals(d.getFkTipoDocumentoId()))
                    .findFirst()
                    .ifPresent(ric -> fiscalDocumentDAO.updateDocumentoCollegato(ric.getId(), saved.getId()));
        }

        // NON aggiorna ancora lo stato booking (cambierà alla conferma/invio — futuro)

        // 6b. Registra la ritenuta nel ledger (solo per la ricevuta owner).
        // L'eventuale fallimento del ledger NON deve bloccare l'emissione del documento.
        if (TIPO_RICEVUTA_OWNER.equals(tipo)) {
            try {
                withholdingLedgerService.registraRitenuta(tenantId, booking.getId(), saved.getId());
            } catch (Exception e) {
                log.warn("Impossibile registrare ritenuta per documento={}: {}", saved.getId(), e.getMessage());
            }
        }

        // 6c. Se il booking ha ora ENTRAMBI i documenti (fattura PM + ricevuta owner),
        // avanza lo stato a 'doc_issued'. Un fallimento non deve bloccare l'emissione.
        try {
            List<FiscalDocument> docs = fiscalDocumentDAO.findByBookingId(booking.getId());
            boolean hasFattura = docs.stream().anyMatch(d -> tipoFattura.getId().equals(d.getFkTipoDocumentoId()));
            boolean hasRicevuta = docs.stream().anyMatch(d -> tipoRicevuta.getId().equals(d.getFkTipoDocumentoId()));
            if (hasFattura && hasRicevuta) {
                Integer statoDocIssuedId = statoPrenotazioneDAO.findByCodice(STATO_DOC_ISSUED)
                        .map(StatoPrenotazione::getId)
                        .orElseThrow(() -> new IllegalStateException("Stato prenotazione 'doc_issued' non trovato"));
                bookingDAO.updateStato(booking.getId(), statoDocIssuedId);
                log.info("DocumentGenerationService - booking {} → doc_issued", booking.getId());
            }
        } catch (Exception e) {
            log.warn("Impossibile aggiornare stato booking {} a doc_issued: {}", booking.getId(), e.getMessage());
        }

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
                .statoDocumento(STATO_READY)
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

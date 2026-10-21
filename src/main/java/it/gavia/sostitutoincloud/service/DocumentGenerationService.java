package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.BookingSplitEconomicoDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dto.batch.BatchRisultatoDTO;
import it.gavia.sostitutoincloud.dto.batch.GeneraDocumentiBatchDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingDetailDTO;
import it.gavia.sostitutoincloud.dto.booking.SplitEconomicoDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateRequestDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateResponseDTO;
import it.gavia.sostitutoincloud.dto.settings.TenantSettingsDTO;
import it.gavia.sostitutoincloud.model.BookingSplitEconomico;
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
import java.util.stream.Collectors;

@Service
@Log4j2
public class DocumentGenerationService {

    private static final String TIPO_RICEVUTA_OWNER = "ricevuta_owner";
    private static final String TIPO_FATTURA_PM = "fattura_pm";
    // Codici lookup tipo_documento / stato_prenotazione usati dalla generazione massiva
    private static final String CODICE_FATTURA = "fattura";
    private static final String CODICE_RICEVUTA = "ricevuta";
    private static final String STATO_CANCELLED = "cancelled";

    // Moltiplicatore IVA ordinaria e aliquota IVA % di default (regime ordinario RF01).
    // Soglia/importo bollo e aliquota ritenuta sono ora configurabili nei tenant_settings.
    private static final BigDecimal ALIQUOTA_IVA_22 = new BigDecimal("22.00");
    // Divisore per lo scorporo dell'IVA dal lordo: imponibile = lordo / 1.22
    private static final BigDecimal DIVISORE_IVA_22 = new BigDecimal("1.22");
    private static final BigDecimal CENTO = new BigDecimal("100");
    private static final String REGIME_FORFETTARIO = "RF19";
    private static final String STATO_READY = "ready";
    private static final String STATO_SENT_SDI = "sent_sdi";
    private static final String STATO_DOC_ISSUED = "doc_issued";
    private static final String STATO_STORNATA = "stornata";

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
    private final SdiXmlService sdiXmlService;
    private final BookingSplitEconomicoDAO splitEconomicoDAO;
    private final F24RecordDAO f24RecordDAO;

    public DocumentGenerationService(FiscalDocumentDAO fiscalDocumentDAO,
                                     BookingService bookingService,
                                     TipoDocumentoDAO tipoDocumentoDAO,
                                     StatoDocumentoDAO statoDocumentoDAO,
                                     AuditService auditService,
                                     TenantSettingsService tenantSettingsService,
                                     PropertyDAO propertyDAO,
                                     WithholdingLedgerService withholdingLedgerService,
                                     BookingDAO bookingDAO,
                                     StatoPrenotazioneDAO statoPrenotazioneDAO,
                                     SdiXmlService sdiXmlService,
                                     BookingSplitEconomicoDAO splitEconomicoDAO,
                                     F24RecordDAO f24RecordDAO) {
        this.f24RecordDAO = f24RecordDAO;
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
        this.sdiXmlService = sdiXmlService;
        this.splitEconomicoDAO = splitEconomicoDAO;
    }

    /**
     * Ricevuta emessa in un periodo il cui F24 principale è già pagato: la ritenuta andrà in un
     * F24 integrativo. Non blocca l'emissione, si segnala solo. null se nessun avviso.
     */
    private String avvisoF24Pagato(Integer tenantId, LocalDate dataEmissione) {
        if (dataEmissione == null) return null;
        int mese = dataEmissione.getMonthValue();
        int anno = dataEmissione.getYear();
        boolean pagato = f24RecordDAO.findPrincipaleByPeriodo(tenantId, anno, mese)
                .map(f -> "paid".equals(f.getStato()))
                .orElse(false);
        if (!pagato) return null;
        log.info("DocumentGenerationService - ricevuta nel periodo {}/{} con F24 già pagato: avviso integrativo",
                mese, anno);
        return "Attenzione: l'F24 del periodo " + String.format("%02d/%d", mese, anno)
                + " è già stato pagato. La ritenuta sarà inclusa in un F24 integrativo.";
    }

    /**
     * Generazione massiva: per ogni prenotazione emette i documenti mancanti (prima la fattura
     * PM, poi la ricevuta owner) riusando generate(). Scartate le prenotazioni non trovate,
     * stornate/cancellate o con entrambi i documenti già emessi (il controllo sul check-out passato
     * è disabilitato, vedi il commento nel ciclo).
     * Se la fattura fallisce la ricevuta non viene emessa (è calcolata sul netto dopo la fattura).
     * Ogni prenotazione è indipendente: un errore non blocca le successive.
     */
    public BatchRisultatoDTO generaBatch(Integer tenantId, GeneraDocumentiBatchDTO dto) {
        if (dto.getBookingIds() == null || dto.getBookingIds().isEmpty()) {
            throw new IllegalArgumentException("Nessuna prenotazione selezionata");
        }
        LocalDate dataEmissione = dto.getDataEmissione() != null ? dto.getDataEmissione() : LocalDate.now();
        LocalDate oggi = LocalDate.now();   // usato dal controllo check-out (disabilitato)
        BatchRisultatoDTO risultato = new BatchRisultatoDTO();

        for (Integer bookingId : dto.getBookingIds()) {
            BookingDetailDTO booking = bookingService.findById(tenantId, bookingId).orElse(null);
            if (booking == null) {
                risultato.scarta("#" + bookingId, "prenotazione non trovata");
                continue;
            }
            String codice = booking.getExternalBookingId() != null ? booking.getExternalBookingId() : "#" + bookingId;
            if (STATO_STORNATA.equals(booking.getStatoPrenotazione()) || STATO_CANCELLED.equals(booking.getStatoPrenotazione())) {
                risultato.scarta(codice, "prenotazione " + booking.getStatoPrenotazione());
                continue;
            }
            // Controllo check-out passato disabilitato: il singolo documento (generate()) non ha
            // questo vincolo e il comportamento deve essere coerente.
            // Per ripristinarlo decommentare il blocco seguente:
            //
            // if (booking.getCheckoutDate() == null || !booking.getCheckoutDate().isBefore(oggi)) {
            //     risultato.scarta(codice, "check-out non ancora passato");
            //     continue;
            // }
            boolean haFattura = haDocumentoAttivo(booking, CODICE_FATTURA);
            boolean haRicevuta = haDocumentoAttivo(booking, CODICE_RICEVUTA);
            if (haFattura && haRicevuta) {
                risultato.scarta(codice, "documenti già emessi");
                continue;
            }
            try {
                if (!haFattura) {
                    generate(tenantId, new DocumentGenerateRequestDTO(bookingId, TIPO_FATTURA_PM, dataEmissione));
                }
            } catch (Exception e) {
                log.warn("DocumentGenerationService.generaBatch() - booking {}: fattura non emessa: {}", codice, e.getMessage());
                risultato.scarta(codice, "fattura PM non emessa — " + e.getMessage());
                continue;
            }
            try {
                if (!haRicevuta) {
                    generate(tenantId, new DocumentGenerateRequestDTO(bookingId, TIPO_RICEVUTA_OWNER, dataEmissione));
                }
                risultato.elaborato();
            } catch (Exception e) {
                log.warn("DocumentGenerationService.generaBatch() - booking {}: ricevuta non emessa: {}", codice, e.getMessage());
                risultato.scarta(codice, "ricevuta owner non emessa — " + e.getMessage());
            }
        }
        log.info("DocumentGenerationService.generaBatch() - tenantId={} richiesti={} elaborati={} scartati={}",
                tenantId, dto.getBookingIds().size(), risultato.getElaborati(), risultato.getScartati());
        return risultato;
    }

    /** Documento del tipo indicato (codice lookup) presente e non annullato. */
    private boolean haDocumentoAttivo(BookingDetailDTO booking, String codiceTipo) {
        return booking.getDocumenti() != null && booking.getDocumenti().stream()
                .anyMatch(d -> codiceTipo.equals(d.getTipoDocumento()) && !"annullata".equals(d.getStatoDocumento()));
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

        // Booking stornato con NDC totale (migration 025/026): nessun nuovo documento, né
        // fattura PM né ricevuta owner. Per riemetterli si copia la prenotazione
        // (BookingService.copiaBooking) e si emettono i documenti sulla copia.
        if (STATO_STORNATA.equals(booking.getStatoPrenotazione())) {
            throw new IllegalStateException("Impossibile emettere documenti: la prenotazione è stata stornata");
        }

        // Blocco fattura PM senza CF ospite (la ricevuta owner resta emettibile senza CF).
        if (TIPO_FATTURA_PM.equals(tipo)
                && (booking.getGuestTaxCode() == null || booking.getGuestTaxCode().isBlank())) {
            throw new IllegalStateException(
                    "CF ospite mancante — inserire il codice fiscale prima di emettere la fattura PM");
        }

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
            Optional<FiscalDocument> fatturaPM = fiscalDocumentDAO.findByBookingId(request.getBookingId()).stream()
                    .filter(d -> tipoFattura.getId().equals(d.getFkTipoDocumentoId()))
                    .findFirst();
            // Il canone è sempre il netto proprietario già calcolato nello split: tiene conto
            // dello scorporo della tassa di soggiorno quando è inclusa nel lordo.
            // Ricavarlo da "lordo - totale fattura PM" lo gonfiava dell'importo della tassa,
            // e faceva dipendere il risultato dall'ordine di emissione ricevuta/fattura.
            BigDecimal canone;
            if (split.getOwnerNetAmount() != null) {
                canone = split.getOwnerNetAmount();
            } else {
                // Fallback per i booking senza netto calcolato: lordo meno i servizi PM.
                canone = fatturaPM
                        .map(f -> gross.subtract(nz(f.getTotalAmount())))
                        .orElseGet(() -> gross.subtract(otaCommission).subtract(cleaning).subtract(pmFee));
            }
            canone = canone.setScale(2, RoundingMode.HALF_UP);
            // Bollo addebitato al proprietario (voce 'bollo' nello split): la decisione è dello
            // split, anche se il canone dopo il bollo scende sotto soglia. Senza voce si applica
            // la regola della soglia sul canone.
            BigDecimal bolloSplit = splitEconomicoDAO.findByBookingId(request.getBookingId()).stream()
                    .filter(r -> BookingService.TIPO_VOCE_BOLLO.equals(r.getTipoVoce()))
                    .map(BookingSplitEconomico::getImporto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            bollo = bolloSplit.signum() > 0
                    ? bolloSplit.setScale(2, RoundingMode.HALF_UP)
                    : canone.compareTo(bolloSoglia) > 0 ? bolloImporto : BigDecimal.ZERO.setScale(2);
            // Il bollo resta salvato per tracciabilità e compare nel dettaglio come voce
            // informativa, ma NON concorre al total_amount: non fa parte del compenso del
            // proprietario. Coerente con DocumentPdfService (netto = canone - ritenuta, bollo
            // non scalato) e con SettlementService.
            // NB: tenant_settings.bollo_addebitato_cliente non incide più su questo calcolo.
            importoTotale = canone.setScale(2, RoundingMode.HALF_UP);
            ritenuta = canone.multiply(aliquotaRitenuta).setScale(2, RoundingMode.HALF_UP);
            imponibile = canone;
            iva = BigDecimal.ZERO.setScale(2);
            aliquotaIva = BigDecimal.ZERO.setScale(2);
            canoneLocazione = canone;
            fkDocumentoCollegatoId = fatturaPM.map(FiscalDocument::getId).orElse(null);
        } else { // fattura_pm
            // Voci lette da booking_split_economico: solo quelle in fattura PM (la tassa di
            // soggiorno ha una riga ma include_in_fattura_pm=false). Modello IVA (migration 020):
            // ogni riga ha imponibile (netto) e importo (lordo = imponibile × (1 + IVA)).
            // Totale fattura = Σ lordi; imponibile = Σ imponibili delle righe, non Σ lordi / 1.22,
            // che può scostarsi di qualche centesimo dai netti mostrati nello split.
            List<BookingSplitEconomico> righe = splitEconomicoDAO.findByBookingId(booking.getId());
            List<BookingSplitEconomico> righeInFattura = righe.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIncludeInFatturaPm()))
                    .collect(Collectors.toList());
            BigDecimal lordoServizi = righeInFattura.stream()
                    .map(BookingSplitEconomico::getImporto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            // Nessuna riga split (booking pre-migrazione 018) → campi flat dello split.
            if (lordoServizi.compareTo(BigDecimal.ZERO) == 0 && righe.isEmpty()) {
                lordoServizi = otaCommission.add(cleaning).add(pmFee).setScale(2, RoundingMode.HALF_UP);
                log.warn("DocumentGenerationService - bookingId={} nessuna riga split trovata, uso campi flat",
                        booking.getId());
            }
            // Senza servizi non c'è nulla da fatturare: una fattura a zero non ha senso
            // fiscale e nasconderebbe il vero problema a monte — regole contratto assenti
            // sull'immobile, oppure commissione OTA non mappata in fase di import.
            if (lordoServizi.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalStateException(
                        "Impossibile emettere la fattura PM: nessun servizio PM calcolato "
                        + "(commissione OTA + pulizie + provvigione = 0). "
                        + "Verificare le regole contratto dell'immobile oppure la commissione "
                        + "OTA nel file di import.");
            }
            // Voci fuori campo IVA art. 15 (rimborso bollo): nel totale, fuori da imponibile e IVA
            BigDecimal fuoriCampo = righeInFattura.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getFuoriCampo()))
                    .map(BookingSplitEconomico::getImporto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal lordoSoggetto = lordoServizi.subtract(fuoriCampo);
            if (forfettario) {
                // Regime forfettario (RF19): nessuno scorporo IVA — imponibile = lordo, IVA = 0.
                imponibile = lordoSoggetto;
                iva = BigDecimal.ZERO.setScale(2);
                aliquotaIva = BigDecimal.ZERO.setScale(2);
            } else if (!righeInFattura.isEmpty()) {
                // Regime ordinario (RF01): Σ imponibili delle righe. Le righe create prima
                // della migration 020 non hanno imponibile: per quelle si scorpora il lordo.
                imponibile = righeInFattura.stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getFuoriCampo()))
                        .map(r -> r.getImponibile() != null
                                ? r.getImponibile()
                                : r.getImporto().divide(DIVISORE_IVA_22, 2, RoundingMode.HALF_UP))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
                iva = lordoSoggetto.subtract(imponibile).setScale(2, RoundingMode.HALF_UP);
                aliquotaIva = ALIQUOTA_IVA_22;
            } else {
                // Regime ordinario senza righe split (campi flat pre-018): scorporo IVA dal lordo.
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
        DocumentGenerateResponseDTO result = DocumentGenerateResponseDTO.builder()
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
                .avviso(TIPO_RICEVUTA_OWNER.equals(tipo) ? avvisoF24Pagato(tenantId, dataEmissione) : null)
                .build();

        // 8. Auto-invio SDI se configurato (tenant_settings.sdi_auto_send).
        // Solo per la fattura PM: la ricevuta owner è un documento interno e
        // SdiXmlService.generaEInvia() la rifiuta.
        // sdi_auto_send arriva dagli stessi settings già usati per bollo e ritenute:
        // per un tenant senza riga in tenant_settings valgono i default, dove è true.
        if (TIPO_FATTURA_PM.equals(tipo) && Boolean.TRUE.equals(settings.getSdiAutoSend())) {
            // Dati obbligatori ospite. Il CF è già garantito dal controllo in testa al metodo
            // (fattura PM senza CF non arriva qui): in pratica solo il nome può mancare.
            boolean datiCompleti = booking.getGuestTaxCode() != null
                    && !booking.getGuestTaxCode().isBlank()
                    && booking.getGuestName() != null
                    && !booking.getGuestName().isBlank();

            if (datiCompleti) {
                try {
                    String filePath = sdiXmlService.generaEInvia(tenantId, saved.getId());
                    log.info("DocumentGenerationService: SDI auto-send OK per doc={} file={}",
                            saved.getId(), filePath);
                    // Segnala al chiamante che lo SDI è stato generato automaticamente
                    result.setSdiAutoGenerato(true);
                    result.setSdiFilePath(filePath);
                    result.setSdiProgressivo(fiscalDocumentDAO.findById(saved.getId())
                            .map(FiscalDocument::getSdiProgressivo)
                            .orElse(null));
                    // generaEInvia() porta il documento a 'sent_sdi': senza questo la response
                    // continuerebbe a dichiarare 'ready' e il dialog mostrerebbe lo stato sbagliato.
                    result.setStatoDocumento(STATO_SENT_SDI);
                } catch (Exception e) {
                    // L'emissione resta valida: l'invio si potrà ritentare manualmente.
                    log.error("DocumentGenerationService: SDI auto-send fallito per doc={}: {}",
                            saved.getId(), e.getMessage(), e);
                    result.setSdiAutoGenerato(false);
                    result.setSdiAutoSendError(e.getMessage());
                }
            } else {
                log.warn("DocumentGenerationService: sdi_auto_send=true ma dati ospite incompleti "
                        + "per doc={} booking={}", saved.getId(), booking.getId());
                result.setSdiAutoGenerato(false);
                result.setSdiDatiIncompleti(true);
            }
        }

        return result;
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

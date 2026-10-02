package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.BookingSplitEconomicoDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentRigaNdcDAO;
import it.gavia.sostitutoincloud.dao.SettlementBookingDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dao.TenantSettingsDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.fiscal.EmettNdcDTO;
import it.gavia.sostitutoincloud.model.BookingSplitEconomico;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.FiscalDocumentRigaNdc;
import it.gavia.sostitutoincloud.model.Settlement;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import it.gavia.sostitutoincloud.model.TenantSettings;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.model.WithholdingLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Note di credito (NDC, tipo_documento 'nota_credito', SDI TD04): storno SEMPRE TOTALE di una
 * fattura PM (SETUP-NDC-TOTALE.md). Il booking stornato si rifà con BookingService.copiaBooking().
 *
 * <ul>
 *   <li>righe ricavate dalle voci split in fattura PM (le stesse di PDF e XML), non dal client;</li>
 *   <li>numero NC-YYYY-NNNN (progressivo per tenant + tipo + anno), importi del documento NEGATIVI,
 *       righe POSITIVE; XML TD04 e PDF con importi positivi;</li>
 *   <li>XML TD04 solo se la fattura è già stata trasmessa ('sent_sdi'/'accepted');</li>
 *   <li>booking → 'stornata'; ricevuta owner → 'annullata' (se non trasmessa);</li>
 *   <li>ritenuta: non ancora in F24 → 'stornata'; in F24 non pagato → 'stornata' e rimossa
 *       dall'F24; in F24 'paid'/'sent' → riga 'credito_imposta' (solo registrata);</li>
 *   <li>booking in un rendiconto 'pending'/'calculated': tolto dal rendiconto e totali
 *       ricalcolati; in un rendiconto 'approved'/'paid': NDC bloccata;</li>
 *   <li>annullamento (solo NDC non trasmessa) ripristina ricevuta, ledger e stato booking.</li>
 * </ul>
 */
@Service
@Transactional
@Log4j2
public class NdcService {

    private static final String CODICE_FATTURA = "fattura";
    private static final String CODICE_RICEVUTA = "ricevuta";
    private static final String CODICE_NOTA_CREDITO = "nota_credito";
    private static final String PREFISSO_NDC = "NC";
    private static final String STATO_READY = "ready";
    private static final String STATO_ANNULLATA = "annullata";
    private static final String STATO_BOOKING_STORNATA = "stornata";
    private static final String STATO_BOOKING_DOC_ISSUED = "doc_issued";
    private static final String STATO_BOOKING_READY = "ready";
    private static final String LEDGER_STORNATA = "stornata";
    private static final String LEDGER_CREDITO = "credito_imposta";
    /** Fatture non stornabili: scartate dallo SDI (fiscalmente mai emesse) o in errore. */
    private static final Set<String> STATI_FATTURA_NON_STORNABILI = Set.of("rejected", "error", "draft");
    /** Documento già preso in carico dallo SDI. */
    private static final Set<String> STATI_TRASMESSI = Set.of("sent_sdi", "accepted");
    /** F24 già versato o inviato: non si modifica più, la ritenuta diventa credito d'imposta. */
    private static final Set<String> STATI_F24_VERSATO = Set.of("paid", "sent");
    private static final BigDecimal CENTO = new BigDecimal("100");
    /** Rendiconti non più modificabili: la NDC è bloccata. */
    private static final Set<String> STATI_SETTLEMENT_BLOCCATI = Set.of("approved", "paid");

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final FiscalDocumentRigaNdcDAO rigaNdcDAO;
    private final BookingDAO bookingDAO;
    private final BookingSplitEconomicoDAO splitEconomicoDAO;
    private final TenantSettingsDAO tenantSettingsDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final StatoPrenotazioneDAO statoPrenotazioneDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final F24RecordDAO f24RecordDAO;
    private final SettlementBookingDAO settlementBookingDAO;
    private final SettlementDAO settlementDAO;
    private final SettlementService settlementService;
    private final WithholdingLedgerService withholdingLedgerService;
    private final F24Service f24Service;
    private final SdiXmlService sdiXmlService;
    private final AuditService auditService;

    public NdcService(FiscalDocumentDAO fiscalDocumentDAO,
                      FiscalDocumentRigaNdcDAO rigaNdcDAO,
                      BookingDAO bookingDAO,
                      BookingSplitEconomicoDAO splitEconomicoDAO,
                      TenantSettingsDAO tenantSettingsDAO,
                      TipoDocumentoDAO tipoDocumentoDAO,
                      StatoDocumentoDAO statoDocumentoDAO,
                      StatoPrenotazioneDAO statoPrenotazioneDAO,
                      WithholdingLedgerDAO withholdingLedgerDAO,
                      F24RecordDAO f24RecordDAO,
                      SettlementBookingDAO settlementBookingDAO,
                      SettlementDAO settlementDAO,
                      SettlementService settlementService,
                      WithholdingLedgerService withholdingLedgerService,
                      F24Service f24Service,
                      SdiXmlService sdiXmlService,
                      AuditService auditService) {
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.rigaNdcDAO = rigaNdcDAO;
        this.bookingDAO = bookingDAO;
        this.splitEconomicoDAO = splitEconomicoDAO;
        this.tenantSettingsDAO = tenantSettingsDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.statoPrenotazioneDAO = statoPrenotazioneDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.f24RecordDAO = f24RecordDAO;
        this.settlementBookingDAO = settlementBookingDAO;
        this.settlementDAO = settlementDAO;
        this.settlementService = settlementService;
        this.withholdingLedgerService = withholdingLedgerService;
        this.f24Service = f24Service;
        this.sdiXmlService = sdiXmlService;
        this.auditService = auditService;
    }

    /** Riga della NDC ricavata da una voce della fattura. */
    private record RigaCalcolata(Integer fkSplitEconomicoId, String descrizione,
                                 BigDecimal importo, BigDecimal imponibile) {
    }

    /**
     * Emette la nota di credito che storna l'intera fattura PM.
     *
     * @throws NoSuchElementException fattura inesistente o di altro tenant (404)
     * @throws IllegalStateException  fattura non stornabile, NDC già presente, booking in liquidazione (400)
     */
    public FiscalDocument emettiNdc(Integer tenantId, Integer utenteId, EmettNdcDTO dto) {
        if (dto == null || dto.getFkFiscalDocumentId() == null) {
            throw new IllegalArgumentException("fkFiscalDocumentId obbligatorio");
        }
        // 1. Fattura originale e vincoli
        FiscalDocument fattura = fiscalDocumentDAO.findById(dto.getFkFiscalDocumentId())
                .filter(d -> tenantId.equals(d.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException(
                        "Fattura non trovata: id=" + dto.getFkFiscalDocumentId()));
        TipoDocumento tipoNdc = tipo(CODICE_NOTA_CREDITO);
        if (!tipo(CODICE_FATTURA).getId().equals(fattura.getFkTipoDocumentoId())) {
            throw new IllegalStateException("La nota di credito si emette solo su una fattura PM");
        }
        String statoFattura = codiceStato(fattura.getFkStatoDocumentoId());
        if (STATI_FATTURA_NON_STORNABILI.contains(statoFattura)) {
            throw new IllegalStateException("Fattura in stato '" + statoFattura
                    + "' non stornabile: una fattura scartata dallo SDI va corretta e ritrasmessa");
        }
        if (!ndcAttive(fattura.getId(), tipoNdc).isEmpty()) {
            throw new IllegalStateException("Esiste già una nota di credito per questa fattura");
        }
        Integer bookingId = fattura.getFkBookingId();
        // Rendiconto approvato o pagato: blocco. Ancora modificabile ('pending'/'calculated'):
        // il booking esce dal rendiconto, che viene ricalcolato (o eliminato se resta vuoto).
        settlementBookingDAO.findSettlementIdByBookingId(bookingId).ifPresent(settlementId -> {
            Settlement settlement = settlementDAO.findById(settlementId)
                    .filter(s -> tenantId.equals(s.getFkTenantId()))
                    .orElseThrow(() -> new IllegalStateException("Rendiconto non trovato: id=" + settlementId));
            if (STATI_SETTLEMENT_BLOCCATI.contains(settlement.getStato())) {
                throw new IllegalStateException("Prenotazione già in rendiconto approvato o pagato: "
                        + "non è possibile emettere la nota di credito");
            }
            settlementBookingDAO.deleteByBookingId(bookingId);
            settlementService.ricalcolaTotali(settlementId, tenantId);
            log.info("NdcService - booking {} rimosso dal rendiconto {} (stato {})",
                    bookingId, settlementId, settlement.getStato());
        });

        // 2. Righe = voci della fattura (storno totale), totali identici alla fattura
        BigDecimal aliquota = fattura.getAliquotaIva() != null ? fattura.getAliquotaIva() : BigDecimal.ZERO;
        List<RigaCalcolata> righe = righeDaFattura(fattura, aliquota);
        BigDecimal totale = nz(fattura.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totaleImponibile = nz(fattura.getImponibile()).setScale(2, RoundingMode.HALF_UP);

        // 3. Documento NDC: importi negativi
        LocalDate oggi = LocalDate.now();
        String documentNumber = fiscalDocumentDAO.generateDocumentNumber(
                tenantId, tipoNdc.getId(), PREFISSO_NDC, oggi.getYear());
        FiscalDocument ndc = fiscalDocumentDAO.insert(FiscalDocument.builder()
                .fkTenantId(tenantId)
                .fkBookingId(bookingId)
                .fkOwnerId(fattura.getFkOwnerId())
                .fkTipoDocumentoId(tipoNdc.getId())
                .fkStatoDocumentoId(stato(STATO_READY).getId())
                .fkDocumentoCollegatoId(fattura.getId())
                .documentNumber(documentNumber)
                .issueDate(oggi)
                .recipientName(fattura.getRecipientName())
                .recipientTaxCode(fattura.getRecipientTaxCode())
                .totalAmount(totale.negate())
                .imponibile(totaleImponibile.negate())
                .vatAmount(totale.subtract(totaleImponibile).negate())
                .aliquotaIva(aliquota)
                .bolloAmount(BigDecimal.ZERO.setScale(2))
                .build());

        int ordine = 10;
        for (RigaCalcolata r : righe) {
            rigaNdcDAO.insert(FiscalDocumentRigaNdc.builder()
                    .fkFiscalDocumentId(ndc.getId())
                    .fkTenantId(tenantId)
                    .fkSplitEconomicoId(r.fkSplitEconomicoId())
                    .descrizione(r.descrizione())
                    .importoStornato(r.importo())
                    .imponibileStornato(r.imponibile())
                    .aliquotaIva(aliquota)
                    .ordinamento(ordine)
                    .createdBy(utenteId)
                    .build());
            ordine += 10;
        }

        // 4. Booking → stornata
        aggiornaStatoBooking(bookingId, STATO_BOOKING_STORNATA);

        // 5. Ricevuta owner collegata → annullata (se non trasmessa)
        ricevutaDelBooking(bookingId).ifPresent(ricevuta -> {
            String statoRicevuta = codiceStato(ricevuta.getFkStatoDocumentoId());
            if (!STATI_TRASMESSI.contains(statoRicevuta)) {
                fiscalDocumentDAO.updateStato(ricevuta.getId(), stato(STATO_ANNULLATA).getId());
                log.info("NdcService - ricevuta {} annullata insieme alla NDC", ricevuta.getDocumentNumber());
            } else {
                log.warn("NdcService - ricevuta {} già inviata SDI: non annullata, verificare manualmente",
                        ricevuta.getDocumentNumber());
            }
        });

        // 6. Ritenuta
        stornaRitenute(tenantId, bookingId, ndc);

        auditService.log("document.ndc.issue", "FiscalDocument", ndc.getId(),
                "Emessa nota di credito " + documentNumber + " a storno totale della fattura "
                        + fattura.getDocumentNumber());
        log.info("NdcService.emettiNdc() - tenant={} fattura={} ndc={} totale={}",
                tenantId, fattura.getDocumentNumber(), documentNumber, totale.negate());

        // 7. XML TD04 solo se la fattura è stata trasmessa allo SDI
        if (!STATI_TRASMESSI.contains(statoFattura)) {
            log.info("NdcService - NDC {} senza invio SDI: fattura {} non inviata",
                    documentNumber, fattura.getDocumentNumber());
        } else {
            boolean autoSend = tenantSettingsDAO.findByTenantId(tenantId)
                    .map(TenantSettings::getSdiAutoSend)
                    .orElse(true);
            if (autoSend) {
                try {
                    sdiXmlService.generaEInvia(tenantId, ndc.getId());
                } catch (Exception e) {
                    // La NDC resta valida: l'invio si ritenta dal dettaglio documento
                    log.error("NdcService.emettiNdc() - invio SDI automatico fallito per ndc={}: {}",
                            ndc.getId(), e.getMessage(), e);
                }
            }
        }
        return fiscalDocumentDAO.findById(ndc.getId()).orElseThrow();
    }

    /**
     * Annulla una NDC non ancora trasmessa: NDC 'annullata' con righe cancellate, ricevuta
     * owner di nuovo 'ready', ledger ripristinato, booking di nuovo 'doc_issued' (fattura e
     * ricevuta emesse) o 'ready'.
     *
     * @throws NoSuchElementException NDC inesistente o di altro tenant (404)
     * @throws IllegalStateException  NDC già inviata allo SDI, già annullata, o booking già copiato (400)
     */
    public FiscalDocument annullaNdc(Integer tenantId, Integer ndcId) {
        FiscalDocument ndc = fiscalDocumentDAO.findById(ndcId)
                .filter(d -> tenantId.equals(d.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Nota di credito non trovata: id=" + ndcId));
        if (!tipo(CODICE_NOTA_CREDITO).getId().equals(ndc.getFkTipoDocumentoId())) {
            throw new NoSuchElementException("Il documento id=" + ndcId + " non è una nota di credito");
        }
        String statoNdc = codiceStato(ndc.getFkStatoDocumentoId());
        if (STATI_TRASMESSI.contains(statoNdc)) {
            throw new IllegalStateException("Impossibile annullare NDC già inviata allo SDI");
        }
        if (STATO_ANNULLATA.equals(statoNdc)) {
            throw new IllegalStateException("Nota di credito già annullata");
        }
        // Credito d'imposta già usato in un F24 (migration 027): togliere prima la compensazione
        if (withholdingLedgerDAO.countCompensatiByNdc(ndcId) > 0) {
            throw new IllegalStateException("Impossibile annullare: il credito d'imposta della nota di credito "
                    + "è già stato compensato in un F24");
        }
        Integer bookingId = ndc.getFkBookingId();
        // Con una copia attiva il booking originale tornerebbe valido accanto alla copia
        bookingDAO.findByOrigine(bookingId, tenantId).ifPresent(copia -> {
            throw new IllegalStateException("Impossibile annullare: la prenotazione è già stata copiata in "
                    + copia.getExternalBookingId());
        });

        fiscalDocumentDAO.updateStato(ndcId, stato(STATO_ANNULLATA).getId());
        rigaNdcDAO.deleteByFiscalDocumentId(ndcId);

        // Ricevuta annullata dalla NDC: l'unico modo in cui una ricevuta diventa 'annullata'
        Integer annullataId = stato(STATO_ANNULLATA).getId();
        ricevutaDelBooking(bookingId)
                .filter(r -> annullataId.equals(r.getFkStatoDocumentoId()))
                .ifPresent(r -> {
                    fiscalDocumentDAO.updateStato(r.getId(), stato(STATO_READY).getId());
                    log.info("NdcService - ricevuta {} ripristinata a 'ready'", r.getDocumentNumber());
                });

        withholdingLedgerService.ripristinaDaNdc(ndcId);

        Integer statoStornataId = statoPrenotazioneDAO.findByCodice(STATO_BOOKING_STORNATA)
                .map(StatoPrenotazione::getId).orElse(null);
        bookingDAO.findById(bookingId)
                .filter(b -> statoStornataId != null && statoStornataId.equals(b.getFkStatoPrenotazioneId()))
                .ifPresent(b -> {
                    Integer tipoFatturaId = tipo(CODICE_FATTURA).getId();
                    Integer tipoRicevutaId = tipo(CODICE_RICEVUTA).getId();
                    List<FiscalDocument> docs = fiscalDocumentDAO.findByBookingId(b.getId());
                    boolean entrambi = docs.stream().anyMatch(d -> tipoFatturaId.equals(d.getFkTipoDocumentoId()))
                            && docs.stream().anyMatch(d -> tipoRicevutaId.equals(d.getFkTipoDocumentoId()));
                    aggiornaStatoBooking(b.getId(), entrambi ? STATO_BOOKING_DOC_ISSUED : STATO_BOOKING_READY);
                });

        auditService.log("document.ndc.cancel", "FiscalDocument", ndcId,
                "Annullata nota di credito " + ndc.getDocumentNumber());
        log.info("NdcService.annullaNdc() - tenant={} ndc={}", tenantId, ndc.getDocumentNumber());
        return fiscalDocumentDAO.findById(ndcId).orElseThrow();
    }

    /** NDC non annullate collegate alla fattura. */
    public List<FiscalDocument> ndcAttive(Integer fatturaId, TipoDocumento tipoNdc) {
        Integer annullataId = statoDocumentoDAO.findByCodice(STATO_ANNULLATA).map(StatoDocumento::getId).orElse(null);
        FiscalDocument fattura = fiscalDocumentDAO.findById(fatturaId).orElse(null);
        if (fattura == null) return List.of();
        return fiscalDocumentDAO.findByBookingId(fattura.getFkBookingId()).stream()
                .filter(d -> tipoNdc.getId().equals(d.getFkTipoDocumentoId()))
                .filter(d -> fatturaId.equals(d.getFkDocumentoCollegatoId()))
                .filter(d -> !d.getFkStatoDocumentoId().equals(annullataId))
                .toList();
    }

    // ── righe e ritenute ──────────────────────────────────────────────────────

    /**
     * Voci della fattura: righe split in fattura PM con importo > 0 (stesso filtro di
     * VociFatturaPmService, quindi di PDF e XML). Il residuo di arrotondamento dell'imponibile
     * va sull'ultima riga, così la somma coincide con l'imponibile della fattura.
     * Booking senza righe split (pre-migrazione 018): una riga unica con i totali della fattura.
     */
    private List<RigaCalcolata> righeDaFattura(FiscalDocument fattura, BigDecimal aliquota) {
        List<BookingSplitEconomico> voci = splitEconomicoDAO.findByBookingId(fattura.getFkBookingId()).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIncludeInFatturaPm()))
                .filter(r -> r.getImporto() != null && r.getImporto().signum() > 0)
                .toList();
        BigDecimal totaleFattura = nz(fattura.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal imponibileFattura = nz(fattura.getImponibile()).setScale(2, RoundingMode.HALF_UP);
        List<RigaCalcolata> righe = new ArrayList<>();
        if (voci.isEmpty()) {
            righe.add(new RigaCalcolata(null, "Storno fattura " + fattura.getDocumentNumber(),
                    totaleFattura, imponibileFattura));
            return righe;
        }
        BigDecimal sommaImporti = voci.stream().map(BookingSplitEconomico::getImporto)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (sommaImporti.compareTo(totaleFattura) != 0) {
            throw new IllegalStateException("Le voci della prenotazione (" + sommaImporti
                    + ") non coincidono con il totale della fattura (" + totaleFattura + ")");
        }
        BigDecimal sommaImponibili = BigDecimal.ZERO;
        for (BookingSplitEconomico v : voci) {
            BigDecimal importo = v.getImporto().setScale(2, RoundingMode.HALF_UP);
            BigDecimal imponibile = aliquota.signum() <= 0 ? importo
                    : v.getImponibile() != null ? v.getImponibile().setScale(2, RoundingMode.HALF_UP)
                    : importo.divide(BigDecimal.ONE.add(aliquota.divide(CENTO, 10, RoundingMode.HALF_UP)),
                            2, RoundingMode.HALF_UP);
            sommaImponibili = sommaImponibili.add(imponibile);
            righe.add(new RigaCalcolata(v.getId(), "Storno " + v.getDescrizione(), importo, imponibile));
        }
        BigDecimal residuo = imponibileFattura.subtract(sommaImponibili);
        if (residuo.signum() != 0) {
            RigaCalcolata ultima = righe.remove(righe.size() - 1);
            righe.add(new RigaCalcolata(ultima.fkSplitEconomicoId(), ultima.descrizione(),
                    ultima.importo(), ultima.imponibile().add(residuo)));
        }
        return righe;
    }

    /**
     * Storno delle ritenute del booking (righe ordinarie, non già toccate da una NDC):
     * - F24 versato ('paid'/'sent') → credito d'imposta pari alla ritenuta;
     * - F24 non pagato → ritenuta stornata e rimossa dall'F24, totale ricalcolato;
     * - nessun F24 → ritenuta stornata.
     */
    private void stornaRitenute(Integer tenantId, Integer bookingId, FiscalDocument ndc) {
        for (WithholdingLedger riga : withholdingLedgerDAO.findByBookingId(bookingId)) {
            if (riga.getFkNdcId() != null || LEDGER_STORNATA.equals(riga.getStato())
                    || LEDGER_CREDITO.equals(riga.getStato())) {
                continue;
            }
            if (riga.getFkF24RecordId() != null) {
                F24Record f24 = f24RecordDAO.findById(riga.getFkF24RecordId()).orElse(null);
                if (f24 != null && STATI_F24_VERSATO.contains(f24.getStato())) {
                    BigDecimal credito = nz(riga.getRitenutaAmount()).negate();
                    withholdingLedgerService.registraCredito(tenantId, riga, ndc, credito);
                    log.warn("NdcService - ritenuta già versata: credito imposta €{} per booking {}",
                            credito.negate(), bookingId);
                } else {
                    Integer f24Id = riga.getFkF24RecordId();
                    withholdingLedgerService.stornaRitenutaDaF24(tenantId, riga.getId(), ndc.getId());
                    f24Service.ricalcolaTotale(f24Id);
                }
            } else {
                withholdingLedgerService.stornaRitenuta(tenantId, riga.getId(), ndc.getId());
            }
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Optional<FiscalDocument> ricevutaDelBooking(Integer bookingId) {
        Integer tipoRicevutaId = tipo(CODICE_RICEVUTA).getId();
        return fiscalDocumentDAO.findByBookingId(bookingId).stream()
                .filter(d -> tipoRicevutaId.equals(d.getFkTipoDocumentoId()))
                .findFirst();
    }

    private void aggiornaStatoBooking(Integer bookingId, String codice) {
        Integer statoId = statoPrenotazioneDAO.findByCodice(codice)
                .map(StatoPrenotazione::getId)
                .orElseThrow(() -> new IllegalStateException("Stato prenotazione '" + codice + "' non trovato"));
        bookingDAO.updateStato(bookingId, statoId);
        log.info("NdcService - booking {} → {}", bookingId, codice);
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
        return statoDocumentoDAO.findById(statoId).map(StatoDocumento::getCodice).orElse(null);
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}

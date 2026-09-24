package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.SdiEsitoDAO;
import it.gavia.sostitutoincloud.dao.SettlementBookingDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dto.document.DocumentDetailDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentListDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentRowDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.SdiEsito;
import it.gavia.sostitutoincloud.model.Settlement;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.util.TenantAddressUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
public class FiscalDocumentService {

    /** Divisore per passare dall'aliquota in percentuale al moltiplicatore (22.00 → 0.22). */
    private static final BigDecimal CENTO = new BigDecimal("100");

    /** Codice della lookup tipo_documento per la ricevuta owner (il dominio la chiama ricevuta_owner). */
    private static final String CODICE_RICEVUTA = "ricevuta";

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final BookingDAO bookingDAO;
    private final PropertyDAO propertyDAO;
    private final CanaleOtaDAO canaleOtaDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final SdiEsitoDAO sdiEsitoDAO;
    private final TenantDAO tenantDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final F24RecordDAO f24RecordDAO;
    private final CuRecordDAO cuRecordDAO;
    private final SettlementBookingDAO settlementBookingDAO;
    private final SettlementDAO settlementDAO;

    public FiscalDocumentService(FiscalDocumentDAO fiscalDocumentDAO,
                                  BookingDAO bookingDAO,
                                  PropertyDAO propertyDAO,
                                  CanaleOtaDAO canaleOtaDAO,
                                  TipoDocumentoDAO tipoDocumentoDAO,
                                  StatoDocumentoDAO statoDocumentoDAO,
                                  SdiEsitoDAO sdiEsitoDAO,
                                  TenantDAO tenantDAO,
                                  OwnerProfileDAO ownerProfileDAO,
                                  WithholdingLedgerDAO withholdingLedgerDAO,
                                  F24RecordDAO f24RecordDAO,
                                  CuRecordDAO cuRecordDAO,
                                  SettlementBookingDAO settlementBookingDAO,
                                  SettlementDAO settlementDAO) {
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.f24RecordDAO = f24RecordDAO;
        this.cuRecordDAO = cuRecordDAO;
        this.settlementBookingDAO = settlementBookingDAO;
        this.settlementDAO = settlementDAO;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.bookingDAO = bookingDAO;
        this.propertyDAO = propertyDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.sdiEsitoDAO = sdiEsitoDAO;
        this.tenantDAO = tenantDAO;
        this.ownerProfileDAO = ownerProfileDAO;
    }

    private record LookupMaps(
            Map<Integer, TipoDocumento> tipiById,
            Map<Integer, StatoDocumento> statiById,
            Map<Integer, SdiEsito> sdiEsitiById,
            Map<Integer, OwnerProfile> ownersById
    ) {}

    private LookupMaps buildLookupMaps(Integer tenantId) {
        Map<Integer, TipoDocumento> tipiById = tipoDocumentoDAO.findAll().stream()
                .collect(Collectors.toMap(TipoDocumento::getId, t -> t));
        Map<Integer, StatoDocumento> statiById = statoDocumentoDAO.findAll().stream()
                .collect(Collectors.toMap(StatoDocumento::getId, s -> s));
        Map<Integer, SdiEsito> sdiEsitiById = sdiEsitoDAO.findAll().stream()
                .collect(Collectors.toMap(SdiEsito::getId, e -> e));
        // Tutti gli owner del tenant caricati una volta sola (no N+1).
        Map<Integer, OwnerProfile> ownersById = ownerProfileDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(OwnerProfile::getId, o -> o));
        return new LookupMaps(tipiById, statiById, sdiEsitiById, ownersById);
    }

    /**
     * Nome visualizzato del proprietario: "nome cognome" per persone fisiche,
     * altrimenti ragione sociale. Restituisce null se l'owner non è presente.
     */
    private String ownerDisplayName(OwnerProfile owner) {
        if (owner == null) return null;
        if (owner.getFirstName() != null && owner.getLastName() != null) {
            return owner.getFirstName() + " " + owner.getLastName();
        }
        return owner.getLegalName();
    }

    /**
     * Righe del documento, coerenti con gli importi memorizzati su fiscal_document:
     * la somma delle righe deve quadrare con imponibile / vat_amount / total_amount,
     * che il frontend mostra come riga totali.
     */
    private List<DocumentRowDTO> buildRighe(FiscalDocument doc, Booking booking, TipoDocumento tipo) {
        if (tipo == null || booking == null) return Collections.emptyList();
        List<DocumentRowDTO> righe = new ArrayList<>();
        if (Boolean.TRUE.equals(tipo.getRichiedeIva())) {
            // Gli importi dei servizi sono GIÀ LORDI (IVA inclusa): l'IVA va scorporata dal
            // lordo, non aggiunta sopra — stessa regola di DocumentGenerationService.
            // L'aliquota è quella memorizzata sul documento (0 in regime forfettario),
            // mai una costante: il regime del PM può cambiare fra un documento e l'altro.
            BigDecimal aliquota = doc.getAliquotaIva() != null ? doc.getAliquotaIva() : BigDecimal.ZERO;
            righe.add(buildRigaScorporata("Riaddebito commissione OTA", booking.getOtaCommissionAmount(), aliquota));
            righe.add(buildRigaScorporata("Riaddebito pulizie", booking.getCleaningAmount(), aliquota));
            righe.add(buildRigaScorporata("Provvigione PM", booking.getPmFeeAmount(), aliquota));
            allineaResiduoScorporo(righe, doc);
        } else {
            // Ricevuta owner: la riga è il canone del proprietario (= imponibile del documento),
            // non il lordo ospite — dal lordo sono già stati tolti i servizi PM, che il PM
            // fattura a parte. Il lordo ospite qui gonfiava la riga rispetto al documento.
            BigDecimal canone = primoNonNullo(doc.getCanoneLocazione(), doc.getImponibile(),
                    doc.getTotalAmount(), BigDecimal.ZERO);
            righe.add(DocumentRowDTO.builder()
                    .descrizione("Canone locazione breve")
                    .importoNetto(canone)
                    .aliquotaIva(BigDecimal.ZERO)
                    .importoIva(BigDecimal.ZERO)
                    .importoLordo(canone)
                    .build());
        }
        return righe;
    }

    /**
     * Riga da importo lordo IVA inclusa: imponibile = lordo / (1 + aliquota/100).
     * Aliquota 0 (forfettario o fuori campo IVA) → imponibile = lordo, IVA = 0.
     */
    private DocumentRowDTO buildRigaScorporata(String descrizione, BigDecimal lordo, BigDecimal aliquota) {
        BigDecimal l = (lordo != null ? lordo : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netto = l;
        BigDecimal iva = BigDecimal.ZERO.setScale(2);
        if (aliquota != null && aliquota.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal divisore = BigDecimal.ONE.add(aliquota.divide(CENTO, 4, RoundingMode.HALF_UP));
            netto = l.divide(divisore, 2, RoundingMode.HALF_UP);
            iva = l.subtract(netto);
        }
        return DocumentRowDTO.builder()
                .descrizione(descrizione)
                .importoNetto(netto)
                .aliquotaIva(aliquota != null ? aliquota : BigDecimal.ZERO)
                .importoIva(iva)
                .importoLordo(l)
                .build();
    }

    /**
     * Lo scorporo riga per riga può differire di qualche centesimo da quello fatto una volta
     * sola sul totale in fase di emissione. Il residuo va sull'ultima riga, così la somma
     * delle righe coincide esattamente con l'imponibile e l'IVA del documento.
     */
    private void allineaResiduoScorporo(List<DocumentRowDTO> righe, FiscalDocument doc) {
        if (righe.isEmpty() || doc.getImponibile() == null) return;
        BigDecimal sommaNetti = righe.stream()
                .map(DocumentRowDTO::getImportoNetto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal residuo = doc.getImponibile().subtract(sommaNetti);
        if (residuo.compareTo(BigDecimal.ZERO) == 0) return;

        DocumentRowDTO ultima = righe.get(righe.size() - 1);
        ultima.setImportoNetto(ultima.getImportoNetto().add(residuo));
        ultima.setImportoIva(ultima.getImportoLordo().subtract(ultima.getImportoNetto()));
        log.debug("FiscalDocumentService.buildRighe() - residuo scorporo {} allocato sull'ultima riga del doc={}",
                residuo, doc.getId());
    }

    /** Primo valore non null fra quelli passati. */
    private BigDecimal primoNonNullo(BigDecimal... valori) {
        for (BigDecimal v : valori) {
            if (v != null) return v;
        }
        return BigDecimal.ZERO;
    }

    public List<DocumentListDTO> findByTenantId(Integer tenantId, String statoFilter, String q,
                                                  Integer ownerId, Integer page, Integer size) {
        LookupMaps lookup = buildLookupMaps(tenantId);

        List<FiscalDocument> docs = fiscalDocumentDAO.findByTenantId(tenantId);
        List<Booking> bookings = bookingDAO.findByTenantId(tenantId);
        List<Property> properties = propertyDAO.findByTenantId(tenantId);
        List<CanaleOta> canali = canaleOtaDAO.findAll();

        Map<Integer, Booking> bookingsById = bookings.stream()
                .collect(Collectors.toMap(Booking::getId, b -> b));
        Map<Integer, Property> propertiesById = properties.stream()
                .collect(Collectors.toMap(Property::getId, p -> p));
        Map<Integer, CanaleOta> canaliById = canali.stream()
                .collect(Collectors.toMap(CanaleOta::getId, c -> c));

        // Liquidazione per prenotazione: due query fisse invece di due per documento (no N+1).
        Map<Integer, Integer> settlementIdByBookingId =
                settlementBookingDAO.findSettlementIdByBookingIdForTenant(tenantId);
        Map<Integer, Settlement> settlementsById = settlementDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Settlement::getId, s -> s));

        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        List<DocumentListDTO> result = docs.stream()
                .filter(d -> {
                    if (statoFilter != null) {
                        StatoDocumento stato = lookup.statiById().get(d.getFkStatoDocumentoId());
                        return stato != null && statoFilter.equalsIgnoreCase(stato.getCodice());
                    }
                    return true;
                })
                .filter(d -> {
                    if (q != null && !q.isBlank()) {
                        String ql = q.toLowerCase();
                        boolean matchNum = d.getDocumentNumber() != null
                                && d.getDocumentNumber().toLowerCase().contains(ql);
                        boolean matchName = d.getRecipientName() != null
                                && d.getRecipientName().toLowerCase().contains(ql);
                        String ownerName = ownerDisplayName(lookup.ownersById().get(d.getFkOwnerId()));
                        boolean matchOwner = ownerName != null && ownerName.toLowerCase().contains(ql);
                        return matchNum || matchName || matchOwner;
                    }
                    return true;
                })
                .filter(d -> {
                    if (ownerId == null) return true;
                    // Filtro diretto sul campo denormalizzato fk_owner_id del documento.
                    return ownerId.equals(d.getFkOwnerId());
                })
                .skip((long) pageNum * pageSize)
                .limit(pageSize)
                .map(d -> {
                    Booking booking = d.getFkBookingId() != null ? bookingsById.get(d.getFkBookingId()) : null;
                    Property property = booking != null ? propertiesById.get(booking.getFkPropertyId()) : null;
                    // Owner risolto direttamente dal campo denormalizzato fk_owner_id (nessuna catena).
                    OwnerProfile owner = d.getFkOwnerId() != null ? lookup.ownersById().get(d.getFkOwnerId()) : null;
                    CanaleOta canale = booking != null ? canaliById.get(booking.getFkCanaleOtaId()) : null;
                    TipoDocumento tipo = lookup.tipiById().get(d.getFkTipoDocumentoId());
                    StatoDocumento stato = lookup.statiById().get(d.getFkStatoDocumentoId());
                    SdiEsito sdiEsito = d.getFkSdiEsitoId() != null
                            ? lookup.sdiEsitiById().get(d.getFkSdiEsitoId()) : null;
                    Integer settlementId = d.getFkBookingId() != null
                            ? settlementIdByBookingId.get(d.getFkBookingId()) : null;
                    Settlement settlement = settlementId != null ? settlementsById.get(settlementId) : null;

                    return DocumentListDTO.builder()
                            .id(d.getId())
                            .documentNumber(d.getDocumentNumber())
                            .documentType(tipo != null ? tipo.getCodice() : null)
                            .issueDate(d.getIssueDate())
                            .recipientName(d.getRecipientName())
                            .recipientTaxCode(d.getRecipientTaxCode())
                            .totalAmount(d.getTotalAmount())
                            .vatAmount(d.getVatAmount())
                            .statoDocumento(stato != null ? stato.getCodice() : null)
                            .sdiIdentifier(d.getSdiIdentifier())
                            .sdiEsito(sdiEsito != null ? sdiEsito.getCodice() : null)
                            .sdiProgressivo(d.getSdiProgressivo())
                            .sdiFilePath(d.getSdiFilePath())
                            .sdiSentAt(d.getSdiSentAt())
                            .sdiErrorMsg(d.getSdiErrorMsg())
                            .propertyName(property != null ? property.getDisplayName() : null)
                            .channelName(canale != null ? canale.getNome() : null)
                            .fkBookingId(d.getFkBookingId())
                            .externalBookingId(booking != null ? booking.getExternalBookingId() : null)
                            .fkOwnerId(d.getFkOwnerId())
                            .ownerName(ownerDisplayName(owner))
                            .createdAt(d.getCreatedAt())
                            .settlementId(settlementId)
                            .settlementStato(settlement != null ? settlement.getStato() : null)
                            .build();
                })
                .collect(Collectors.toList());

        log.info("FiscalDocumentService.findByTenantId() - tenantId={}, ownerId={}, risultati={}",
                tenantId, ownerId, result.size());
        return result;
    }

    /**
     * Ricevute owner di un singolo proprietario — portale owner.
     *
     * <p>L'ownerId arriva dal token, non dal client. Riusa findByTenantId() con il filtro
     * per owner già presente, così la mappatura verso DocumentListDTO (immobile, canale,
     * codici di stato) resta in un solo posto, e tiene solo le ricevute: le fatture PM
     * sono documenti del property manager, non del proprietario.
     *
     * <p>NB il codice della lookup è {@code ricevuta}: {@code ricevuta_owner} è il termine
     * di dominio e non esiste in tipo_documento.
     */
    public List<DocumentListDTO> findRicevuteByOwner(Integer tenantId, Integer ownerId) {
        List<DocumentListDTO> result = findByTenantId(tenantId, null, null, ownerId, 0, Integer.MAX_VALUE)
                .stream()
                .filter(d -> CODICE_RICEVUTA.equals(d.getDocumentType()))
                .sorted(Comparator.comparing(DocumentListDTO::getIssueDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        log.info("FiscalDocumentService.findRicevuteByOwner() - tenantId={} ownerId={} {} ricevute",
                tenantId, ownerId, result.size());
        return result;
    }

    /**
     * Il documento è una ricevuta del proprietario indicato? Usato per autorizzare il
     * download del PDF dal portale owner, dove findById() da solo non basta: filtra per
     * tenant ma non per proprietario.
     */
    public boolean isRicevutaDellOwner(Integer tenantId, Integer ownerId, Integer documentId) {
        boolean mia = findRicevuteByOwner(tenantId, ownerId).stream()
                .anyMatch(d -> documentId.equals(d.getId()));
        log.debug("FiscalDocumentService.isRicevutaDellOwner() - ownerId={} documentId={} esito={}",
                ownerId, documentId, mia);
        return mia;
    }

    public Optional<DocumentDetailDTO> findById(Integer tenantId, Integer documentId) {
        Optional<FiscalDocument> opt = fiscalDocumentDAO.findById(documentId);
        if (opt.isEmpty() || !tenantId.equals(opt.get().getFkTenantId())) {
            return Optional.empty();
        }
        FiscalDocument doc = opt.get();
        LookupMaps lookup = buildLookupMaps(doc.getFkTenantId());

        Booking booking = doc.getFkBookingId() != null
                ? bookingDAO.findById(doc.getFkBookingId()).orElse(null)
                : null;
        Property property = booking != null
                ? propertyDAO.findById(booking.getFkPropertyId()).orElse(null)
                : null;
        CanaleOta canale = booking != null
                ? canaleOtaDAO.findById(booking.getFkCanaleOtaId()).orElse(null)
                : null;

        TipoDocumento tipo = lookup.tipiById().get(doc.getFkTipoDocumentoId());
        StatoDocumento stato = lookup.statiById().get(doc.getFkStatoDocumentoId());
        SdiEsito sdiEsito = doc.getFkSdiEsitoId() != null
                ? lookup.sdiEsitiById().get(doc.getFkSdiEsitoId()) : null;
        Tenant tenant = tenantDAO.findById(doc.getFkTenantId()).orElse(null);

        List<DocumentRowDTO> righe = buildRighe(doc, booking, tipo);

        DocumentDetailDTO detail = DocumentDetailDTO.builder()
                .id(doc.getId())
                .fkTenantId(doc.getFkTenantId())
                .fkTipoDocumentoId(doc.getFkTipoDocumentoId())
                .fkStatoDocumentoId(doc.getFkStatoDocumentoId())
                .documentNumber(doc.getDocumentNumber())
                .documentType(tipo != null ? tipo.getCodice() : null)
                .richiedeIva(tipo != null ? tipo.getRichiedeIva() : null)
                .issueDate(doc.getIssueDate())
                .recipientName(doc.getRecipientName())
                .recipientTaxCode(doc.getRecipientTaxCode())
                .totalAmount(doc.getTotalAmount())
                .vatAmount(doc.getVatAmount())
                .aliquotaIva(doc.getAliquotaIva())
                .imponibile(doc.getImponibile())
                .ritenutaAmount(doc.getRitenutaAmount())
                .bolloAmount(doc.getBolloAmount())
                .canoneLocazione(doc.getCanoneLocazione())
                .fkDocumentoCollegatoId(doc.getFkDocumentoCollegatoId())
                .statoDocumento(stato != null ? stato.getCodice() : null)
                .sdiIdentifier(doc.getSdiIdentifier())
                .sdiEsito(sdiEsito != null ? sdiEsito.getCodice() : null)
                .sdiProgressivo(doc.getSdiProgressivo())
                .sdiFilePath(doc.getSdiFilePath())
                .sdiSentAt(doc.getSdiSentAt())
                .sdiErrorMsg(doc.getSdiErrorMsg())
                .propertyName(property != null ? property.getDisplayName() : null)
                .channelName(canale != null ? canale.getNome() : null)
                .fkBookingId(doc.getFkBookingId())
                .externalBookingId(booking != null ? booking.getExternalBookingId() : null)
                .checkinDate(booking != null ? booking.getCheckinDate() : null)
                .checkoutDate(booking != null ? booking.getCheckoutDate() : null)
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .righe(righe)
                // emittente (tenant)
                .tenantLegalName(tenant != null ? tenant.getLegalName() : null)
                .tenantVatNumber(tenant != null ? tenant.getVatNumber() : null)
                .tenantTaxCode(tenant != null ? tenant.getTaxCode() : null)
                // Indirizzo completo (via + CAP/comune/provincia): la card Emittente lo mostra
                // su una riga, come il PDF.
                .tenantLegalAddress(TenantAddressUtils.indirizzoCompleto(tenant))
                .tenantPec(tenant != null ? tenant.getPec() : null)
                .build();

        // Liquidazione della prenotazione: stessa logica di BookingService, valorizzata per
        // qualsiasi tipo di documento (la card è mostrata solo sulla ricevuta owner).
        popolaSettlement(detail, doc);

        // F24 e CU riguardano la ritenuta, quindi solo la ricevuta owner.
        if (tipo != null && !Boolean.TRUE.equals(tipo.getRichiedeIva())) {
            popolaF24(detail, doc);
            popolaCu(detail, doc, tenantId);
            log.debug("FiscalDocumentService: f24RecordId={} cuRecordId={} per doc={}",
                    detail.getF24RecordId(), detail.getCuRecordId(), documentId);
        }

        log.info("FiscalDocumentService.findById() - tenantId={}, documentId={}", tenantId, documentId);
        return Optional.of(detail);
    }

    /**
     * Liquidazione che include la prenotazione del documento: si passa da
     * settlement_booking, come fa BookingService per il dettaglio prenotazione.
     * Documento senza prenotazione o prenotazione non ancora liquidata → campi a null.
     */
    private void popolaSettlement(DocumentDetailDTO detail, FiscalDocument doc) {
        if (doc.getFkBookingId() == null) {
            return;
        }
        settlementBookingDAO.findSettlementIdByBookingId(doc.getFkBookingId())
                .flatMap(settlementDAO::findById)
                .ifPresent(s -> {
                    detail.setSettlementId(s.getId());
                    detail.setSettlementStato(s.getStato());
                });
        log.debug("FiscalDocumentService: settlementStato={} per doc={}",
                detail.getSettlementStato(), doc.getId());
    }

    /**
     * Versamento F24 in cui è finita la ritenuta della ricevuta: si passa dalla riga
     * di withholding_ledger collegata al documento.
     */
    private void popolaF24(DocumentDetailDTO detail, FiscalDocument doc) {
        withholdingLedgerDAO.findByFiscalDocumentId(doc.getId()).ifPresent(wl -> {
            if (wl.getFkF24RecordId() == null) {
                return;   // ritenuta registrata ma non ancora inclusa in un F24
            }
            f24RecordDAO.findById(wl.getFkF24RecordId()).ifPresent(f24 -> {
                detail.setF24RecordId(f24.getId());
                detail.setF24Periodo(String.format("%02d/%d", f24.getPeriodoMese(), f24.getPeriodoAnno()));
                detail.setF24Stato(f24.getStato());
                detail.setF24Pagato("paid".equals(f24.getStato()));
            });
        });
    }

    /**
     * CU del proprietario per l'anno della ricevuta. Serve l'owner denormalizzato sul
     * documento: sui documenti che non l'hanno la CU non è determinabile.
     */
    private void popolaCu(DocumentDetailDTO detail, FiscalDocument doc, Integer tenantId) {
        if (doc.getFkOwnerId() == null || doc.getIssueDate() == null) {
            return;
        }
        Integer anno = doc.getIssueDate().getYear();
        cuRecordDAO.findByTenantOwnerYear(tenantId, doc.getFkOwnerId(), anno).ifPresent(cu -> {
            detail.setCuRecordId(cu.getId());
            detail.setCuTaxYear(cu.getTaxYear());
            detail.setCuStato(cu.getStato());
            detail.setCuConsegnata("delivered".equals(cu.getStato()) || "sent".equals(cu.getStato()));
        });
    }

    /**
     * Aggiorna lo stato di un documento fiscale verificando l'appartenenza al tenant
     * e risolvendo l'id dello stato dalla lookup per codice.
     * Restituisce il dettaglio aggiornato.
     */
    public DocumentDetailDTO aggiornaStato(Integer tenantId, Integer documentId, String nuovoStato) {
        FiscalDocument doc = fiscalDocumentDAO.findById(documentId)
                .filter(d -> tenantId.equals(d.getFkTenantId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Documento non trovato per questo tenant: id=" + documentId));
        StatoDocumento stato = statoDocumentoDAO.findByCodice(nuovoStato)
                .orElseThrow(() -> new IllegalArgumentException("Stato documento non valido: " + nuovoStato));

        fiscalDocumentDAO.updateStato(doc.getId(), stato.getId());
        log.info("FiscalDocumentService.aggiornaStato() - id={} stato={}", documentId, nuovoStato);

        return findById(tenantId, documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Documento non trovato dopo aggiornamento: id=" + documentId));
    }
}

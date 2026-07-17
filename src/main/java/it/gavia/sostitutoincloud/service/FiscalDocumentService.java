package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.SdiEsitoDAO;
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
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
public class FiscalDocumentService {

    private static final BigDecimal IVA_22 = new BigDecimal("0.22");

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final BookingDAO bookingDAO;
    private final PropertyDAO propertyDAO;
    private final CanaleOtaDAO canaleOtaDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final SdiEsitoDAO sdiEsitoDAO;
    private final TenantDAO tenantDAO;
    private final OwnerProfileDAO ownerProfileDAO;

    public FiscalDocumentService(FiscalDocumentDAO fiscalDocumentDAO,
                                  BookingDAO bookingDAO,
                                  PropertyDAO propertyDAO,
                                  CanaleOtaDAO canaleOtaDAO,
                                  TipoDocumentoDAO tipoDocumentoDAO,
                                  StatoDocumentoDAO statoDocumentoDAO,
                                  SdiEsitoDAO sdiEsitoDAO,
                                  TenantDAO tenantDAO,
                                  OwnerProfileDAO ownerProfileDAO) {
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

    private List<DocumentRowDTO> buildRighe(FiscalDocument doc, Booking booking, TipoDocumento tipo) {
        if (tipo == null || booking == null) return Collections.emptyList();
        List<DocumentRowDTO> righe = new ArrayList<>();
        if (Boolean.TRUE.equals(tipo.getRichiedeIva())) {
            righe.add(buildRigaConIva("Riaddebito commissione OTA", booking.getOtaCommissionAmount()));
            righe.add(buildRigaConIva("Riaddebito pulizie", booking.getCleaningAmount()));
            righe.add(buildRigaConIva("Provvigione PM", booking.getPmFeeAmount()));
        } else {
            BigDecimal gross = booking.getGrossAmount() != null ? booking.getGrossAmount() : BigDecimal.ZERO;
            righe.add(DocumentRowDTO.builder()
                    .descrizione("Compenso lordo ospite")
                    .importoNetto(gross)
                    .aliquotaIva(BigDecimal.ZERO)
                    .importoIva(BigDecimal.ZERO)
                    .importoLordo(gross)
                    .build());
        }
        return righe;
    }

    private DocumentRowDTO buildRigaConIva(String descrizione, BigDecimal netto) {
        BigDecimal n = netto != null ? netto : BigDecimal.ZERO;
        BigDecimal iva = n.multiply(IVA_22).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lordo = n.add(iva).setScale(2, RoundingMode.HALF_UP);
        return DocumentRowDTO.builder()
                .descrizione(descrizione)
                .importoNetto(n)
                .aliquotaIva(IVA_22)
                .importoIva(iva)
                .importoLordo(lordo)
                .build();
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
                            .propertyName(property != null ? property.getDisplayName() : null)
                            .channelName(canale != null ? canale.getNome() : null)
                            .fkBookingId(d.getFkBookingId())
                            .fkOwnerId(d.getFkOwnerId())
                            .ownerName(ownerDisplayName(owner))
                            .createdAt(d.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        log.info("FiscalDocumentService.findByTenantId() - tenantId={}, ownerId={}, risultati={}",
                tenantId, ownerId, result.size());
        return result;
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
                .imponibile(doc.getImponibile())
                .ritenutaAmount(doc.getRitenutaAmount())
                .bolloAmount(doc.getBolloAmount())
                .canoneLocazione(doc.getCanoneLocazione())
                .fkDocumentoCollegatoId(doc.getFkDocumentoCollegatoId())
                .statoDocumento(stato != null ? stato.getCodice() : null)
                .sdiIdentifier(doc.getSdiIdentifier())
                .sdiEsito(sdiEsito != null ? sdiEsito.getCodice() : null)
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
                .tenantLegalAddress(tenant != null ? tenant.getLegalAddress() : null)
                .tenantPec(tenant != null ? tenant.getPec() : null)
                .build();

        log.info("FiscalDocumentService.findById() - tenantId={}, documentId={}", tenantId, documentId);
        return Optional.of(detail);
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

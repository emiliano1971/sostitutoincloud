package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.ScenarioFiscaleDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dto.booking.BookingDetailDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingFilterDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingListDTO;
import it.gavia.sostitutoincloud.dto.booking.SplitEconomicoDTO;
import it.gavia.sostitutoincloud.dto.document.FiscalDocumentSummaryDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.ScenarioFiscale;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Log4j2
public class BookingService {

    private static final Set<String> STATI_ESCLUSI_DA_COMPLETARE =
            Set.of("doc_issued", "settled", "cancelled");

    private final BookingDAO bookingDAO;
    private final PropertyDAO propertyDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final CanaleOtaDAO canaleOtaDAO;
    private final StatoPrenotazioneDAO statoPrenotazioneDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final ScenarioFiscaleDAO scenarioFiscaleDAO;
    private final TenantDAO tenantDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final FiscalDocumentDAO fiscalDocumentDAO;

    public BookingService(BookingDAO bookingDAO,
                          PropertyDAO propertyDAO,
                          OwnerProfileDAO ownerProfileDAO,
                          CanaleOtaDAO canaleOtaDAO,
                          StatoPrenotazioneDAO statoPrenotazioneDAO,
                          StatoDocumentoDAO statoDocumentoDAO,
                          ScenarioFiscaleDAO scenarioFiscaleDAO,
                          TenantDAO tenantDAO,
                          TipoDocumentoDAO tipoDocumentoDAO,
                          FiscalDocumentDAO fiscalDocumentDAO) {
        this.bookingDAO = bookingDAO;
        this.propertyDAO = propertyDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.statoPrenotazioneDAO = statoPrenotazioneDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.scenarioFiscaleDAO = scenarioFiscaleDAO;
        this.tenantDAO = tenantDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
    }

    public List<BookingListDTO> findByTenantId(Integer tenantId, BookingFilterDTO filter) {
        LookupMaps maps = buildLookupMaps(tenantId);
        List<Booking> all = bookingDAO.findByTenantId(tenantId);

        int page = filter.getPage() != null ? filter.getPage() : 0;
        int size = filter.getSize() != null ? filter.getSize() : 20;

        Stream<Booking> stream = all.stream();
        stream = applyStatusFilter(stream, filter.getStatus(), maps);
        stream = applyChannelFilter(stream, filter.getChannel(), maps);
        stream = applySearchFilter(stream, filter.getQ());
        stream = stream.skip((long) page * size).limit(size);

        List<BookingListDTO> result = stream.map(b -> toListDTO(b, maps)).toList();
        log.info("BookingService.findByTenantId() - tenantId={}, filtro=[status={}, channel={}, q={}], {} booking trovati",
                tenantId, filter.getStatus(), filter.getChannel(), filter.getQ(), result.size());
        return result;
    }

    public Optional<BookingDetailDTO> findById(Integer tenantId, Integer bookingId) {
        log.info("BookingService.findById() - tenantId={}, bookingId={}", tenantId, bookingId);
        return bookingDAO.findById(bookingId)
                .filter(b -> tenantId.equals(b.getFkTenantId()))
                .map(b -> {
                    LookupMaps maps = buildLookupMaps(tenantId);
                    return toDetailDTO(b, maps);
                });
    }

    // ── filtri ──────────────────────────────────────────────────────────────

    private Stream<Booking> applyStatusFilter(Stream<Booking> stream, String status, LookupMaps maps) {
        if (status == null) return stream;
        if ("da_completare".equals(status)) {
            LocalDate oggi = LocalDate.now();
            return stream.filter(b -> {
                boolean scaduto = b.getCheckoutDate() != null && !b.getCheckoutDate().isAfter(oggi);
                String codice = statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById);
                return scaduto && !STATI_ESCLUSI_DA_COMPLETARE.contains(codice);
            });
        }
        return stream.filter(b ->
                status.equals(statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById)));
    }

    private Stream<Booking> applyChannelFilter(Stream<Booking> stream, String channel, LookupMaps maps) {
        if (channel == null) return stream;
        return stream.filter(b -> {
            CanaleOta c = maps.canaliById.get(b.getFkCanaleOtaId());
            return c != null && channel.equals(c.getCodice());
        });
    }

    private Stream<Booking> applySearchFilter(Stream<Booking> stream, String q) {
        if (q == null || q.isBlank()) return stream;
        String lower = q.toLowerCase();
        return stream.filter(b ->
                (b.getGuestName() != null && b.getGuestName().toLowerCase().contains(lower))
                || (b.getExternalBookingId() != null && b.getExternalBookingId().toLowerCase().contains(lower)));
    }

    // ── lookup helpers ───────────────────────────────────────────────────────

    private LookupMaps buildLookupMaps(Integer tenantId) {
        Map<Integer, Property> propertiesById = propertyDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Property::getId, p -> p));
        Map<Integer, OwnerProfile> ownersById = ownerProfileDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(OwnerProfile::getId, o -> o));
        Map<Integer, CanaleOta> canaliById = canaleOtaDAO.findAll().stream()
                .collect(Collectors.toMap(CanaleOta::getId, c -> c));
        Map<Integer, StatoPrenotazione> statiPrenotazioneById = statoPrenotazioneDAO.findAll().stream()
                .collect(Collectors.toMap(StatoPrenotazione::getId, s -> s));
        Map<Integer, StatoDocumento> statiDocumentoById = statoDocumentoDAO.findAll().stream()
                .collect(Collectors.toMap(StatoDocumento::getId, s -> s));
        Map<Integer, ScenarioFiscale> scenariById = scenarioFiscaleDAO.findAll().stream()
                .collect(Collectors.toMap(ScenarioFiscale::getId, s -> s));
        Map<Integer, TipoDocumento> tipiDocumentoById = tipoDocumentoDAO.findAll().stream()
                .collect(Collectors.toMap(TipoDocumento::getId, t -> t));
        Tenant tenant = tenantDAO.findById(tenantId).orElse(null);
        return new LookupMaps(propertiesById, ownersById, canaliById,
                statiPrenotazioneById, statiDocumentoById, scenariById, tipiDocumentoById, tenant);
    }

    private String statoCodiceDa(Integer id, Map<Integer, StatoPrenotazione> map) {
        if (id == null) return null;
        StatoPrenotazione s = map.get(id);
        return s != null ? s.getCodice() : null;
    }

    private String statoDocCodiceDa(Integer id, Map<Integer, StatoDocumento> map) {
        if (id == null) return null;
        StatoDocumento s = map.get(id);
        return s != null ? s.getCodice() : null;
    }

    private String resolveOwnerName(Property property, Map<Integer, OwnerProfile> ownersById) {
        if (property == null || property.getFkOwnerId() == null) return null;
        OwnerProfile o = ownersById.get(property.getFkOwnerId());
        return o != null ? o.getFirstName() + " " + o.getLastName() : null;
    }

    private BigDecimal safeVal(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ── mapping ─────────────────────────────────────────────────────────────

    private BookingListDTO toListDTO(Booking b, LookupMaps maps) {
        Property prop = maps.propertiesById.get(b.getFkPropertyId());
        CanaleOta canale = maps.canaliById.get(b.getFkCanaleOtaId());
        return BookingListDTO.builder()
                .id(b.getId())
                .externalBookingId(b.getExternalBookingId())
                .guestName(b.getGuestName())
                .propertyName(prop != null ? prop.getDisplayName() : null)
                .ownerName(resolveOwnerName(prop, maps.ownersById))
                .channelName(canale != null ? canale.getNome() : null)
                .checkinDate(b.getCheckinDate())
                .checkoutDate(b.getCheckoutDate())
                .nights(b.getNights())
                .guests(b.getGuests())
                .grossAmount(b.getGrossAmount())
                .ownerNetAmount(b.getOwnerNetAmount())
                .statoPrenotazione(statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById))
                .paymentStatus(b.getPaymentStatus())
                .documentStatus(statoDocCodiceDa(b.getFkStatoDocumentoId(), maps.statiDocumentoById))
                .settlementStatus(b.getSettlementStatus())
                .createdAt(b.getCreatedAt())
                .build();
    }

    private BookingDetailDTO toDetailDTO(Booking b, LookupMaps maps) {
        Property prop = maps.propertiesById.get(b.getFkPropertyId());
        CanaleOta canale = maps.canaliById.get(b.getFkCanaleOtaId());
        ScenarioFiscale scenario = maps.scenariById.get(b.getFkScenarioFiscaleId());
        OwnerProfile owner = prop != null && prop.getFkOwnerId() != null
                ? maps.ownersById.get(prop.getFkOwnerId()) : null;
        Tenant tenant = maps.tenant;

        BigDecimal ownerNet = safeVal(b.getOwnerNetAmount());
        BigDecimal withholding = safeVal(b.getWithholdingAmount());
        SplitEconomicoDTO split = SplitEconomicoDTO.builder()
                .grossAmount(b.getGrossAmount())
                .otaCommissionAmount(b.getOtaCommissionAmount())
                .cleaningAmount(b.getCleaningAmount())
                .pmFeeAmount(b.getPmFeeAmount())
                .ownerNetAmount(b.getOwnerNetAmount())
                .withholdingAmount(b.getWithholdingAmount())
                .liquidazioneOwner(ownerNet.subtract(withholding))
                .touristTaxAmount(b.getTouristTaxAmount())
                .touristTaxIncludedInGross(b.getTouristTaxIncludedInGross())
                .build();

        return BookingDetailDTO.builder()
                .id(b.getId())
                .fkTenantId(b.getFkTenantId())
                .fkPropertyId(b.getFkPropertyId())
                .fkOwnerId(prop != null ? prop.getFkOwnerId() : null)
                .externalBookingId(b.getExternalBookingId())
                .guestName(b.getGuestName())
                .guestTaxCode(b.getGuestTaxCode())
                .propertyName(prop != null ? prop.getDisplayName() : null)
                .ownerName(resolveOwnerName(prop, maps.ownersById))
                .channelName(canale != null ? canale.getNome() : null)
                .fiscalScenarioCode(scenario != null ? scenario.getCodice() : null)
                .checkinDate(b.getCheckinDate())
                .checkoutDate(b.getCheckoutDate())
                .nights(b.getNights())
                .guests(b.getGuests())
                .grossAmount(b.getGrossAmount())
                .otaCommissionAmount(b.getOtaCommissionAmount())
                .cleaningAmount(b.getCleaningAmount())
                .pmFeeAmount(b.getPmFeeAmount())
                .ownerNetAmount(b.getOwnerNetAmount())
                .withholdingAmount(b.getWithholdingAmount())
                .touristTaxAmount(b.getTouristTaxAmount())
                .touristTaxIncludedInGross(b.getTouristTaxIncludedInGross())
                .touristTaxCollection(b.getTouristTaxCollection())
                .statoPrenotazione(statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById))
                .paymentStatus(b.getPaymentStatus())
                .documentStatus(statoDocCodiceDa(b.getFkStatoDocumentoId(), maps.statiDocumentoById))
                .settlementStatus(b.getSettlementStatus())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .splitEconomico(split)
                // dati immobile (per dialog)
                .propertyAddress(prop != null ? prop.getAddress() : null)
                .propertyCity(prop != null ? prop.getCity() : null)
                .propertyInternalCode(prop != null ? prop.getInternalCode() : null)
                // dati proprietario (per dialog)
                .ownerTaxCode(owner != null ? owner.getTaxCode() : null)
                .ownerIban(owner != null ? owner.getIban() : null)
                .ownerEmail(owner != null ? owner.getEmail() : null)
                // dati tenant (per dialog fattura PM)
                .tenantLegalName(tenant != null ? tenant.getLegalName() : null)
                .tenantVatNumber(tenant != null ? tenant.getVatNumber() : null)
                .tenantTaxCode(tenant != null ? tenant.getTaxCode() : null)
                .tenantLegalAddress(tenant != null ? tenant.getLegalAddress() : null)
                .tenantPec(tenant != null ? tenant.getPec() : null)
                // documenti fiscali associati alla prenotazione
                .documenti(mapDocumenti(b.getId(), maps))
                .build();
    }

    private List<FiscalDocumentSummaryDTO> mapDocumenti(Integer bookingId, LookupMaps maps) {
        return fiscalDocumentDAO.findByBookingId(bookingId).stream()
                .map(d -> toDocumentSummaryDTO(d, maps))
                .toList();
    }

    private FiscalDocumentSummaryDTO toDocumentSummaryDTO(FiscalDocument d, LookupMaps maps) {
        TipoDocumento tipo = d.getFkTipoDocumentoId() != null
                ? maps.tipiDocumentoById.get(d.getFkTipoDocumentoId()) : null;
        return FiscalDocumentSummaryDTO.builder()
                .id(d.getId())
                .documentNumber(d.getDocumentNumber())
                .tipoDocumento(tipo != null ? tipo.getCodice() : null)
                .statoDocumento(statoDocCodiceDa(d.getFkStatoDocumentoId(), maps.statiDocumentoById))
                .dataEmissione(d.getIssueDate())
                .importoTotale(d.getTotalAmount())
                .imponibile(d.getImponibile())
                .ritenutaAmount(d.getRitenutaAmount())
                .bolloAmount(d.getBolloAmount())
                .ivaAmount(d.getIvaAmount())
                .build();
    }

    private record LookupMaps(
            Map<Integer, Property> propertiesById,
            Map<Integer, OwnerProfile> ownersById,
            Map<Integer, CanaleOta> canaliById,
            Map<Integer, StatoPrenotazione> statiPrenotazioneById,
            Map<Integer, StatoDocumento> statiDocumentoById,
            Map<Integer, ScenarioFiscale> scenariById,
            Map<Integer, TipoDocumento> tipiDocumentoById,
            Tenant tenant) {
    }
}

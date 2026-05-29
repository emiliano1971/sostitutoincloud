package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.SettlementBookingDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementBookingDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementDetailDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementListDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.Settlement;
import it.gavia.sostitutoincloud.model.SettlementBooking;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Log4j2
public class SettlementService {

    private static final Set<String> STATI_VALIDI = Set.of("pending", "calculated", "approved", "paid");

    private final SettlementDAO settlementDAO;
    private final SettlementBookingDAO settlementBookingDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final BookingDAO bookingDAO;
    private final PropertyDAO propertyDAO;

    public SettlementService(SettlementDAO settlementDAO,
                              SettlementBookingDAO settlementBookingDAO,
                              OwnerProfileDAO ownerProfileDAO,
                              BookingDAO bookingDAO,
                              PropertyDAO propertyDAO) {
        this.settlementDAO = settlementDAO;
        this.settlementBookingDAO = settlementBookingDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.bookingDAO = bookingDAO;
        this.propertyDAO = propertyDAO;
    }

    private String resolveOwnerName(OwnerProfile owner) {
        if (owner == null) return null;
        if (owner.getFirstName() != null && owner.getLastName() != null) {
            return owner.getFirstName() + " " + owner.getLastName();
        }
        return owner.getLegalName();
    }

    private BigDecimal resolveNetAmount(Settlement s) {
        if (s.getNetAmount() != null) return s.getNetAmount();
        BigDecimal total = s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal withholding = s.getWithholdingAmount() != null ? s.getWithholdingAmount() : BigDecimal.ZERO;
        return total.subtract(withholding);
    }

    public List<SettlementListDTO> findByTenantId(Integer tenantId, Integer ownerId, String period) {
        List<Settlement> settlements = settlementDAO.findByTenantId(tenantId);

        Map<Integer, OwnerProfile> ownersById = ownerProfileDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(OwnerProfile::getId, o -> o));

        List<SettlementListDTO> result = settlements.stream()
                .filter(s -> ownerId == null || ownerId.equals(s.getFkOwnerId()))
                .filter(s -> period == null || period.equals(s.getPeriod()))
                .map(s -> {
                    OwnerProfile owner = ownersById.get(s.getFkOwnerId());
                    int bookingsCount = settlementBookingDAO.findBySettlementId(s.getId()).size();
                    return SettlementListDTO.builder()
                            .id(s.getId())
                            .ownerName(resolveOwnerName(owner))
                            .period(s.getPeriod())
                            .totalAmount(s.getTotalAmount())
                            .withholdingAmount(s.getWithholdingAmount())
                            .netAmount(resolveNetAmount(s))
                            .bookingsCount(bookingsCount)
                            .stato(s.getStato())
                            .paymentDate(s.getPaymentDate())
                            .createdAt(s.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        log.info("SettlementService.findByTenantId() - tenantId={}, risultati={}", tenantId, result.size());
        return result;
    }

    public Optional<SettlementDetailDTO> findById(Integer tenantId, Integer settlementId) {
        Optional<Settlement> opt = settlementDAO.findById(settlementId);
        if (opt.isEmpty() || !tenantId.equals(opt.get().getFkTenantId())) {
            return Optional.empty();
        }
        Settlement s = opt.get();

        OwnerProfile owner = ownerProfileDAO.findById(s.getFkOwnerId()).orElse(null);

        List<SettlementBooking> settlementBookings = settlementBookingDAO.findBySettlementId(s.getId());

        Map<Integer, Property> propertiesById = propertyDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Property::getId, p -> p));

        List<SettlementBookingDTO> bookingDTOs = settlementBookings.stream()
                .map(sb -> {
                    Booking booking = bookingDAO.findById(sb.getFkBookingId()).orElse(null);
                    if (booking == null) return null;
                    Property property = propertiesById.get(booking.getFkPropertyId());
                    return SettlementBookingDTO.builder()
                            .bookingId(booking.getId())
                            .externalBookingId(booking.getExternalBookingId())
                            .propertyName(property != null ? property.getDisplayName() : null)
                            .checkinDate(booking.getCheckinDate())
                            .checkoutDate(booking.getCheckoutDate())
                            .grossAmount(booking.getGrossAmount())
                            .ownerNetAmount(booking.getOwnerNetAmount())
                            .withholdingAmount(booking.getWithholdingAmount())
                            .build();
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());

        SettlementDetailDTO detail = SettlementDetailDTO.builder()
                .id(s.getId())
                .fkTenantId(s.getFkTenantId())
                .fkOwnerId(s.getFkOwnerId())
                .ownerName(resolveOwnerName(owner))
                .period(s.getPeriod())
                .totalAmount(s.getTotalAmount())
                .withholdingAmount(s.getWithholdingAmount())
                .netAmount(resolveNetAmount(s))
                .bookingsCount(bookingDTOs.size())
                .stato(s.getStato())
                .paymentDate(s.getPaymentDate())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .bookings(bookingDTOs)
                .build();

        log.info("SettlementService.findById() - tenantId={}, settlementId={}", tenantId, settlementId);
        return Optional.of(detail);
    }

    public SettlementListDTO updateStatus(Integer tenantId, Integer settlementId, String nuovoStato) {
        Optional<Settlement> opt = settlementDAO.findById(settlementId);
        if (opt.isEmpty() || !tenantId.equals(opt.get().getFkTenantId())) {
            throw new RuntimeException("Settlement non trovato: id=" + settlementId);
        }
        if (!STATI_VALIDI.contains(nuovoStato)) {
            throw new IllegalArgumentException("Stato non valido: " + nuovoStato
                    + ". Valori ammessi: " + STATI_VALIDI);
        }
        log.warn("SettlementService.updateStatus() - operazione non implementata, tenantId={}, settlementId={}", tenantId, settlementId);
        throw new UnsupportedOperationException("updateStatus non ancora implementato");
    }
}

package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.SettlementBookingDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementBookingDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementCalcolaRequestDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementCalcolaResultDTO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final AuditService auditService;

    public SettlementService(SettlementDAO settlementDAO,
                              SettlementBookingDAO settlementBookingDAO,
                              OwnerProfileDAO ownerProfileDAO,
                              BookingDAO bookingDAO,
                              PropertyDAO propertyDAO,
                              WithholdingLedgerDAO withholdingLedgerDAO,
                              AuditService auditService) {
        this.settlementDAO = settlementDAO;
        this.settlementBookingDAO = settlementBookingDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.bookingDAO = bookingDAO;
        this.propertyDAO = propertyDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.auditService = auditService;
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

    /**
     * Calcola (o ricalcola) il settlement mensile di un singolo owner aggregando le ritenute
     * del periodo dal withholding_ledger e collegando le prenotazioni via settlement_booking.
     */
    private SettlementListDTO calcolaPerOwner(Integer tenantId, Integer ownerId, Integer mese, Integer anno) {
        String period = String.format("%d-%02d", anno, mese);

        // 1. Aggrega canone e ritenuta del periodo.
        Map<String, Object> row = withholdingLedgerDAO.aggregaByOwnerAndPeriodo(tenantId, ownerId, mese, anno);
        if (row.get("total_amount") == null) {
            throw new IllegalArgumentException("Nessuna ritenuta per owner=" + ownerId + " periodo=" + period);
        }
        BigDecimal totalAmount = toBigDecimal(row.get("total_amount"));
        BigDecimal withholdingAmount = toBigDecimal(row.get("withholding_amount"));
        BigDecimal netAmount = totalAmount.subtract(withholdingAmount);

        // 2. Prenotazioni collegate al periodo.
        List<Integer> bookingIds = withholdingLedgerDAO
                .findDistinctBookingIdsByOwnerAndPeriodo(tenantId, ownerId, mese, anno);

        // 3. Settlement esistente?
        Optional<Settlement> existingOpt = settlementDAO.findByOwnerAndPeriod(tenantId, ownerId, period);
        Settlement settlement;
        String tipo;
        if (existingOpt.isPresent()) {
            Settlement existing = existingOpt.get();
            if ("paid".equals(existing.getStato())) {
                throw new IllegalStateException("Settlement già pagato per " + period);
            }
            settlement = settlementDAO.updateTotali(existing.getId(), totalAmount, withholdingAmount, netAmount);
            settlementBookingDAO.deleteBySettlementId(existing.getId());
            for (Integer bookingId : bookingIds) {
                settlementBookingDAO.insert(existing.getId(), bookingId);
            }
            tipo = "updated";
        } else {
            Settlement toInsert = Settlement.builder()
                    .fkTenantId(tenantId)
                    .fkOwnerId(ownerId)
                    .period(period)
                    .periodoMese(mese)
                    .periodoAnno(anno)
                    .totalAmount(totalAmount)
                    .withholdingAmount(withholdingAmount)
                    .netAmount(netAmount)
                    .stato("calculated")
                    .build();
            settlement = settlementDAO.insert(toInsert);
            for (Integer bookingId : bookingIds) {
                settlementBookingDAO.insert(settlement.getId(), bookingId);
            }
            tipo = "generated";
        }

        // 4. Nome owner.
        OwnerProfile owner = ownerProfileDAO.findById(ownerId).orElse(null);

        log.info("SettlementService.calcolaPerOwner() - tenantId={} ownerId={} period={} tipo={}",
                tenantId, ownerId, period, tipo);

        return SettlementListDTO.builder()
                .id(settlement.getId())
                .ownerName(resolveOwnerName(owner))
                .period(settlement.getPeriod())
                .totalAmount(settlement.getTotalAmount())
                .withholdingAmount(settlement.getWithholdingAmount())
                .netAmount(resolveNetAmount(settlement))
                .bookingsCount(bookingIds.size())
                .stato(settlement.getStato())
                .paymentDate(settlement.getPaymentDate())
                .createdAt(settlement.getCreatedAt())
                .build();
    }

    /**
     * Calcola i settlement mensili per tutti gli owner con ritenute nel periodo.
     * Gli owner già liquidati (settlement 'paid') vengono saltati.
     */
    public SettlementCalcolaResultDTO calcola(Integer tenantId, SettlementCalcolaRequestDTO req) {
        Integer mese = req.getMese();
        Integer anno = req.getAnno();
        if (mese == null || mese < 1 || mese > 12) {
            throw new IllegalArgumentException("Mese non valido: " + mese + " (atteso 1-12)");
        }
        if (anno == null || anno <= 2020) {
            throw new IllegalArgumentException("Anno non valido: " + anno);
        }

        List<Integer> ownerIds = withholdingLedgerDAO.findDistinctOwnerIdsByPeriodo(tenantId, mese, anno);
        if (ownerIds.isEmpty()) {
            throw new IllegalArgumentException("Nessuna ritenuta per il periodo " + mese + "/" + anno);
        }

        String period = String.format("%d-%02d", anno, mese);
        int generated = 0;
        int updated = 0;
        int skipped = 0;
        List<SettlementListDTO> settlements = new ArrayList<>();

        for (Integer ownerId : ownerIds) {
            // Determina in anticipo se sarà un aggiornamento (settlement già presente).
            boolean esisteva = settlementDAO.findByOwnerAndPeriod(tenantId, ownerId, period).isPresent();
            try {
                SettlementListDTO dto = calcolaPerOwner(tenantId, ownerId, mese, anno);
                settlements.add(dto);
                if (esisteva) {
                    updated++;
                } else {
                    generated++;
                }
            } catch (IllegalStateException e) {
                skipped++;
                log.warn("SettlementService.calcola() - owner {} saltato: {}", ownerId, e.getMessage());
            } catch (IllegalArgumentException e) {
                log.warn("SettlementService.calcola() - owner {} senza ritenute: {}", ownerId, e.getMessage());
            }
        }

        auditService.log("settlement.calcola", "Settlement", null,
                "Calcolati settlement periodo " + mese + "/" + anno
                        + ": generated=" + generated + " updated=" + updated + " skipped=" + skipped);

        log.info("SettlementService.calcola() - period={}/{} gen={} upd={} skip={}",
                mese, anno, generated, updated, skipped);

        return SettlementCalcolaResultDTO.builder()
                .generated(generated)
                .updated(updated)
                .skipped(skipped)
                .settlements(settlements)
                .build();
    }

    public SettlementListDTO updateStatus(Integer tenantId, Integer settlementId, String nuovoStato) {
        if (!STATI_VALIDI.contains(nuovoStato)) {
            throw new IllegalArgumentException("Stato non valido: " + nuovoStato
                    + ". Valori ammessi: " + STATI_VALIDI);
        }
        Settlement s = settlementDAO.findById(settlementId)
                .filter(x -> tenantId.equals(x.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Settlement non trovato: id=" + settlementId));
        if ("paid".equals(s.getStato())) {
            throw new IllegalStateException("Settlement già pagato");
        }
        Settlement updated = settlementDAO.updateStato(settlementId, nuovoStato);
        OwnerProfile owner = ownerProfileDAO.findById(updated.getFkOwnerId()).orElse(null);
        int bookingsCount = settlementBookingDAO.findBySettlementId(updated.getId()).size();
        log.info("SettlementService.updateStatus() - tenantId={} settlementId={} stato={}",
                tenantId, settlementId, nuovoStato);
        return SettlementListDTO.builder()
                .id(updated.getId())
                .ownerName(resolveOwnerName(owner))
                .period(updated.getPeriod())
                .totalAmount(updated.getTotalAmount())
                .withholdingAmount(updated.getWithholdingAmount())
                .netAmount(resolveNetAmount(updated))
                .bookingsCount(bookingsCount)
                .stato(updated.getStato())
                .paymentDate(updated.getPaymentDate())
                .createdAt(updated.getCreatedAt())
                .build();
    }

    /** Converte in BigDecimal i valori aggregati provenienti da queryForMap (SUM → BigDecimal, COUNT → Long). */
    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(v.toString());
        return new BigDecimal(v.toString());
    }
}

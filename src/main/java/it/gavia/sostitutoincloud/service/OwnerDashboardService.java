package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dto.dashboard.MensileDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerDashboardDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.Settlement;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Log4j2
public class OwnerDashboardService {

    private final BookingDAO bookingDAO;
    private final SettlementDAO settlementDAO;
    private final OwnerProfileDAO ownerProfileDAO;

    public OwnerDashboardService(BookingDAO bookingDAO,
                                  SettlementDAO settlementDAO,
                                  OwnerProfileDAO ownerProfileDAO) {
        this.bookingDAO = bookingDAO;
        this.settlementDAO = settlementDAO;
        this.ownerProfileDAO = ownerProfileDAO;
    }

    public OwnerDashboardDTO getDashboard(Integer ownerId, Integer tenantId) {
        log.info("OwnerDashboardService.getDashboard() - ownerId={}, tenantId={}", ownerId, tenantId);

        ownerProfileDAO.findById(ownerId)
                .filter(o -> tenantId.equals(o.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("Owner non trovato o non appartiene al tenant: id=" + ownerId));

        List<Booking> bookings = bookingDAO.findByOwnerId(ownerId);
        List<Settlement> settlements = settlementDAO.findByTenantIdAndOwnerId(tenantId, ownerId);

        BigDecimal ricaviTotali = bookings.stream()
                .map(Booking::getGrossAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRitenute = bookings.stream()
                .map(Booking::getWithholdingAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLiquidato = settlements.stream()
                .filter(s -> "paid".equals(s.getStato()))
                .map(Settlement::getNetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth currentMonth = YearMonth.now();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ITALIAN);
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        List<MensileDTO> ricaviMensili = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            List<Booking> monthBookings = bookings.stream()
                    .filter(b -> YearMonth.from(b.getCheckinDate()).equals(month))
                    .collect(Collectors.toList());

            String meseLabel = month.format(monthFormatter);
            meseLabel = meseLabel.substring(0, 1).toUpperCase() + meseLabel.substring(1);

            BigDecimal ricaviOw = monthBookings.stream()
                    .map(Booking::getOwnerNetAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ritenute = monthBookings.stream()
                    .map(Booking::getWithholdingAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ricaviMensili.add(MensileDTO.builder()
                    .mese(meseLabel)
                    .meseKey(month.format(keyFormatter))
                    .ricaviPm(BigDecimal.ZERO)
                    .ricaviOw(ricaviOw)
                    .commissioni(BigDecimal.ZERO)
                    .ritenute(ritenute)
                    .build());
        }

        return OwnerDashboardDTO.builder()
                .ricaviTotali(ricaviTotali)
                .prenotazioniCount(bookings.size())
                .totalRitenute(totalRitenute)
                .totalLiquidato(totalLiquidato)
                .ricaviMensili(ricaviMensili)
                .build();
    }
}

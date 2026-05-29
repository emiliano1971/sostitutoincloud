package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dto.dashboard.DashboardDTO;
import it.gavia.sostitutoincloud.dto.dashboard.MensileDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.Settlement;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Log4j2
public class DashboardService {

    private final BookingDAO bookingDAO;
    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final F24RecordDAO f24RecordDAO;
    private final SettlementDAO settlementDAO;
    private final StatoPrenotazioneDAO statoPrenotazioneDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;

    public DashboardService(BookingDAO bookingDAO,
                            FiscalDocumentDAO fiscalDocumentDAO,
                            F24RecordDAO f24RecordDAO,
                            SettlementDAO settlementDAO,
                            StatoPrenotazioneDAO statoPrenotazioneDAO,
                            StatoDocumentoDAO statoDocumentoDAO) {
        this.bookingDAO = bookingDAO;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.f24RecordDAO = f24RecordDAO;
        this.settlementDAO = settlementDAO;
        this.statoPrenotazioneDAO = statoPrenotazioneDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
    }

    public DashboardDTO getDashboard(Integer tenantId) {
        log.info("DashboardService.getDashboard() - tenantId={}", tenantId);

        List<Booking> allBookings = bookingDAO.findByTenantId(tenantId);

        Map<String, Integer> statoPrenotazioneMap = statoPrenotazioneDAO.findAll().stream()
                .collect(Collectors.toMap(s -> s.getCodice(), s -> s.getId()));

        Map<String, Integer> statoDocumentoMap = statoDocumentoDAO.findAll().stream()
                .collect(Collectors.toMap(s -> s.getCodice(), s -> s.getId()));

        Set<Integer> statiEsclusiIds = Stream.of("doc_issued", "settled", "cancelled")
                .map(statoPrenotazioneMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LocalDate oggi = LocalDate.now();
        LocalDate penaleThreshold = oggi.minusDays(12);

        int daCompletare = (int) allBookings.stream()
                .filter(b -> !b.getCheckoutDate().isAfter(oggi))
                .filter(b -> !statiEsclusiIds.contains(b.getFkStatoPrenotazioneId()))
                .count();

        int inPenale = (int) allBookings.stream()
                .filter(b -> !b.getCheckoutDate().isAfter(penaleThreshold))
                .filter(b -> !statiEsclusiIds.contains(b.getFkStatoPrenotazioneId()))
                .count();

        Set<Integer> statiDocPendingIds = Stream.of("draft", "ready")
                .map(statoDocumentoMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<FiscalDocument> allDocs = fiscalDocumentDAO.findByTenantId(tenantId);
        int documentiPending = (int) allDocs.stream()
                .filter(d -> statiDocPendingIds.contains(d.getFkStatoDocumentoId()))
                .count();

        List<F24Record> allF24 = f24RecordDAO.findByTenantId(tenantId);
        int f24DaGenerare = (int) allF24.stream()
                .filter(f -> "draft".equals(f.getStato()) || "ready".equals(f.getStato()))
                .count();

        List<Settlement> allSettlements = settlementDAO.findByTenantId(tenantId);
        int liquidazioniPending = (int) allSettlements.stream()
                .filter(s -> "pending".equals(s.getStato()) || "calculated".equals(s.getStato()))
                .count();

        YearMonth currentMonth = YearMonth.now();
        YearMonth prevMonth = currentMonth.minusMonths(1);

        BigDecimal ricaviMeseCorrente = allBookings.stream()
                .filter(b -> YearMonth.from(b.getCheckinDate()).equals(currentMonth))
                .map(Booking::getGrossAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ricaviMesePrecedente = allBookings.stream()
                .filter(b -> YearMonth.from(b.getCheckinDate()).equals(prevMonth))
                .map(Booking::getGrossAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ITALIAN);
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        List<MensileDTO> ricaviUltimi12Mesi = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            List<Booking> monthBookings = allBookings.stream()
                    .filter(b -> YearMonth.from(b.getCheckinDate()).equals(month))
                    .collect(Collectors.toList());

            String meseLabel = month.format(monthFormatter);
            meseLabel = meseLabel.substring(0, 1).toUpperCase() + meseLabel.substring(1);

            BigDecimal ricaviPm = monthBookings.stream()
                    .map(Booking::getPmFeeAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ricaviOw = monthBookings.stream()
                    .map(Booking::getOwnerNetAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal commissioni = monthBookings.stream()
                    .map(Booking::getOtaCommissionAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ritenute = monthBookings.stream()
                    .map(Booking::getWithholdingAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ricaviUltimi12Mesi.add(MensileDTO.builder()
                    .mese(meseLabel)
                    .meseKey(month.format(keyFormatter))
                    .ricaviPm(ricaviPm)
                    .ricaviOw(ricaviOw)
                    .commissioni(commissioni)
                    .ritenute(ritenute)
                    .build());
        }

        return DashboardDTO.builder()
                .bookingsDaCompletare(daCompletare)
                .bookingsInPenale(inPenale)
                .documentiPending(documentiPending)
                .f24DaGenerare(f24DaGenerare)
                .liquidazioniPending(liquidazioniPending)
                .ricaviMeseCorrente(ricaviMeseCorrente)
                .ricaviMesePrecedente(ricaviMesePrecedente)
                .ricaviUltimi12Mesi(ricaviUltimi12Mesi)
                .build();
    }
}

package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dto.touristtax.GuestTaxDTO;
import it.gavia.sostitutoincloud.dto.touristtax.RegolaTassaSoggiornoDetailDTO;
import it.gavia.sostitutoincloud.dto.touristtax.TassaFasciaEtaDTO;
import it.gavia.sostitutoincloud.dto.touristtax.TassaStagioneDTO;
import it.gavia.sostitutoincloud.dto.touristtax.TassaZonaDTO;
import it.gavia.sostitutoincloud.dto.touristtax.TouristTaxCalculationDTO;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Replica esatta della funzione calculateTouristTax() di
 * frontend/src/data/tourist-tax.ts.
 * Fonte di verità per il calcolo della tassa di soggiorno usato nelle prenotazioni reali.
 */
@Service
@Log4j2
public class TouristTaxCalculatorService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public TouristTaxCalculationDTO calculate(RegolaTassaSoggiornoDetailDTO regola,
                                              Integer nights,
                                              LocalDate checkinDate,
                                              String zona,
                                              List<Integer> guestAges) {
        log.debug("TouristTaxCalculatorService.calculate() - regola={} nights={} zona={} ospiti={}",
                regola.getId(), nights, zona, guestAges != null ? guestAges.size() : 0);

        int reqNights = (nights != null && nights >= 1) ? nights : 1;
        int effectiveNights = (regola.getMaxNotti() != null && regola.getMaxNotti() > 0)
                ? Math.min(reqNights, regola.getMaxNotti())
                : reqNights;

        // Riduzione di stagione in base alla data di check-in
        int month = checkinDate.getMonthValue();
        int day = checkinDate.getDayOfMonth();
        int seasonReduction = findSeasonReduction(regola.getStagioni(), month, day);

        // Riduzione di zona (solo se specificata)
        int zoneReduction = findZoneReduction(regola.getZone(), zona);

        BigDecimal baseRate = regola.getImportoPerNotte() != null ? regola.getImportoPerNotte() : BigDecimal.ZERO;
        BigDecimal maxPerPerson = regola.getMaxAmountPerPerson();

        List<GuestTaxDTO> perPerson = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        List<Integer> ages = guestAges != null ? guestAges : List.of();
        for (Integer ageRaw : ages) {
            int age = ageRaw != null ? ageRaw : 30;
            int ageReduction = findAgeReduction(regola.getFascieEta(), age);

            if (ageReduction >= 100) {
                perPerson.add(GuestTaxDTO.builder()
                        .age(age).nightsCharged(0)
                        .ratePerNight(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .total(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .esente(true)
                        .build());
                continue;
            }

            // Riduzioni cumulative applicate in sequenza
            BigDecimal rate = baseRate;
            rate = applyReduction(rate, ageReduction);
            rate = applyReduction(rate, seasonReduction);
            rate = applyReduction(rate, zoneReduction);
            rate = rate.setScale(2, RoundingMode.HALF_UP);

            BigDecimal guestTotal = rate.multiply(BigDecimal.valueOf(effectiveNights));
            if (maxPerPerson != null) {
                guestTotal = guestTotal.min(maxPerPerson);
            }
            guestTotal = guestTotal.setScale(2, RoundingMode.HALF_UP);

            perPerson.add(GuestTaxDTO.builder()
                    .age(age)
                    .nightsCharged(effectiveNights)
                    .ratePerNight(rate)
                    .total(guestTotal)
                    .esente(false)
                    .build());
            total = total.add(guestTotal);
        }

        return TouristTaxCalculationDTO.builder()
                .total(total.setScale(2, RoundingMode.HALF_UP))
                .perPerson(perPerson)
                .build();
    }

    private BigDecimal applyReduction(BigDecimal rate, int reductionPct) {
        if (reductionPct > 0) {
            return rate.multiply(BigDecimal.valueOf(100 - reductionPct)).divide(HUNDRED);
        }
        return rate;
    }

    private int findAgeReduction(List<TassaFasciaEtaDTO> fascie, int age) {
        if (fascie == null) return 0;
        return fascie.stream()
                .filter(b -> b.getMinAge() != null && b.getMaxAge() != null
                        && age >= b.getMinAge() && age <= b.getMaxAge())
                .findFirst()
                .map(b -> b.getReductionPct() != null ? b.getReductionPct() : 0)
                .orElse(0);
    }

    private int findSeasonReduction(List<TassaStagioneDTO> stagioni, int month, int day) {
        if (stagioni == null) return 0;
        for (TassaStagioneDTO s : stagioni) {
            if (matchesSeason(s, month, day)) {
                return s.getReductionPct() != null ? s.getReductionPct() : 0;
            }
        }
        return 0;
    }

    private boolean matchesSeason(TassaStagioneDTO s, int month, int day) {
        int sm = s.getStartMonth(), sd = s.getStartDay();
        int em = s.getEndMonth(), ed = s.getEndDay();
        boolean afterStart = month > sm || (month == sm && day >= sd);
        boolean beforeEnd = month < em || (month == em && day <= ed);
        if (sm <= em) {
            return afterStart && beforeEnd;          // intervallo nello stesso anno
        }
        return afterStart || beforeEnd;              // intervallo a cavallo d'anno
    }

    private int findZoneReduction(List<TassaZonaDTO> zone, String zona) {
        if (zona == null || zona.isBlank() || zone == null) return 0;
        return zone.stream()
                .filter(z -> zona.equals(z.getLabel()))
                .findFirst()
                .map(z -> z.getReductionPct() != null ? z.getReductionPct() : 0)
                .orElse(0);
    }
}

package it.gavia.sostitutoincloud.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDTO {

    private Integer bookingsDaCompletare;
    private Integer bookingsInPenale;
    private Integer documentiPending;
    private Integer f24DaGenerare;
    private Integer liquidazioniPending;
    private BigDecimal ricaviMeseCorrente;
    private BigDecimal ricaviMesePrecedente;
    private List<MensileDTO> ricaviUltimi12Mesi;
}

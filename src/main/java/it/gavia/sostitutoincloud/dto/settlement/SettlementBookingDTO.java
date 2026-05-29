package it.gavia.sostitutoincloud.dto.settlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementBookingDTO {

    private Integer bookingId;
    private String externalBookingId;
    private String propertyName;
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private BigDecimal grossAmount;
    private BigDecimal ownerNetAmount;
    private BigDecimal withholdingAmount;
}

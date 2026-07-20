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
    private BigDecimal otaCommissionAmount;   // commissione OTA
    private BigDecimal cleaningAmount;        // pulizie
    private BigDecimal pmFeeAmount;           // provvigione property manager
    private BigDecimal ivaAmount;             // IVA scorporata dalla provvigione PM (pmFee * 0.22 / 1.22)
    private BigDecimal ownerNetAmount;        // canone al proprietario
    private BigDecimal withholdingAmount;
    private Integer bolloCents;   // bollo in centesimi (0 o 200) dai fiscal_document "ricevuta" del booking
}

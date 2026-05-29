package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitEconomicoDTO {

    private BigDecimal grossAmount;
    private BigDecimal otaCommissionAmount;
    private BigDecimal cleaningAmount;
    private BigDecimal pmFeeAmount;
    private BigDecimal ownerNetAmount;
    private BigDecimal withholdingAmount;
    private BigDecimal liquidazioneOwner;
    private BigDecimal touristTaxAmount;
    private Boolean touristTaxIncludedInGross;
}

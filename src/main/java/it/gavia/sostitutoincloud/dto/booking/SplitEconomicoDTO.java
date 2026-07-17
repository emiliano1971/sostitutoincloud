package it.gavia.sostitutoincloud.dto.booking;

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
public class SplitEconomicoDTO {

    private BigDecimal grossAmount;
    private BigDecimal otaCommissionAmount;
    private BigDecimal cleaningAmount;
    private BigDecimal pmFeeAmount;
    private BigDecimal ownerNetAmount;
    private BigDecimal withholdingAmount;
    private BigDecimal aliquotaRitenuta;
    private BigDecimal liquidazioneOwner;
    private BigDecimal touristTaxAmount;
    private Boolean touristTaxIncludedInGross;
    private BigDecimal imponibileFatturaPm;  // base imponibile scorporata (lordo / 1.22)
    private BigDecimal ivaScorporataPm;      // IVA scorporata sui servizi PM (informativa, non detrazione)
    private BigDecimal fatturaPmTotale;      // totale lordo della fattura PM
    private List<String> warnings;
    private Boolean calcoloCompleto;
}

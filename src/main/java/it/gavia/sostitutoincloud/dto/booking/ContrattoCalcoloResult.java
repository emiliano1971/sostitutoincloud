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
public class ContrattoCalcoloResult {

    private BigDecimal grossAmount;
    private BigDecimal otaCommissionAmount;
    private BigDecimal cleaningAmount;
    private BigDecimal pmFeeAmount;
    private BigDecimal imponibilePm;        // lordo servizi = ota + cleaning + pmFee (IVA inclusa)
    private BigDecimal imponibileFatturaPm; // lordo servizi / (1 + aliquotaIva) — base imponibile scorporata
    private BigDecimal ivaScorporata;       // lordo servizi - imponibileFatturaPm — IVA scorporata (informativa)
    private BigDecimal fatturaPmTotale;     // = lordo servizi (totale lordo della fattura PM)
    private BigDecimal ownerNetAmount;      // gross - fatturaPmTotale
    private BigDecimal withholdingAmount;   // ownerNet * aliquotaRitenuta
    private BigDecimal aliquotaRitenuta;    // % ritenuta applicata (21.00 o 26.00)
    private BigDecimal liquidazioneOwner;   // ownerNet - withholding
    private String regimeFiscalePm;         // RF01 o RF19
    private Boolean calcoloCompleto;        // true se trovate tutte le regole
    private List<String> warnings;          // messaggi se regole mancanti
}

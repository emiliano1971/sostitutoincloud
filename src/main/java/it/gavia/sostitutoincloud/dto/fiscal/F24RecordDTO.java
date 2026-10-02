package it.gavia.sostitutoincloud.dto.fiscal;

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
public class F24RecordDTO {

    private Integer id;
    private Integer periodoMese;
    private Integer periodoAnno;
    private BigDecimal totalAmount;
    private Integer withholdingsCount;
    private String stato;
    private LocalDate deadlineDate;
    private LocalDate paymentDate;
    private String codiceTributo;
    // Crediti d'imposta da NDC compensati (migration 027)
    private BigDecimal importoCredito;
    private String codiceTributoCreditoImposta;
    private Integer annoCredito;
    private BigDecimal saldoNetto;
}

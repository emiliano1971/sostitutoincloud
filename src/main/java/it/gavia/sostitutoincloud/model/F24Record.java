package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class F24Record {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkCodiceTributoId;
    /** Formato YYYY-MM */
    private String period;
    private BigDecimal totalAmount;
    private Integer withholdingsCount;
    /** Enum PostgreSQL f24_status → String */
    private String stato;
    private LocalDate deadlineDate;
    private LocalDate paymentDate;
    private Integer periodoMese;
    private Integer periodoAnno;
    private Integer referenceYear;
    // Crediti d'imposta da NDC compensati (migration 027): riga 2 del modello F24
    private BigDecimal importoCredito;
    private String codiceTributoCreditoImposta;
    private Integer annoCredito;
    /** total_amount - importo_credito: importo effettivamente da versare. */
    private BigDecimal saldoNetto;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

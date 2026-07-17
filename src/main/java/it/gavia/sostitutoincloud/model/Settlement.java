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
public class Settlement {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkOwnerId;
    /** Formato YYYY-MM */
    private String period;
    private BigDecimal totalAmount;
    private BigDecimal withholdingAmount;
    private BigDecimal netAmount;
    /** Enum PostgreSQL settlement_status → String */
    private String stato;
    private LocalDate paymentDate;
    private Integer periodoMese;
    private Integer periodoAnno;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

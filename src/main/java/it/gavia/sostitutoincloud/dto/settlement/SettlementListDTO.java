package it.gavia.sostitutoincloud.dto.settlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementListDTO {

    private Integer id;
    private String ownerName;
    private String period;
    private BigDecimal totalAmount;
    private BigDecimal withholdingAmount;
    private BigDecimal netAmount;
    private Integer bookingsCount;
    private String stato;
    private LocalDate paymentDate;
    private LocalDateTime createdAt;
}

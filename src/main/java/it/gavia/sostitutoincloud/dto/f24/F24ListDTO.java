package it.gavia.sostitutoincloud.dto.f24;

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
public class F24ListDTO {

    private Integer id;
    private String period;
    private String codiceTributo;
    private BigDecimal totalAmount;
    private Integer withholdingsCount;
    private String stato;
    private LocalDate deadlineDate;
    private LocalDate paymentDate;
    private LocalDateTime createdAt;
}

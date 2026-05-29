package it.gavia.sostitutoincloud.dto.cu;

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
public class CuListDTO {

    private Integer id;
    private String ownerName;
    private Integer taxYear;
    private BigDecimal totalCompensi;
    private BigDecimal totalRitenute;
    private String stato;
    private LocalDate generatedAt;
    private LocalDateTime createdAt;
}

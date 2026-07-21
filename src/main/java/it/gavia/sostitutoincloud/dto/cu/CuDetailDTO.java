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
public class CuDetailDTO {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkOwnerId;
    private String ownerName;
    private String ownerTaxCode;
    private String ownerIban;
    private Integer taxYear;
    private BigDecimal totalCompensi;
    private BigDecimal totalImponibile;
    private BigDecimal totalRitenute;
    private String stato;
    private LocalDate generatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

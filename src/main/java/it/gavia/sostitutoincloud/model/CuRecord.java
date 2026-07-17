package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class CuRecord {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkOwnerId;
    private Integer taxYear;
    private BigDecimal totalCompensi;
    private BigDecimal totalRitenute;
    private BigDecimal totalImponibile;
    /** Enum PostgreSQL cu_status → String */
    private String stato;
    private LocalDateTime generatedAt;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

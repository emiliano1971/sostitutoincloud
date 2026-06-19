package it.gavia.sostitutoincloud.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSummaryDTO {

    private Integer id;
    private String displayName;
    private String legalName;
    private String stato;
    private Integer propertiesCount;
    private Integer ownersCount;
    private Integer bookingsCount;
    private LocalDate createdAt;
}

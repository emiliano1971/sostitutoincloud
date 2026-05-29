package it.gavia.sostitutoincloud.dto.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantListDTO {

    private Integer id;
    private String legalName;
    private String displayName;
    private String taxCode;
    private String vatNumber;
    private String stato;
    private String administrativeEmail;
    private String phone;
    private String legalAddress;
    private LocalDate activatedAt;
    private LocalDateTime createdAt;
    private Integer propertiesCount;
    private Integer ownersCount;
    private Integer bookingsCount;
}

package it.gavia.sostitutoincloud.dto.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerDetailDTO {

    private Integer id;
    private Integer fkTenantId;
    private String ownerType;
    private String firstName;
    private String lastName;
    private String legalName;
    private String taxCode;
    private String vatNumber;
    private String fiscalRegime;
    private String email;
    private String phone;
    private String iban;
    private Boolean attivo;
    private Integer propertiesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer bookingsCount;
    private BigDecimal totalGrossAmount;
    private BigDecimal totalOwnerNet;
    private Integer settlementsCount;
}

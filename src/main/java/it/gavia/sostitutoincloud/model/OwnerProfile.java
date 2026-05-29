package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class OwnerProfile {

    private Integer id;
    private Integer fkTenantId;
    private String ownerType;
    private String firstName;
    private String lastName;
    private String legalName;
    private String taxCode;
    private String vatNumber;
    private Integer fkRegimeFiscaleId;
    private String email;
    private String phone;
    private String iban;
    private Boolean attivo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

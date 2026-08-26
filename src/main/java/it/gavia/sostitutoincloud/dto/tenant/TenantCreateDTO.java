package it.gavia.sostitutoincloud.dto.tenant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantCreateDTO {

    private String legalName;
    private String displayName;
    private String taxCode;
    private String vatNumber;
    private String administrativeEmail;
    private String pec;
    private String phone;
    private String legalAddress;
    // Sede legale scomposta per l'XML SDI
    private String cap;
    private String comune;
    private String provincia;
}

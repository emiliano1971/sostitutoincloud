package it.gavia.sostitutoincloud.dto.owner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OwnerUpdateDTO {

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
}

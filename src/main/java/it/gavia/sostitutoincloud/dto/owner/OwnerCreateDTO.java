package it.gavia.sostitutoincloud.dto.owner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OwnerCreateDTO {

    // obbligatori
    private String ownerType;
    private String firstName;
    private String lastName;
    private String taxCode;
    private String email;

    // opzionali
    private String legalName;
    private String vatNumber;
    private Integer fkRegimeFiscaleId;
    private String phone;
    private String iban;
}

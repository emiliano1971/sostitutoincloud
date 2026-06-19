package it.gavia.sostitutoincloud.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UtenteCreateDTO {

    private String email;       // obbligatorio
    private String firstName;   // obbligatorio
    private String lastName;    // obbligatorio
    private String password;    // obbligatorio, min 8 char
    private String ruolo;       // obbligatorio: solo "pm_user" o "owner_user"
    private Integer fkOwnerId;  // obbligatorio se ruolo = "owner_user", null se "pm_user"
}

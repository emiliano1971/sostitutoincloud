package it.gavia.sostitutoincloud.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {

    private String token;
    private UserMeDTO user;
    /** Duplicato di user.mustChangePassword: comodo per il frontend che legge la
     *  risposta di login senza scendere nell'oggetto user. */
    private Boolean mustChangePassword;
}

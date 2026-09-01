package it.gavia.sostitutoincloud.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di reset password. La validazione è nel PasswordResetService:
 * spring-boot-starter-validation non è tra le dipendenze del progetto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequestDTO {

    /** Obbligatorio. */
    private String email;
}

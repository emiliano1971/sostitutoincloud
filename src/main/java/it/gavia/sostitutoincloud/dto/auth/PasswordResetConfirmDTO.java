package it.gavia.sostitutoincloud.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Conferma del reset con il token ricevuto per email.
 * Validazione (token presente, password min 8 caratteri) nel PasswordResetService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetConfirmDTO {

    /** Obbligatorio — 64 caratteri esadecimali. */
    private String token;

    /** Obbligatorio — minimo 8 caratteri. */
    private String newPassword;
}

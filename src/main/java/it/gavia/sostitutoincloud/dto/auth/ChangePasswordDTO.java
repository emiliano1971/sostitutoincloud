package it.gavia.sostitutoincloud.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cambio password per utente già autenticato.
 * Validazione (password corrente corretta, nuova min 8 caratteri) nel PasswordResetService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordDTO {

    /** Obbligatorio. */
    private String currentPassword;

    /** Obbligatorio — minimo 8 caratteri. */
    private String newPassword;
}

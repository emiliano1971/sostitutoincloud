package it.gavia.sostitutoincloud.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cambio password al primo accesso: non c'è currentPassword perché l'utente non
 * conosce quella temporanea assegnata dall'amministratore. L'endpoint è utilizzabile
 * SOLO da utenti con must_change_password = true (verifica lato server).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForceChangePasswordDTO {
    private String newPassword;
}

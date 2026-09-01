package it.gavia.sostitutoincloud.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modifica dei dati anagrafici di un utente esistente.
 * Lo stato attivo/inattivo NON passa da qui: si cambia con
 * PATCH /api/users/{id}/status (UtenteDAO.updateStatus()).
 * Validazione nel UserManagementService: il progetto non ha
 * spring-boot-starter-validation tra le dipendenze.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UtenteUpdateDTO {

    private String email;       // obbligatorio, univoco
    private String firstName;   // obbligatorio
    private String lastName;    // obbligatorio
}

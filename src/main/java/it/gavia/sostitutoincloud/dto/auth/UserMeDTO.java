package it.gavia.sostitutoincloud.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMeDTO {

    private Integer id;
    private String email;
    private String ruolo;
    private Integer fkTenantId;
    private Integer fkOwnerId;
    private String firstName;
    private String lastName;
    private Boolean attivo;
    /** true = l'utente deve cambiare password prima di poter usare l'applicazione.
     *  Esposto anche qui, e non solo nella risposta di login, perché al reload della
     *  pagina il frontend ricostruisce la sessione da /auth/me: senza il flag qui
     *  basterebbe un F5 per aggirare il cambio forzato. */
    private Boolean mustChangePassword;
}

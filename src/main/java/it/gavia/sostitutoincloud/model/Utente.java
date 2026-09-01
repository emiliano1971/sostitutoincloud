package it.gavia.sostitutoincloud.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class Utente {

    private Integer id;
    private Integer fkTenantId;
    private String email;
    private String firstName;
    private String lastName;
    /**
     * Mai popolato da UtenteRowMapper: l'hash non entra nel model.
     * Per verificare la password corrente usare UtenteDAO.findPasswordHashById().
     */
    @JsonIgnore
    private String passwordHash;
    private String ruolo;
    private Boolean attivo;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer fkOwnerId;

    /**
     * Token di reset password (64 hex). @JsonIgnore obbligatorio: TestController
     * (@Profile local, path /api/public/test) serializza Utente grezzo su un endpoint
     * non autenticato — senza questo il token sarebbe leggibile da chiunque.
     */
    @JsonIgnore
    private String resetToken;

    @JsonIgnore
    private LocalDateTime resetTokenExpiresAt;

    private Boolean mustChangePassword;
}

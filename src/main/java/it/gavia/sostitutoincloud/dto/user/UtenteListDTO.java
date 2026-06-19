package it.gavia.sostitutoincloud.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtenteListDTO {

    private Integer id;
    private String email;
    private String firstName;
    private String lastName;
    private String ruolo;
    private Boolean attivo;
    private String ownerName;   // null se non è owner_user
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
}

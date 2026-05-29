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
}

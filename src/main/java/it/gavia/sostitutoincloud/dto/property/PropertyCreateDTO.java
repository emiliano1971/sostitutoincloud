package it.gavia.sostitutoincloud.dto.property;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyCreateDTO {

    private String displayName;
    private String internalCode;
    private Integer fkTipoImmobileId;
    private String propertyType;
    private String city;
    private String address;
    private String region;
    private String cinCode;
    private Integer fkOwnerId;
    // null = lascia decidere al backend (in creazione: primo immobile attivo dell'owner).
    // Valorizzato = scelta esplicita del PM dal toggle in creazione/modifica.
    private Boolean primoImmobile;
    private List<OtaCodeDTO> otaCodes;
}

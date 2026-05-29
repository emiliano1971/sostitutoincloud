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
    private String city;
    private String address;
    private String region;
    private String cinCode;
    private Integer fkOwnerId;
    private List<OtaCodeDTO> otaCodes;
}

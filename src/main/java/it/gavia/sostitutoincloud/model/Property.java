package it.gavia.sostitutoincloud.model;

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
public class Property {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkOwnerId;
    private Integer fkPmUserId;
    private Integer fkTipoImmobileId;
    private String internalCode;
    private String displayName;
    private String address;
    private String city;
    private String region;
    private String cinCode;
    private Boolean attivo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

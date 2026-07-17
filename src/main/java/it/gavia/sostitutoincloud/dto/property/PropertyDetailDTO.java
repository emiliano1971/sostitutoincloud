package it.gavia.sostitutoincloud.dto.property;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyDetailDTO {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkOwnerId;
    private Integer fkPmUserId;
    private String internalCode;
    private String displayName;
    private String address;
    private String city;
    private String region;
    private String propertyType;
    private String cinCode;
    private Boolean attivo;
    private Boolean primoImmobile;
    private String ownerName;
    private Integer listingsCount;
    private Integer bookingsCount;
    private List<OtaCodeDTO> otaCodes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

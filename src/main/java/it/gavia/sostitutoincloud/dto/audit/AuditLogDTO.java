package it.gavia.sostitutoincloud.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkUtenteId;
    private String userEmail;
    private String action;
    private String entityType;
    private Integer entityId;
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;
}

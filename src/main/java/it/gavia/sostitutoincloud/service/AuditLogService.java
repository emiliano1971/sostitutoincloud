package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.AuditLogDAO;
import it.gavia.sostitutoincloud.dto.audit.AuditLogDTO;
import it.gavia.sostitutoincloud.model.AuditLog;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Log4j2
public class AuditLogService {

    private final AuditLogDAO auditLogDAO;

    public AuditLogService(AuditLogDAO auditLogDAO) {
        this.auditLogDAO = auditLogDAO;
    }

    public List<AuditLogDTO> findByTenantId(Integer tenantId, String q, String action,
                                              Integer page, Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        List<AuditLogDTO> result = auditLogDAO.findByTenantIdOrderByCreatedAtDesc(tenantId, 1000).stream()
                .filter(a -> {
                    if (q != null && !q.isBlank()) {
                        String ql = q.toLowerCase();
                        boolean matchDetails = a.getDetails() != null
                                && a.getDetails().toLowerCase().contains(ql);
                        boolean matchEmail = a.getUserEmail() != null
                                && a.getUserEmail().toLowerCase().contains(ql);
                        return matchDetails || matchEmail;
                    }
                    return true;
                })
                .filter(a -> {
                    if (action != null && !action.isBlank()) {
                        return a.getAction() != null && a.getAction().startsWith(action);
                    }
                    return true;
                })
                .skip((long) pageNum * pageSize)
                .limit(pageSize)
                .map(a -> AuditLogDTO.builder()
                        .id(a.getId())
                        .fkTenantId(a.getFkTenantId())
                        .fkUtenteId(a.getFkUtenteId())
                        .userEmail(a.getUserEmail())
                        .action(a.getAction())
                        .entityType(a.getEntityType())
                        .entityId(a.getEntityId())
                        .details(a.getDetails())
                        .ipAddress(a.getIpAddress())
                        .createdAt(a.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        log.info("AuditLogService.findByTenantId() - tenantId={}, risultati={}", tenantId, result.size());
        return result;
    }
}

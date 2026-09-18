package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.AuditLogDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dto.audit.AuditLogDTO;
import it.gavia.sostitutoincloud.model.AuditLog;
import it.gavia.sostitutoincloud.model.Tenant;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Log4j2
public class AuditLogService {

    private final AuditLogDAO auditLogDAO;
    private final TenantDAO tenantDAO;

    public AuditLogService(AuditLogDAO auditLogDAO, TenantDAO tenantDAO) {
        this.auditLogDAO = auditLogDAO;
        this.tenantDAO = tenantDAO;
    }

    /**
     * tenantId null = vista globale del super_admin su tutti i tenant. La decisione sul
     * ruolo sta nel controller: qui il null è già il risultato di quella scelta, non un
     * dato anagrafico da interpretare.
     */
    public List<AuditLogDTO> findByTenantId(Integer tenantId, String q, String action,
                                              String entity, Integer page, Integer size) {
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;
        boolean vistaGlobale = tenantId == null;

        // Nomi dei tenant caricati una sola volta: la vista globale mescola log di tenant
        // diversi e senza questa mappa servirebbe una query per riga.
        Map<Integer, String> nomiTenant = vistaGlobale
                ? tenantDAO.findAll().stream().collect(Collectors.toMap(
                        Tenant::getId,
                        t -> t.getDisplayName() != null && !t.getDisplayName().isBlank()
                                ? t.getDisplayName() : t.getLegalName(),
                        (a, b) -> a))
                : Map.of();

        List<AuditLog> righe = vistaGlobale
                ? auditLogDAO.findAllOrderByCreatedAtDesc(1000)
                : auditLogDAO.findByTenantIdOrderByCreatedAtDesc(tenantId, 1000);

        List<AuditLogDTO> result = righe.stream()
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
                .filter(a -> {
                    if (entity != null && !entity.isBlank()) {
                        return a.getEntityType() != null && a.getEntityType().equalsIgnoreCase(entity);
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
                        // Solo nella vista globale: per i log del proprio tenant sarebbe
                        // un'informazione ridondante.
                        .tenantDisplayName(vistaGlobale ? nomeTenant(a.getFkTenantId(), nomiTenant) : null)
                        .build())
                .collect(Collectors.toList());

        log.info("AuditLogService.findByTenantId() - tenantId={}, vistaGlobale={}, risultati={}",
                tenantId, vistaGlobale, result.size());
        return result;
    }

    /**
     * Etichetta del tenant per la vista globale. Gli eventi senza tenant sono azioni di
     * sistema (tipicamente del super_admin); il fallback numerico copre un tenant nel
     * frattempo cancellato, che nella mappa dei nomi non c'è più.
     */
    private String nomeTenant(Integer fkTenantId, Map<Integer, String> nomiTenant) {
        if (fkTenantId == null) return "Sistema";
        return nomiTenant.getOrDefault(fkTenantId, "Tenant " + fkTenantId);
    }
}

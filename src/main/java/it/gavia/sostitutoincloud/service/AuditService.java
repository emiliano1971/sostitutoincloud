package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.AuditLogDAO;
import it.gavia.sostitutoincloud.model.AuditLog;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class AuditService {

    /** Placeholder per le colonne NOT NULL quando il dato non è disponibile (ip_address / user_email). */
    private static final String SYSTEM = "system";

    private final AuditLogDAO auditLogDAO;

    public AuditService(AuditLogDAO auditLogDAO) {
        this.auditLogDAO = auditLogDAO;
    }

    /**
     * Registra un evento di audit. Non propaga mai eccezioni: l'audit non deve
     * bloccare il flusso principale. In caso di errore logga WARN e prosegue.
     */
    public void log(String azione, String entita, Integer entitaId, String descrizione,
                    Integer tenantId, Integer utenteId, String ipAddress) {
        try {
            String email;
            try {
                email = SecurityUtils.getCurrentUserEmail();
            } catch (Exception e) {
                email = SYSTEM;
            }
            AuditLog entry = AuditLog.builder()
                    .fkTenantId(tenantId)
                    .fkUtenteId(utenteId)
                    .userEmail(email != null && !email.isBlank() ? email : SYSTEM)
                    .action(azione)
                    .entityType(entita)
                    .entityId(entitaId)
                    .details(descrizione)
                    .ipAddress(ipAddress != null && !ipAddress.isBlank() ? ipAddress : SYSTEM)
                    .build();
            auditLogDAO.insert(entry);
            log.debug("AuditService.log() - azione={} tenantId={} entitaId={}", azione, tenantId, entitaId);
        } catch (Exception e) {
            log.warn("AuditService.log() fallito - azione={} entita={}: {}", azione, entita, e.getMessage());
        }
    }

    /**
     * Overload che ricava tenantId e utenteId dal contesto di sicurezza corrente.
     * ipAddress non disponibile in questo punto → null (verrà sostituito dal placeholder).
     */
    public void log(String azione, String entita, Integer entitaId, String descrizione) {
        Integer tenantId = null;
        Integer utenteId = null;
        try {
            tenantId = SecurityUtils.getCurrentTenantId();
        } catch (Exception e) {
            // utente non vincolato a tenant (es. super_admin) o contesto assente
        }
        try {
            utenteId = SecurityUtils.getCurrentUtenteId();
        } catch (Exception e) {
            // contesto di sicurezza assente
        }
        log(azione, entita, entitaId, descrizione, tenantId, utenteId, null);
    }
}

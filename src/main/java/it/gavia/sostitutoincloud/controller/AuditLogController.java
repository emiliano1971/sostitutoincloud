package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.audit.AuditLogDTO;
import it.gavia.sostitutoincloud.service.AuditLogService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-log")
@Log4j2
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogDTO>> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(auditLogService.findByTenantId(tenantId, q, action, page, size));
    }
}

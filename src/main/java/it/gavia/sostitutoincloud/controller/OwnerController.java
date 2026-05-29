package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.owner.OwnerDashboardDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerDetailDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerListDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerStatusUpdateDTO;
import it.gavia.sostitutoincloud.service.OwnerDashboardService;
import it.gavia.sostitutoincloud.service.OwnerService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owners")
@Log4j2
public class OwnerController {

    private final OwnerService ownerService;
    private final OwnerDashboardService ownerDashboardService;

    public OwnerController(OwnerService ownerService, OwnerDashboardService ownerDashboardService) {
        this.ownerService = ownerService;
        this.ownerDashboardService = ownerDashboardService;
    }

    @GetMapping
    public ResponseEntity<List<OwnerListDTO>> findAll(
            @RequestParam(required = false) Boolean attivo) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (attivo != null) {
            return ResponseEntity.ok(ownerService.findByTenantIdAndAttivo(tenantId, attivo));
        }
        return ResponseEntity.ok(ownerService.findByTenantId(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OwnerDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ownerService.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Owner non trovato: id=" + id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OwnerDetailDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody OwnerStatusUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        OwnerDetailDTO updated = ownerService.updateStatus(tenantId, id, dto.getAttivo());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/dashboard")
    public ResponseEntity<OwnerDashboardDTO> getDashboard(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        Integer currentOwnerId = SecurityUtils.getCurrentOwnerId();
        boolean isTenantAdmin = SecurityUtils.hasRole("tenant_admin") || SecurityUtils.hasRole("super_admin");
        if (!isTenantAdmin && !id.equals(currentOwnerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ownerDashboardService.getDashboard(id, tenantId));
    }
}

package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.owner.OwnerBulkImportResult;
import it.gavia.sostitutoincloud.dto.owner.OwnerCreateDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerDashboardDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerDetailDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerListDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerStatusUpdateDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerUpdateDTO;
import it.gavia.sostitutoincloud.service.OwnerBulkImportService;
import it.gavia.sostitutoincloud.service.OwnerDashboardService;
import it.gavia.sostitutoincloud.service.OwnerService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owners")
@Log4j2
public class OwnerController {

    private final OwnerService ownerService;
    private final OwnerDashboardService ownerDashboardService;
    private final OwnerBulkImportService ownerBulkImportService;

    public OwnerController(OwnerService ownerService,
                           OwnerDashboardService ownerDashboardService,
                           OwnerBulkImportService ownerBulkImportService) {
        this.ownerService = ownerService;
        this.ownerDashboardService = ownerDashboardService;
        this.ownerBulkImportService = ownerBulkImportService;
    }

    /**
     * Importazione massiva proprietari e immobili dal template Excel.
     * 400 file mancante/non valido (IllegalArgumentException → GlobalExceptionHandler),
     * 500 errore di lettura del file.
     */
    @PostMapping("/import-bulk")
    public ResponseEntity<?> importBulk(@RequestParam("file") MultipartFile file) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        log.info("OwnerController.importBulk() - tenantId={} utenteId={} file={}",
                tenantId, utenteId, file.getOriginalFilename());
        try {
            OwnerBulkImportResult result = ownerBulkImportService.importa(tenantId, utenteId, file);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("OwnerController.importBulk() - errore lettura file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Errore nella lettura del file: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<OwnerDetailDTO> create(@RequestBody OwnerCreateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        OwnerDetailDTO created = ownerService.create(tenantId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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

    @PutMapping("/{id}")
    public ResponseEntity<OwnerDetailDTO> update(
            @PathVariable Integer id,
            @RequestBody OwnerUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        OwnerDetailDTO updated = ownerService.update(tenantId, id, dto);
        return ResponseEntity.ok(updated);
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

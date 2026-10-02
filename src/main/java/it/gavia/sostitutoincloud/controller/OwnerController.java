package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.owner.OwnerBulkImportPreviewResult;
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

import java.util.HashSet;
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
     * Preview dell'importazione massiva: stato di ogni riga, nessuna scrittura a DB.
     * 400 file mancante/non valido (IllegalArgumentException → GlobalExceptionHandler).
     */
    @PostMapping("/import-bulk/preview")
    public ResponseEntity<?> importBulkPreview(@RequestParam("file") MultipartFile file) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("OwnerController.importBulkPreview() - tenantId={} file={}", tenantId, file.getOriginalFilename());
        try {
            OwnerBulkImportPreviewResult result = ownerBulkImportService.preview(tenantId, file);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("OwnerController.importBulkPreview() - errore lettura file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Errore nella lettura del file: " + e.getMessage()));
        }
    }

    /**
     * Importazione massiva proprietari e immobili dal template Excel.
     * Il file va ricaricato insieme ai numeri di riga scelti nella preview
     * (multipart: file + righe ripetuto); righe assente o vuoto = tutte le righe valide.
     * 400 file mancante/non valido (IllegalArgumentException → GlobalExceptionHandler),
     * 500 errore di lettura del file.
     */
    @PostMapping("/import-bulk")
    public ResponseEntity<?> importBulk(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "righe", required = false) List<Integer> righe) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        log.info("OwnerController.importBulk() - tenantId={} utenteId={} file={} righe={}",
                tenantId, utenteId, file.getOriginalFilename(), righe);
        try {
            OwnerBulkImportResult result = ownerBulkImportService.importa(tenantId, utenteId, file,
                    righe != null ? new HashSet<>(righe) : null);
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

    /** 204 eliminato; 400 prenotazioni o dati collegati; 404 non trovato. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> elimina(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("OwnerController.elimina() - tenantId={} ownerId={}", tenantId, id);
        try {
            ownerService.eliminaProprietario(tenantId, id);
            return ResponseEntity.noContent().build();
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("OwnerController.elimina() - 400: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
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

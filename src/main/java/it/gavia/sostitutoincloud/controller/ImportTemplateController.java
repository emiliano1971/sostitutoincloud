package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.importing.ImportTemplateDTO;
import it.gavia.sostitutoincloud.dto.importing.ImportTemplateSaveDTO;
import it.gavia.sostitutoincloud.service.ImportTemplateService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/import-templates")
@Log4j2
public class ImportTemplateController {

    private final ImportTemplateService importTemplateService;

    public ImportTemplateController(ImportTemplateService importTemplateService) {
        this.importTemplateService = importTemplateService;
    }

    @GetMapping
    public ResponseEntity<List<ImportTemplateDTO>> findAll() {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("ImportTemplateController.findAll() - tenantId={}", tenantId);
        return ResponseEntity.ok(importTemplateService.findByTenant(tenantId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ImportTemplateSaveDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            dto.setId(null); // POST è sempre insert
            return ResponseEntity.ok(importTemplateService.save(tenantId, dto));
        } catch (IllegalArgumentException e) {
            log.warn("ImportTemplateController.create() - errore: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody ImportTemplateSaveDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            dto.setId(id);
            return ResponseEntity.ok(importTemplateService.save(tenantId, dto));
        } catch (IllegalArgumentException e) {
            log.warn("ImportTemplateController.update() - errore: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            importTemplateService.delete(tenantId, id);
            return ResponseEntity.ok(Map.of("message", "Template eliminato"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }
}

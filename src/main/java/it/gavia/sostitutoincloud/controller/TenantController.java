package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.tenant.TenantCreateDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantDetailDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantListDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantStatusUpdateDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantUpdateDTO;
import it.gavia.sostitutoincloud.service.TenantService;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tenants")
@Log4j2
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantDetailDTO> create(@RequestBody TenantCreateDTO dto) {
        TenantDetailDTO created = tenantService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<TenantListDTO>> findAll() {
        return ResponseEntity.ok(tenantService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantDetailDTO> findById(@PathVariable Integer id) {
        return tenantService.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Tenant non trovato: id=" + id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantDetailDTO> update(
            @PathVariable Integer id,
            @RequestBody TenantUpdateDTO dto) {
        TenantDetailDTO updated = tenantService.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TenantDetailDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody TenantStatusUpdateDTO dto) {
        TenantDetailDTO updated = tenantService.updateStatus(id, dto.getStato());
        return ResponseEntity.ok(updated);
    }
}

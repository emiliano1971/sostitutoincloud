package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.f24.F24DetailDTO;
import it.gavia.sostitutoincloud.dto.f24.F24ListDTO;
import it.gavia.sostitutoincloud.dto.f24.F24StatusUpdateDTO;
import it.gavia.sostitutoincloud.service.F24Service;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/f24")
@Log4j2
public class F24Controller {

    private final F24Service f24Service;

    public F24Controller(F24Service f24Service) {
        this.f24Service = f24Service;
    }

    @GetMapping
    public ResponseEntity<List<F24ListDTO>> findAll() {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(f24Service.findByTenantId(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<F24DetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return f24Service.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("F24 non trovato: id=" + id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<F24ListDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody F24StatusUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(f24Service.updateStatus(tenantId, id, dto.getStato()));
    }
}

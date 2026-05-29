package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.cu.CuDetailDTO;
import it.gavia.sostitutoincloud.dto.cu.CuListDTO;
import it.gavia.sostitutoincloud.dto.cu.CuStatusUpdateDTO;
import it.gavia.sostitutoincloud.service.CuService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cu")
@Log4j2
public class CuController {

    private final CuService cuService;

    public CuController(CuService cuService) {
        this.cuService = cuService;
    }

    @GetMapping
    public ResponseEntity<List<CuListDTO>> findAll(
            @RequestParam(required = false) Integer ownerId,
            @RequestParam(required = false) Integer taxYear) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(cuService.findByTenantId(tenantId, ownerId, taxYear));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CuDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return cuService.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("CU non trovata: id=" + id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CuListDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody CuStatusUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(cuService.updateStatus(tenantId, id, dto.getStato()));
    }
}

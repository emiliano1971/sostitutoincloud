package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.cu.CuDetailDTO;
import it.gavia.sostitutoincloud.dto.cu.CuGeneraRequestDTO;
import it.gavia.sostitutoincloud.dto.cu.CuListDTO;
import it.gavia.sostitutoincloud.dto.cu.CuStatusUpdateDTO;
import it.gavia.sostitutoincloud.service.CuService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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

    @PostMapping("/genera")
    public ResponseEntity<?> genera(@RequestBody CuGeneraRequestDTO req) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("CuController.genera() - ownerId={} year={}", req.getOwnerId(), req.getTaxYear());
        try {
            if (req.getOwnerId() != null) {
                return ResponseEntity.ok(cuService.genera(tenantId, req));
            }
            return ResponseEntity.ok(cuService.generaBatch(tenantId, req.getTaxYear()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Integer id,
            @RequestBody CuStatusUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            return ResponseEntity.ok(cuService.updateStatus(tenantId, id, dto.getStato()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}

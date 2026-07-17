package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.settlement.SettlementCalcolaRequestDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementCalcolaResultDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementDetailDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementListDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementStatusUpdateDTO;
import it.gavia.sostitutoincloud.service.SettlementService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/settlements")
@Log4j2
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    public ResponseEntity<List<SettlementListDTO>> findAll(
            @RequestParam(required = false) Integer ownerId,
            @RequestParam(required = false) String period) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(settlementService.findByTenantId(tenantId, ownerId, period));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SettlementDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return settlementService.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Settlement non trovato: id=" + id));
    }

    @PostMapping("/calcola")
    public ResponseEntity<?> calcola(@RequestBody SettlementCalcolaRequestDTO request) {
        log.info("SettlementController.calcola() - mese={} anno={}", request.getMese(), request.getAnno());
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            SettlementCalcolaResultDTO result = settlementService.calcola(tenantId, request);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Integer id,
            @RequestBody SettlementStatusUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            return ResponseEntity.ok(settlementService.updateStatus(tenantId, id, dto.getStato()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

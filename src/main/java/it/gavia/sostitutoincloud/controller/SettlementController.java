package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.settlement.SettlementDetailDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementListDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementStatusUpdateDTO;
import it.gavia.sostitutoincloud.service.SettlementService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PatchMapping("/{id}/status")
    public ResponseEntity<SettlementListDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody SettlementStatusUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(settlementService.updateStatus(tenantId, id, dto.getStato()));
    }
}

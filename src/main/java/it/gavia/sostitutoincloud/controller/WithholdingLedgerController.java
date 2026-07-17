package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.fiscal.WithholdingLedgerDTO;
import it.gavia.sostitutoincloud.service.WithholdingLedgerService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/withholding-ledger")
@Log4j2
public class WithholdingLedgerController {

    private final WithholdingLedgerService withholdingLedgerService;

    public WithholdingLedgerController(WithholdingLedgerService withholdingLedgerService) {
        this.withholdingLedgerService = withholdingLedgerService;
    }

    @GetMapping
    public ResponseEntity<List<WithholdingLedgerDTO>> findByPeriodo(
            @RequestParam Integer anno,
            @RequestParam Integer mese) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(withholdingLedgerService.findDettaglioByPeriodo(tenantId, anno, mese));
    }

    @GetMapping("/totale")
    public ResponseEntity<Map<String, Object>> totalePeriodo(
            @RequestParam Integer anno,
            @RequestParam Integer mese) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        BigDecimal totale = withholdingLedgerService.totalePeriodo(tenantId, anno, mese);
        long count = withholdingLedgerService.countDaVersarePeriodo(tenantId, anno, mese);
        return ResponseEntity.ok(Map.of("totale", totale, "count", count));
    }
}

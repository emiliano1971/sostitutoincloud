package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.settlement.BookingDaLiquidareDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementCalcolaRequestDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementCalcolaResultDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementDetailDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementListDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementStatusUpdateDTO;
import it.gavia.sostitutoincloud.service.SettlementPdfService;
import it.gavia.sostitutoincloud.service.SettlementService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final SettlementPdfService settlementPdfService;

    public SettlementController(SettlementService settlementService,
                                SettlementPdfService settlementPdfService) {
        this.settlementService = settlementService;
        this.settlementPdfService = settlementPdfService;
    }

    @GetMapping
    public ResponseEntity<List<SettlementListDTO>> findAll(
            @RequestParam(required = false) Integer ownerId,
            @RequestParam(required = false) String period) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(settlementService.findByTenantId(tenantId, ownerId, period));
    }

    /**
     * Prenotazioni con documenti emessi non ancora liquidate: alimenta l'avviso e il
     * relativo dettaglio nella lista liquidazioni.
     * Passa dal service, non dal DAO (Controller → Service → DAO).
     * NB: mappato prima di /{id} non sarebbe necessario — "da-liquidare" non è un Integer —
     * ma resta comunque il path più specifico.
     */
    @GetMapping("/da-liquidare")
    public ResponseEntity<List<BookingDaLiquidareDTO>> findDaLiquidare() {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        List<BookingDaLiquidareDTO> result = settlementService.findDaLiquidare(tenantId);
        log.debug("SettlementController.findDaLiquidare() - tenantId={} count={}", tenantId, result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SettlementDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return settlementService.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Settlement non trovato: id=" + id));
    }

    /** Rendiconto di liquidazione in PDF. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> downloadPdf(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("SettlementController.downloadPdf() - tenantId={} settlementId={}", tenantId, id);
        try {
            byte[] pdf = settlementPdfService.generaPdf(tenantId, id);
            String filename = settlementService.findById(tenantId, id)
                    .map(s -> "Rendiconto_" + safe(s.getPeriod()) + "_" + safe(s.getOwnerName()) + ".pdf")
                    .orElse("Rendiconto_" + id + ".pdf");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdf);
        } catch (NoSuchElementException e) {
            log.warn("SettlementController.downloadPdf() - liquidazione non trovata: id={}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (Exception e) {
            log.error("SettlementController.downloadPdf() - errore generazione PDF settlementId={}: {}",
                    id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Errore generazione PDF", "message", e.getMessage()));
        }
    }

    /**
     * Rende un valore utilizzabile nel nome file del Content-Disposition: gli spazi
     * diventano underscore e i caratteri non ASCII (es. "Niccolò") vanno rimossi,
     * altrimenti l'header non è valido e il browser scarta il nome.
     */
    private String safe(String v) {
        if (v == null || v.isBlank()) {
            return "";
        }
        return v.trim().replaceAll("[^A-Za-z0-9._-]", "_");
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

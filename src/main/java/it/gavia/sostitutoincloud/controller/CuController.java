package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.cu.CuDetailDTO;
import it.gavia.sostitutoincloud.dto.cu.CuGeneraRequestDTO;
import it.gavia.sostitutoincloud.dto.cu.CuListDTO;
import it.gavia.sostitutoincloud.dto.cu.CuStatusUpdateDTO;
import it.gavia.sostitutoincloud.service.CuPdfService;
import it.gavia.sostitutoincloud.service.CuService;
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
@RequestMapping("/api/cu")
@Log4j2
public class CuController {

    private final CuService cuService;
    private final CuPdfService cuPdfService;

    public CuController(CuService cuService, CuPdfService cuPdfService) {
        this.cuService = cuService;
        this.cuPdfService = cuPdfService;
    }

    /** PDF della Certificazione Unica compilando il modello ordinario AdE. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> downloadPdf(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("CuController.downloadPdf() - tenantId={} cuId={}", tenantId, id);
        try {
            byte[] pdf = cuPdfService.generaPdf(tenantId, id);
            String filename = cuService.findById(tenantId, id)
                    .map(cu -> "CU_" + cu.getTaxYear() + "_" + nomeFile(cu.getOwnerTaxCode()) + ".pdf")
                    .orElse("CU_" + id + ".pdf");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdf);
        } catch (NoSuchElementException e) {
            log.warn("CuController.downloadPdf() - non trovato: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("CuController.downloadPdf() - generazione non possibile: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        }
    }

    /** Ripulisce il codice fiscale per usarlo nel nome file. */
    private String nomeFile(String v) {
        return v == null || v.isBlank() ? "CU" : v.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
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

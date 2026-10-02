package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.fiscal.EmettNdcDTO;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.service.DocumentPdfService;
import it.gavia.sostitutoincloud.service.NdcService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Note di credito sulle fatture PM.
 * 400 validazione / stato non ammesso (IllegalArgument/IllegalState), 404 documento non trovato.
 */
@RestController
@RequestMapping("/api/ndc")
@Log4j2
public class NdcController {

    private final NdcService ndcService;
    private final DocumentPdfService documentPdfService;

    public NdcController(NdcService ndcService, DocumentPdfService documentPdfService) {
        this.ndcService = ndcService;
        this.documentPdfService = documentPdfService;
    }

    @PostMapping
    public ResponseEntity<?> emetti(@RequestBody EmettNdcDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        log.info("NdcController.emetti() - tenantId={} fattura={}", tenantId, dto.getFkFiscalDocumentId());
        try {
            FiscalDocument ndc = ndcService.emettiNdc(tenantId, utenteId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(ndc);
        } catch (NoSuchElementException e) {
            return errore(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("NdcController.emetti() - 400: {}", e.getMessage());
            return errore(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> annulla(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("NdcController.annulla() - tenantId={} ndc={}", tenantId, id);
        try {
            return ResponseEntity.ok(ndcService.annullaNdc(tenantId, id));
        } catch (NoSuchElementException e) {
            return errore(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("NdcController.annulla() - 400: {}", e.getMessage());
            return errore(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> pdf(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("NdcController.pdf() - tenantId={} ndc={}", tenantId, id);
        try {
            byte[] pdf = documentPdfService.generaPdf(tenantId, id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nota-credito-" + id + ".pdf\"")
                    .body(pdf);
        } catch (NoSuchElementException e) {
            return errore(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return errore(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> errore(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message, "message", message));
    }
}

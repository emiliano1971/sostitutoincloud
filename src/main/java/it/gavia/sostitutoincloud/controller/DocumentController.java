package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.document.DocumentDetailDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateRequestDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateResponseDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentListDTO;
import it.gavia.sostitutoincloud.service.DocumentGenerationService;
import it.gavia.sostitutoincloud.service.FiscalDocumentService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Log4j2
public class DocumentController {

    private final FiscalDocumentService fiscalDocumentService;
    private final DocumentGenerationService documentGenerationService;

    public DocumentController(FiscalDocumentService fiscalDocumentService,
                              DocumentGenerationService documentGenerationService) {
        this.fiscalDocumentService = fiscalDocumentService;
        this.documentGenerationService = documentGenerationService;
    }

    @GetMapping
    public ResponseEntity<List<DocumentListDTO>> findAll(
            @RequestParam(required = false) String stato,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(fiscalDocumentService.findByTenantId(tenantId, stato, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return fiscalDocumentService.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Documento non trovato: id=" + id));
    }

    @PostMapping("/generate")
    public ResponseEntity<DocumentGenerateResponseDTO> generate(
            @RequestBody DocumentGenerateRequestDTO request) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        DocumentGenerateResponseDTO result = documentGenerationService.generate(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}

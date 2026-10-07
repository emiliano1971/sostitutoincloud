package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.document.DocumentDetailDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentFilterDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateRequestDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentGenerateResponseDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentPageDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentStatoUpdateDTO;
import it.gavia.sostitutoincloud.dto.fiscal.StatoFiscaleRicevutaDTO;
import it.gavia.sostitutoincloud.service.DocumentGenerationService;
import it.gavia.sostitutoincloud.service.DocumentPdfService;
import it.gavia.sostitutoincloud.service.FiscalDocumentService;
import it.gavia.sostitutoincloud.service.SdiXmlService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/documents")
@Log4j2
public class DocumentController {

    private final FiscalDocumentService fiscalDocumentService;
    private final DocumentGenerationService documentGenerationService;
    private final DocumentPdfService documentPdfService;
    private final SdiXmlService sdiXmlService;

    public DocumentController(FiscalDocumentService fiscalDocumentService,
                              DocumentGenerationService documentGenerationService,
                              DocumentPdfService documentPdfService,
                              SdiXmlService sdiXmlService) {
        this.fiscalDocumentService = fiscalDocumentService;
        this.documentGenerationService = documentGenerationService;
        this.documentPdfService = documentPdfService;
        this.sdiXmlService = sdiXmlService;
    }

    /**
     * Lista documenti paginata. size=0 (default) → dimensione pagina del tenant
     * (tenant_settings.page_size). Filtri e ordinamento valgono su tutti i documenti, non solo
     * sulla pagina. filtroFiscale (da_liquidare | f24_non_pagato | senza_cu): solo ricevute
     * owner attive; valore non valido → 400 via GlobalExceptionHandler.
     */
    @GetMapping
    public ResponseEntity<DocumentPageDTO> findAll(
            @RequestParam(required = false) String stato,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String filtroFiscale,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataTo,
            @RequestParam(required = false) Integer ownerId,
            @RequestParam(required = false) String liquidazione,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        DocumentFilterDTO filtro = DocumentFilterDTO.builder()
                .stato(stato).tipo(tipo).search(search).filtroFiscale(filtroFiscale)
                .dataFrom(dataFrom).dataTo(dataTo).ownerId(ownerId)
                .liquidazione(liquidazione).sort(sort).dir(dir)
                .build();
        return ResponseEntity.ok(fiscalDocumentService.findPage(tenantId, filtro, page, size));
    }

    /** Stato fiscale di una ricevuta owner: ritenuta/F24, liquidazione, CU. 404 se non è una ricevuta. */
    @GetMapping("/{id}/stato-fiscale")
    public ResponseEntity<?> statoFiscale(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.debug("DocumentController.statoFiscale() - tenantId={} documentId={}", tenantId, id);
        try {
            StatoFiscaleRicevutaDTO dto = fiscalDocumentService.getStatoFiscale(tenantId, id);
            return ResponseEntity.ok(dto);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return fiscalDocumentService.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Documento non trovato: id=" + id));
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody DocumentGenerateRequestDTO request) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            DocumentGenerateResponseDTO result = documentGenerationService.generate(tenantId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalStateException e) {
            // 422: emissione bloccata (es. fattura PM senza CF ospite).
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        }
    }

    /**
     * PDF del documento fiscale (fattura PM / ricevuta owner) generato server-side.
     * Il filename usa il numero documento del documento richiesto.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> downloadPdf(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("DocumentController.downloadPdf() - tenantId={} documentId={}", tenantId, id);
        try {
            byte[] pdf = documentPdfService.generaPdf(tenantId, id);
            String filename = fiscalDocumentService.findById(tenantId, id)
                    .map(d -> d.getDocumentNumber())
                    .orElse("documento-" + id) + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdf);
        } catch (NoSuchElementException e) {
            log.warn("DocumentController.downloadPdf() - documento non trovato: id={}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("DocumentController.downloadPdf() - generazione non possibile: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        }
    }

    /**
     * Genera il file XML SDI (FatturaPA FPR12) del documento e lo porta in stato 'sent_sdi'.
     * Ammesso solo per le fatture PM: la ricevuta owner è un documento interno.
     */
    @PostMapping("/{id}/sdi")
    public ResponseEntity<?> inviaSdi(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.info("DocumentController.inviaSdi() - tenantId={} documentId={}", tenantId, id);
        try {
            String filePath = sdiXmlService.generaEInvia(tenantId, id);
            String progressivo = fiscalDocumentService.findById(tenantId, id)
                    .map(DocumentDetailDTO::getSdiProgressivo)
                    .orElse(null);
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "File SDI generato",
                    "filePath", filePath,
                    "progressivo", progressivo != null ? progressivo : ""));
        } catch (NoSuchElementException e) {
            log.warn("DocumentController.inviaSdi() - documento non trovato: id={}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalStateException e) {
            // 422: invio non ammesso (ricevuta owner, fattura già inviata, dati mancanti)
            log.warn("DocumentController.inviaSdi() - invio non ammesso: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (Exception e) {
            log.error("DocumentController.inviaSdi() - errore imprevisto per id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of(
                            "error", "Errore generazione XML SDI",
                            "message", "Errore generazione XML SDI"));
        }
    }

    @PatchMapping("/{id}/stato")
    public ResponseEntity<DocumentDetailDTO> aggiornaStato(@PathVariable Integer id,
                                                           @RequestBody DocumentStatoUpdateDTO request) {
        // IllegalArgumentException (documento/stato non validi) → 400 via GlobalExceptionHandler
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        DocumentDetailDTO result = fiscalDocumentService.aggiornaStato(tenantId, id, request.getStato());
        return ResponseEntity.ok(result);
    }
}

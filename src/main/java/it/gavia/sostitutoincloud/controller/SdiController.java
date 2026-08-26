package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.sdi.SdiElaborazioneResultDTO;
import it.gavia.sostitutoincloud.dto.sdi.SdiFileDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantDetailDTO;
import it.gavia.sostitutoincloud.service.SdiRispostaService;
import it.gavia.sostitutoincloud.service.SdiXmlService;
import it.gavia.sostitutoincloud.service.TenantService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/sdi")
@Log4j2
public class SdiController {

    private final SdiRispostaService sdiRispostaService;
    private final SdiXmlService sdiXmlService;
    private final TenantService tenantService;

    public SdiController(SdiRispostaService sdiRispostaService,
                         SdiXmlService sdiXmlService,
                         TenantService tenantService) {
        this.sdiRispostaService = sdiRispostaService;
        this.sdiXmlService = sdiXmlService;
        this.tenantService = tenantService;
    }

    /**
     * Scarica il file XML archiviato di un invio SDI.
     * Nessun vincolo di ruolo oltre all'autenticazione — come il download del PDF in
     * DocumentController — ma il documento deve appartenere al tenant del chiamante
     * (il super_admin, che non ha tenant, non è vincolato).
     */
    @GetMapping("/download/{progressivo}")
    public ResponseEntity<?> downloadXml(@PathVariable String progressivo) {
        Integer tenantId = SecurityUtils.hasRole("super_admin") ? null : SecurityUtils.getCurrentTenantId();
        log.info("SdiController.downloadXml() - tenantId={} progressivo={}", tenantId, progressivo);
        try {
            SdiFileDTO file = sdiXmlService.leggiFileXml(tenantId, progressivo);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getNomeFile() + "\"")
                    .body(file.getContenuto());
        } catch (NoSuchElementException e) {
            log.warn("SdiController.downloadXml() - {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("SdiController.downloadXml() - errore lettura file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        }
    }

    /**
     * Elabora le risposte SDI presenti in incoming/: aggiorna gli stati dei documenti,
     * archivia le ricevute in elaborati/ e chiude gli invii in outgoing/.
     *
     * Riservato a tenant_admin e super_admin: /api/sdi/** non è sotto /api/admin/**,
     * quindi la SecurityChain lo lascia a isAuthenticated() e il ruolo va verificato qui.
     */
    @PostMapping("/elabora-risposte")
    public ResponseEntity<?> elaboraRisposte() {
        log.info("SdiController.elaboraRisposte() chiamato");
        boolean superAdmin = SecurityUtils.hasRole("super_admin");
        if (!superAdmin && !SecurityUtils.hasRole("tenant_admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // super_admin: nessun filtro. tenant_admin: solo le risposte del proprio tenant,
        // riconosciute dal prefisso IT{identificativo}_ nel nome file.
        String tenantPiva = null;
        if (!superAdmin) {
            Integer tenantId = SecurityUtils.getCurrentTenantId();
            tenantPiva = tenantService.findById(tenantId)
                    .map(this::identificativoFiscale)
                    .orElse(null);
            if (tenantPiva == null) {
                // Senza identificativo non è possibile filtrare: elaborare tutto
                // esporrebbe le risposte degli altri tenant.
                log.warn("SdiController.elaboraRisposte() - tenantId={} senza P.IVA né codice fiscale", tenantId);
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(java.util.Map.of(
                        "error", "P.IVA o codice fiscale del tenant mancanti",
                        "message", "Imposta P.IVA o codice fiscale del tenant per elaborare le risposte SDI"));
            }
        }

        SdiElaborazioneResultDTO result = sdiRispostaService.elaboraRisposte(tenantPiva);
        return ResponseEntity.ok(result);
    }

    /** Stessa regola di SdiXmlService per il nome file: P.IVA, altrimenti codice fiscale. */
    private String identificativoFiscale(TenantDetailDTO tenant) {
        if (tenant.getVatNumber() != null && !tenant.getVatNumber().isBlank()) {
            return tenant.getVatNumber();
        }
        return tenant.getTaxCode() != null && !tenant.getTaxCode().isBlank() ? tenant.getTaxCode() : null;
    }
}

package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.booking.BookingListDTO;
import it.gavia.sostitutoincloud.dto.cu.CuDetailDTO;
import it.gavia.sostitutoincloud.dto.cu.CuListDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentDetailDTO;
import it.gavia.sostitutoincloud.dto.document.DocumentListDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerDashboardDTO;
import it.gavia.sostitutoincloud.dto.settlement.SettlementListDTO;
import it.gavia.sostitutoincloud.service.BookingService;
import it.gavia.sostitutoincloud.service.CuPdfService;
import it.gavia.sostitutoincloud.service.CuService;
import it.gavia.sostitutoincloud.service.DocumentPdfService;
import it.gavia.sostitutoincloud.service.FiscalDocumentService;
import it.gavia.sostitutoincloud.service.OwnerDashboardService;
import it.gavia.sostitutoincloud.service.SettlementService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Portale del proprietario: espone i soli dati del proprietario collegato all'utente
 * autenticato. Distinto da OwnerController (/api/owners), che è il CRUD delle anagrafiche
 * proprietario a uso del tenant_admin.
 *
 * <p>L'ownerId è SEMPRE preso dal token con SecurityUtils.getCurrentOwnerId(), mai da un
 * parametro di richiesta: è questo a garantire che un proprietario non possa leggere i dati
 * di un altro cambiando un id nell'URL.
 *
 * <p>L'accesso è riservato al ruolo owner_user dalla regola su /api/owner/** in SecurityConfig
 * (un tenant_admin riceve 403), quindi qui non servono controlli di ruolo.
 */
@RestController
@RequestMapping("/api/owner")
@Log4j2
public class OwnerPortalController {

    private final BookingService bookingService;
    private final SettlementService settlementService;
    private final CuService cuService;
    private final CuPdfService cuPdfService;
    private final FiscalDocumentService fiscalDocumentService;
    private final DocumentPdfService documentPdfService;
    private final OwnerDashboardService ownerDashboardService;

    public OwnerPortalController(BookingService bookingService,
                                 SettlementService settlementService,
                                 CuService cuService,
                                 CuPdfService cuPdfService,
                                 FiscalDocumentService fiscalDocumentService,
                                 DocumentPdfService documentPdfService,
                                 OwnerDashboardService ownerDashboardService) {
        this.bookingService = bookingService;
        this.settlementService = settlementService;
        this.cuService = cuService;
        this.cuPdfService = cuPdfService;
        this.fiscalDocumentService = fiscalDocumentService;
        this.documentPdfService = documentPdfService;
        this.ownerDashboardService = ownerDashboardService;
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingListDTO>> bookings() {
        Integer ownerId = SecurityUtils.getCurrentOwnerId();
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (ownerId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("OwnerPortalController.bookings() - tenantId={} ownerId={}", tenantId, ownerId);
        return ResponseEntity.ok(bookingService.findByOwner(tenantId, ownerId));
    }

    @GetMapping("/settlements")
    public ResponseEntity<List<SettlementListDTO>> settlements() {
        Integer ownerId = SecurityUtils.getCurrentOwnerId();
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (ownerId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("OwnerPortalController.settlements() - tenantId={} ownerId={}", tenantId, ownerId);
        return ResponseEntity.ok(settlementService.findByTenantId(tenantId, ownerId, null));
    }

    /** Ricevute owner del proprietario autenticato (le fatture PM non lo riguardano). */
    @GetMapping("/documents")
    public ResponseEntity<List<DocumentListDTO>> documents() {
        Integer ownerId = SecurityUtils.getCurrentOwnerId();
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (ownerId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("OwnerPortalController.documents() - tenantId={} ownerId={}", tenantId, ownerId);
        return ResponseEntity.ok(fiscalDocumentService.findRicevuteByOwner(tenantId, ownerId));
    }

    /**
     * PDF di una propria ricevuta. Come per la CU serve un endpoint dedicato: /api/documents/**
     * è riservato al back-office, e lì il filtro è solo sul tenant.
     */
    @GetMapping("/documents/{id}/pdf")
    public ResponseEntity<?> documentPdf(@PathVariable Integer id) {
        Integer ownerId = SecurityUtils.getCurrentOwnerId();
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (ownerId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("OwnerPortalController.documentPdf() - tenantId={} ownerId={} documentId={}",
                tenantId, ownerId, id);

        if (!fiscalDocumentService.isRicevutaDellOwner(tenantId, ownerId, id)) {
            // Non si distingue "non esiste" da "non è tua": per un portale esterno è
            // preferibile non confermare l'esistenza di documenti di altri proprietari.
            log.warn("OwnerPortalController.documentPdf() - documento {} non è una ricevuta dell'owner {}",
                    id, ownerId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Documento non trovato",
                            "message", "Documento non trovato tra le tue ricevute: id=" + id));
        }

        try {
            byte[] pdf = documentPdfService.generaPdf(tenantId, id);
            String filename = fiscalDocumentService.findById(tenantId, id)
                    .map(DocumentDetailDTO::getDocumentNumber)
                    .orElse("documento-" + id) + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdf);
        } catch (NoSuchElementException e) {
            log.warn("OwnerPortalController.documentPdf() - non trovato: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("OwnerPortalController.documentPdf() - generazione non possibile: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        }
    }

    @GetMapping("/cu")
    public ResponseEntity<List<CuListDTO>> cu() {
        Integer ownerId = SecurityUtils.getCurrentOwnerId();
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (ownerId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("OwnerPortalController.cu() - tenantId={} ownerId={}", tenantId, ownerId);
        return ResponseEntity.ok(cuService.findByTenantId(tenantId, ownerId, null));
    }

    /**
     * PDF della propria Certificazione Unica.
     *
     * <p>Serve un endpoint dedicato perché /api/cu/** è riservato al back-office del
     * tenant: senza questo il proprietario non potrebbe scaricare la propria CU.
     * La CU deve appartenere all'owner del token, altrimenti 403 — è il controllo che
     * l'endpoint del back-office non fa, dove il filtro è solo sul tenant.
     */
    @GetMapping("/cu/{id}/pdf")
    public ResponseEntity<?> cuPdf(@PathVariable Integer id) {
        Integer ownerId = SecurityUtils.getCurrentOwnerId();
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (ownerId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("OwnerPortalController.cuPdf() - tenantId={} ownerId={} cuId={}", tenantId, ownerId, id);

        // findById filtra già per tenant; qui si aggiunge il vincolo sull'owner.
        Optional<CuDetailDTO> cuOpt = cuService.findById(tenantId, id);
        if (cuOpt.isEmpty()) {
            log.warn("OwnerPortalController.cuPdf() - CU non trovata: id={}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "CU non trovata", "message", "CU non trovata: id=" + id));
        }
        CuDetailDTO cu = cuOpt.get();
        if (!ownerId.equals(cu.getFkOwnerId())) {
            log.warn("OwnerPortalController.cuPdf() - CU {} non appartiene all'owner {}", id, ownerId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "CU di un altro proprietario",
                            "message", "La CU richiesta non appartiene al proprietario autenticato"));
        }

        try {
            byte[] pdf = cuPdfService.generaPdf(tenantId, id);
            String filename = "CU_" + cu.getTaxYear() + "_" + nomeFile(cu.getOwnerTaxCode()) + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdf);
        } catch (NoSuchElementException e) {
            log.warn("OwnerPortalController.cuPdf() - non trovato: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalStateException e) {
            // Es. CU ancora in bozza: non è un errore di autorizzazione.
            log.warn("OwnerPortalController.cuPdf() - generazione non possibile: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage(), "message", e.getMessage()));
        }
    }

    /** Ripulisce il codice fiscale per usarlo nel nome file. */
    private String nomeFile(String v) {
        return v == null || v.isBlank() ? "CU" : v.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    @GetMapping("/dashboard")
    public ResponseEntity<OwnerDashboardDTO> dashboard() {
        Integer ownerId = SecurityUtils.getCurrentOwnerId();
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (ownerId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        log.info("OwnerPortalController.dashboard() - tenantId={} ownerId={}", tenantId, ownerId);
        return ResponseEntity.ok(ownerDashboardService.getDashboard(ownerId, tenantId));
    }
}

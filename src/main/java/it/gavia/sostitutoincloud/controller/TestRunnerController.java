package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.test.CleanupAnagraficaDTO;
import it.gavia.sostitutoincloud.dto.test.CleanupBookingsDTO;
import it.gavia.sostitutoincloud.dto.test.CleanupCuDTO;
import it.gavia.sostitutoincloud.dto.test.CleanupDocumentiDTO;
import it.gavia.sostitutoincloud.dto.test.CleanupF24DTO;
import it.gavia.sostitutoincloud.dto.test.CleanupSettlementDTO;
import it.gavia.sostitutoincloud.service.TestCleanupService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Endpoint di supporto ai test E2E. Attivo SOLO nel profilo local.
 *
 * NB: sta sotto /api/test e non /api/public/test proprio perché è distruttivo:
 * la SecurityChain lo fa ricadere in /api/** → isAuthenticated(), mentre tutto
 * ciò che è sotto /api/public/** è permitAll.
 */
@RestController
@RequestMapping("/api/test")
@Profile("local")
@Log4j2
public class TestRunnerController {

    private final TestCleanupService testCleanupService;

    public TestRunnerController(TestCleanupService testCleanupService) {
        this.testCleanupService = testCleanupService;
    }

    /**
     * Cancella immobile e/o proprietario creati dai test, oppure — passando
     * lastNamePattern — tutti i proprietari di test con quel pattern nel cognome e i
     * loro immobili (pre-cleanup dei residui di una run interrotta).
     * Procede solo sui nomi che contengono "E2E-" o "TEST-".
     */
    @DeleteMapping("/cleanup-anagrafica")
    public ResponseEntity<?> cleanupAnagrafica(@RequestBody CleanupAnagraficaDTO dto) {
        log.info("TestRunnerController.cleanupAnagrafica() - ownerId={} propertyId={} lastNamePattern={}",
                dto.getOwnerId(), dto.getPropertyId(), dto.getLastNamePattern());
        try {
            if (dto.getLastNamePattern() != null && !dto.getLastNamePattern().isBlank()) {
                Integer tenantId = SecurityUtils.getCurrentTenantId();
                return ResponseEntity.ok(
                        testCleanupService.cleanupByLastNamePattern(tenantId, dto.getLastNamePattern()));
            }
            return ResponseEntity.ok(
                    testCleanupService.cleanupAnagrafica(dto.getOwnerId(), dto.getPropertyId()));
        } catch (IllegalArgumentException ex) {
            log.warn("TestRunnerController.cleanupAnagrafica() - rifiutato: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "message", ex.getMessage()));
        }
    }

    /**
     * Cancella i booking di test del tenant chiamante e tutto ciò che vi pende
     * (documenti fiscali, righe di liquidazione, ritenute, tracce di audit).
     * Procede solo se il pattern contiene "E2E-" o "TEST-".
     */
    @DeleteMapping("/cleanup-bookings")
    public ResponseEntity<?> cleanupBookings(@RequestBody CleanupBookingsDTO dto) {
        log.info("TestRunnerController.cleanupBookings() - externalIdPattern={}", dto.getExternalIdPattern());
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            return ResponseEntity.ok(
                    testCleanupService.cleanupBookings(tenantId, dto.getExternalIdPattern()));
        } catch (IllegalArgumentException ex) {
            log.warn("TestRunnerController.cleanupBookings() - rifiutato: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "message", ex.getMessage()));
        }
    }

    /**
     * Rimuove i documenti fiscali e le ritenute emessi da un test su un booking,
     * riportandolo allo stato 'ready'. Il booking deve essere del tenant chiamante.
     * NB: sdi_progressivo non viene toccato.
     */
    @DeleteMapping("/cleanup-documenti")
    public ResponseEntity<?> cleanupDocumenti(@RequestBody CleanupDocumentiDTO dto) {
        log.info("TestRunnerController.cleanupDocumenti() - bookingId={}", dto.getBookingId());
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            return ResponseEntity.ok(testCleanupService.cleanupDocumenti(tenantId, dto.getBookingId()));
        } catch (NoSuchElementException ex) {
            log.warn("TestRunnerController.cleanupDocumenti() - non trovato: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", HttpStatus.NOT_FOUND.value(),
                    "error", HttpStatus.NOT_FOUND.getReasonPhrase(),
                    "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            log.warn("TestRunnerController.cleanupDocumenti() - rifiutato: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "message", ex.getMessage()));
        }
    }

    /**
     * Annulla la generazione di un F24 di test: elimina il record e riporta le ritenute
     * collegate a 'da_versare'. L'F24 deve essere del tenant chiamante; se è pagato serve
     * forzaSePagato=true, che il test passa solo sull'F24 generato da lui.
     */
    @DeleteMapping("/cleanup-f24")
    public ResponseEntity<?> cleanupF24(@RequestBody CleanupF24DTO dto) {
        log.info("TestRunnerController.cleanupF24() - f24Id={} forzaSePagato={}",
                dto.getF24Id(), dto.getForzaSePagato());
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            return ResponseEntity.ok(testCleanupService.cleanupF24(
                    tenantId, dto.getF24Id(), Boolean.TRUE.equals(dto.getForzaSePagato())));
        } catch (NoSuchElementException ex) {
            log.warn("TestRunnerController.cleanupF24() - non trovato: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", HttpStatus.NOT_FOUND.value(),
                    "error", HttpStatus.NOT_FOUND.getReasonPhrase(),
                    "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            log.warn("TestRunnerController.cleanupF24() - rifiutato: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "message", ex.getMessage()));
        }
    }

    /**
     * Annulla una liquidazione di test: elimina il settlement con i suoi collegamenti e
     * riporta a 'doc_issued' le prenotazioni che il pagamento aveva portato a 'settled'.
     * Se la liquidazione è pagata serve forzaSePagato=true, che il test passa solo su
     * quella calcolata da lui.
     */
    @DeleteMapping("/cleanup-settlement")
    public ResponseEntity<?> cleanupSettlement(@RequestBody CleanupSettlementDTO dto) {
        log.info("TestRunnerController.cleanupSettlement() - settlementId={} forzaSePagato={}",
                dto.getSettlementId(), dto.getForzaSePagato());
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            return ResponseEntity.ok(testCleanupService.cleanupSettlement(
                    tenantId, dto.getSettlementId(), Boolean.TRUE.equals(dto.getForzaSePagato())));
        } catch (NoSuchElementException ex) {
            log.warn("TestRunnerController.cleanupSettlement() - non trovato: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", HttpStatus.NOT_FOUND.value(),
                    "error", HttpStatus.NOT_FOUND.getReasonPhrase(),
                    "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            log.warn("TestRunnerController.cleanupSettlement() - rifiutato: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "message", ex.getMessage()));
        }
    }

    /**
     * Elimina una Certificazione Unica generata da un test. La CU deve essere del tenant
     * chiamante e non ancora trasmessa ('sent'/'delivered'): quelle si rifiutano sempre.
     */
    @DeleteMapping("/cleanup-cu")
    public ResponseEntity<?> cleanupCu(@RequestBody CleanupCuDTO dto) {
        log.info("TestRunnerController.cleanupCu() - cuId={}", dto.getCuId());
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            return ResponseEntity.ok(testCleanupService.cleanupCu(tenantId, dto.getCuId()));
        } catch (NoSuchElementException ex) {
            log.warn("TestRunnerController.cleanupCu() - non trovata: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", HttpStatus.NOT_FOUND.value(),
                    "error", HttpStatus.NOT_FOUND.getReasonPhrase(),
                    "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            log.warn("TestRunnerController.cleanupCu() - rifiutato: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "message", ex.getMessage()));
        }
    }
}

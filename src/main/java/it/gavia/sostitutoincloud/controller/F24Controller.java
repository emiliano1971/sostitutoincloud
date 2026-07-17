package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.fiscal.F24GeneraRequestDTO;
import it.gavia.sostitutoincloud.dto.fiscal.F24GenerazioneResultDTO;
import it.gavia.sostitutoincloud.dto.fiscal.F24RecordDTO;
import it.gavia.sostitutoincloud.service.F24Service;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/f24")
@Log4j2
public class F24Controller {

    private final F24Service f24Service;

    public F24Controller(F24Service f24Service) {
        this.f24Service = f24Service;
    }

    @GetMapping
    public ResponseEntity<List<F24RecordDTO>> findAll() {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(f24Service.findByTenant(tenantId));
    }

    // IllegalStateException (F24 già esistente) e IllegalArgumentException (nessuna ritenuta)
    // sono mappate a 400 dal GlobalExceptionHandler.
    @PostMapping("/genera")
    public ResponseEntity<F24GenerazioneResultDTO> genera(@RequestBody F24GeneraRequestDTO request) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        F24GenerazioneResultDTO result = f24Service.generaF24(tenantId, request.getAnno(), request.getMese());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<F24GenerazioneResultDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(f24Service.findDettaglio(tenantId, id));
    }

    @PatchMapping("/{id}/pagato")
    public ResponseEntity<F24RecordDTO> marcaPagato(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(f24Service.marcaPagato(tenantId, id));
    }

    @PatchMapping("/{id}/ricalcola")
    public ResponseEntity<?> ricalcola(@PathVariable Integer id) {
        log.info("F24Controller.ricalcola() - id={}", id);
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            F24GenerazioneResultDTO result = f24Service.ricalcola(tenantId, id);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            // 422: F24 già pagato. Includiamo anche "message" per il client (apiClient legge .message).
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            // 400: nessuna ritenuta nuova per il periodo.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("error", e.getMessage(), "message", e.getMessage()));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

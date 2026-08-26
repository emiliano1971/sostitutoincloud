package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.tenant.TenantCreateDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantDetailDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantListDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantStatusUpdateDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantUpdateDTO;
import it.gavia.sostitutoincloud.dto.user.UtenteCreateDTO;
import it.gavia.sostitutoincloud.dto.user.UtenteListDTO;
import it.gavia.sostitutoincloud.service.TenantService;
import it.gavia.sostitutoincloud.service.UserManagementService;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/tenants")
@Log4j2
public class TenantController {

    private final TenantService tenantService;
    private final UserManagementService userManagementService;

    public TenantController(TenantService tenantService,
                            UserManagementService userManagementService) {
        this.tenantService = tenantService;
        this.userManagementService = userManagementService;
    }

    @PostMapping
    public ResponseEntity<TenantDetailDTO> create(@RequestBody TenantCreateDTO dto) {
        TenantDetailDTO created = tenantService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<TenantListDTO>> findAll() {
        return ResponseEntity.ok(tenantService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantDetailDTO> findById(@PathVariable Integer id) {
        return tenantService.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Tenant non trovato: id=" + id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantDetailDTO> update(
            @PathVariable Integer id,
            @RequestBody TenantUpdateDTO dto) {
        TenantDetailDTO updated = tenantService.update(id, dto);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TenantDetailDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody TenantStatusUpdateDTO dto) {
        TenantDetailDTO updated = tenantService.updateStatus(id, dto.getStato());
        return ResponseEntity.ok(updated);
    }

    /**
     * Utenti di un tenant — solo super_admin (l'intero /api/admin/** lo è via SecurityConfig).
     * Serve al super_admin per sapere se il tenant ha già un amministratore.
     */
    @GetMapping("/{tenantId}/users")
    public ResponseEntity<List<UtenteListDTO>> findUsers(@PathVariable Integer tenantId) {
        return ResponseEntity.ok(userManagementService.findByTenantId(tenantId));
    }

    /**
     * Crea il primo utente amministratore di un tenant — solo super_admin.
     * Il ruolo è forzato a tenant_admin, qualunque cosa arrivi nel body.
     */
    @PostMapping("/{tenantId}/users")
    public ResponseEntity<?> createUser(
            @PathVariable Integer tenantId,
            @RequestBody UtenteCreateDTO dto) {
        log.info("TenantController.createUser() - tenantId={} email={}", tenantId, dto.getEmail());
        try {
            if (!tenantService.findById(tenantId).isPresent()) {
                throw new IllegalArgumentException("Tenant non trovato: id=" + tenantId);
            }
            UtenteListDTO created = userManagementService.createTenantAdmin(tenantId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            log.warn("TenantController.createUser() - richiesta non valida: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", HttpStatus.BAD_REQUEST.value(),
                    "error", HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "message", ex.getMessage()));
        }
    }
}

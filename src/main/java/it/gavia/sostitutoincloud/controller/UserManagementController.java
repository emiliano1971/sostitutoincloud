package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.user.UtenteCreateDTO;
import it.gavia.sostitutoincloud.dto.user.UtenteListDTO;
import it.gavia.sostitutoincloud.service.UserManagementService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Log4j2
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    private boolean isTenantAdmin() {
        return SecurityUtils.hasRole("tenant_admin") || SecurityUtils.hasRole("super_admin");
    }

    @GetMapping
    public ResponseEntity<List<UtenteListDTO>> findAll() {
        if (!isTenantAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(userManagementService.findByTenantId(tenantId));
    }

    @PostMapping
    public ResponseEntity<UtenteListDTO> create(@RequestBody UtenteCreateDTO dto) {
        if (!isTenantAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        UtenteListDTO created = userManagementService.create(tenantId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UtenteListDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody Map<String, Boolean> body) {
        if (!isTenantAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        UtenteListDTO updated = userManagementService.updateStatus(tenantId, id, body.get("attivo"));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        if (!isTenantAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        userManagementService.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}

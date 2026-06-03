package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.settings.TenantSettingsDTO;
import it.gavia.sostitutoincloud.dto.settings.TenantSettingsUpdateDTO;
import it.gavia.sostitutoincloud.service.TenantSettingsService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@Log4j2
public class TenantSettingsController {

    private final TenantSettingsService tenantSettingsService;

    public TenantSettingsController(TenantSettingsService tenantSettingsService) {
        this.tenantSettingsService = tenantSettingsService;
    }

    @GetMapping
    public ResponseEntity<TenantSettingsDTO> getSettings() {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(tenantSettingsService.getSettings(tenantId));
    }

    @PutMapping
    public ResponseEntity<?> updateSettings(@RequestBody TenantSettingsUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            TenantSettingsDTO result = tenantSettingsService.updateSettings(tenantId, dto);
            return ResponseEntity.ok(result);
        } catch (UnsupportedOperationException e) {
            log.warn("TenantSettingsController.updateSettings() - 501: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}

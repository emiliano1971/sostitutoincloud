package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.admin.SuperAdminDashboardDTO;
import it.gavia.sostitutoincloud.service.TenantService;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@Log4j2
public class AdminDashboardController {

    private final TenantService tenantService;

    public AdminDashboardController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    // Accesso ristretto a ROLE_SUPER_ADMIN tramite SecurityConfig (/api/admin/**)
    @GetMapping
    public ResponseEntity<SuperAdminDashboardDTO> getDashboard() {
        return ResponseEntity.ok(tenantService.getDashboard());
    }
}

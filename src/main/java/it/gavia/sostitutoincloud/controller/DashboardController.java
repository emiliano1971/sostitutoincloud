package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.dashboard.DashboardDTO;
import it.gavia.sostitutoincloud.service.DashboardService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Log4j2
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardDTO> getDashboard() {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.debug("DashboardController.getDashboard() - tenantId={}", tenantId);
        DashboardDTO dto = dashboardService.getDashboard(tenantId);
        return ResponseEntity.ok(dto);
    }
}

package it.gavia.sostitutoincloud.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperAdminDashboardDTO {

    private Integer totalTenant;
    private Integer tenantAttivi;
    private Integer tenantSospesi;
    private Integer tenantDraft;
    private Integer totalProprietari;
    private Integer totalImmobili;
    private Integer totalPrenotazioni;
    private List<TenantSummaryDTO> ultimiTenant;   // ultimi 5 per created_at DESC
}

package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TenantSettingsDAO;
import it.gavia.sostitutoincloud.dto.settings.TenantSettingsDTO;
import it.gavia.sostitutoincloud.dto.settings.TenantSettingsUpdateDTO;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TenantSettings;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Log4j2
public class TenantSettingsService {

    private final TenantSettingsDAO tenantSettingsDAO;
    private final TenantDAO tenantDAO;

    public TenantSettingsService(TenantSettingsDAO tenantSettingsDAO, TenantDAO tenantDAO) {
        this.tenantSettingsDAO = tenantSettingsDAO;
        this.tenantDAO = tenantDAO;
    }

    public TenantSettingsDTO getSettings(Integer tenantId) {
        log.info("TenantSettingsService.getSettings() - tenantId={}", tenantId);

        Tenant tenant = tenantDAO.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant non trovato: id=" + tenantId));

        TenantSettings settings = tenantSettingsDAO.findByTenantId(tenantId)
                .orElseGet(() -> {
                    log.info("Settings non esistenti per tenantId={}, creo defaults", tenantId);
                    return tenantSettingsDAO.save(defaultSettings(tenantId));
                });

        return toDTO(tenant, settings);
    }

    public TenantSettingsDTO updateSettings(Integer tenantId, TenantSettingsUpdateDTO dto) {
        log.info("TenantSettingsService.updateSettings() - tenantId={}", tenantId);

        Tenant tenant = tenantDAO.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant non trovato: id=" + tenantId));

        if (hasTenantDataChanges(dto)) {
            Tenant toUpdate = Tenant.builder()
                    .id(tenant.getId())
                    .legalName(dto.getLegalName() != null ? dto.getLegalName() : tenant.getLegalName())
                    .displayName(dto.getDisplayName() != null ? dto.getDisplayName() : tenant.getDisplayName())
                    .taxCode(dto.getTaxCode() != null ? dto.getTaxCode() : tenant.getTaxCode())
                    .vatNumber(dto.getVatNumber() != null ? dto.getVatNumber() : tenant.getVatNumber())
                    .administrativeEmail(dto.getAdministrativeEmail() != null ? dto.getAdministrativeEmail() : tenant.getAdministrativeEmail())
                    .pec(dto.getPec() != null ? dto.getPec() : tenant.getPec())
                    .phone(dto.getPhone() != null ? dto.getPhone() : tenant.getPhone())
                    .legalAddress(dto.getLegalAddress() != null ? dto.getLegalAddress() : tenant.getLegalAddress())
                    .build();
            tenant = tenantDAO.update(toUpdate);
        }

        TenantSettings existing = tenantSettingsDAO.findByTenantId(tenantId)
                .orElse(defaultSettings(tenantId));

        TenantSettings updated = TenantSettings.builder()
                .fkTenantId(tenantId)
                .withholdingRatePrimary(
                        dto.getWithholdingRatePrimary() != null
                                ? dto.getWithholdingRatePrimary()
                                : existing.getWithholdingRatePrimary())
                .withholdingRateSecondary(
                        dto.getWithholdingRateSecondary() != null
                                ? dto.getWithholdingRateSecondary()
                                : existing.getWithholdingRateSecondary())
                .codiceTributoF24(
                        dto.getCodiceTributoF24() != null
                                ? dto.getCodiceTributoF24()
                                : existing.getCodiceTributoF24())
                .documentWindowDays(
                        dto.getDocumentWindowDays() != null
                                ? dto.getDocumentWindowDays()
                                : existing.getDocumentWindowDays())
                .cedolareSeccaEnabled(
                        dto.getCedolareSeccaEnabled() != null
                                ? dto.getCedolareSeccaEnabled()
                                : existing.getCedolareSeccaEnabled())
                .bolloImporto(
                        dto.getBolloImporto() != null
                                ? dto.getBolloImporto()
                                : existing.getBolloImporto())
                .bolloSoglia(
                        dto.getBolloSoglia() != null
                                ? dto.getBolloSoglia()
                                : existing.getBolloSoglia())
                .bolloAddebitatoCliente(
                        dto.getBolloAddebitatoCliente() != null
                                ? dto.getBolloAddebitatoCliente()
                                : existing.getBolloAddebitatoCliente())
                .regimeFiscalePm(
                        dto.getRegimeFiscalePm() != null
                                ? dto.getRegimeFiscalePm()
                                : existing.getRegimeFiscalePm())
                .naturaIvaEsente(
                        dto.getNaturaIvaEsente() != null
                                ? dto.getNaturaIvaEsente()
                                : existing.getNaturaIvaEsente())
                .sdiAutoSend(
                        dto.getSdiAutoSend() != null
                                ? dto.getSdiAutoSend()
                                : existing.getSdiAutoSend())
                .derogaRicevutaEnabled(
                        dto.getDerogaRicevutaEnabled() != null
                                ? dto.getDerogaRicevutaEnabled()
                                : existing.getDerogaRicevutaEnabled())
                .numerazioneAutomatica(
                        dto.getNumerazioneAutomatica() != null
                                ? dto.getNumerazioneAutomatica()
                                : existing.getNumerazioneAutomatica())
                .alertScadenzeDocumenti(
                        dto.getAlertScadenzeDocumenti() != null
                                ? dto.getAlertScadenzeDocumenti()
                                : existing.getAlertScadenzeDocumenti())
                .alertScadenzeF24(
                        dto.getAlertScadenzeF24() != null
                                ? dto.getAlertScadenzeF24()
                                : existing.getAlertScadenzeF24())
                .notificheEmail(
                        dto.getNotificheEmail() != null
                                ? dto.getNotificheEmail()
                                : existing.getNotificheEmail())
                .build();

        TenantSettings saved = tenantSettingsDAO.save(updated);
        return toDTO(tenant, saved);
    }

    private boolean hasTenantDataChanges(TenantSettingsUpdateDTO dto) {
        return dto.getLegalName() != null
                || dto.getDisplayName() != null
                || dto.getTaxCode() != null
                || dto.getVatNumber() != null
                || dto.getAdministrativeEmail() != null
                || dto.getPec() != null
                || dto.getPhone() != null
                || dto.getLegalAddress() != null;
    }

    private TenantSettings defaultSettings(Integer tenantId) {
        return TenantSettings.builder()
                .fkTenantId(tenantId)
                .withholdingRatePrimary(new BigDecimal("21.00"))
                .withholdingRateSecondary(new BigDecimal("26.00"))
                .codiceTributoF24("1919")
                .documentWindowDays(12)
                .cedolareSeccaEnabled(true)
                .bolloImporto(new BigDecimal("2.00"))
                .bolloSoglia(new BigDecimal("77.47"))
                .bolloAddebitatoCliente(true)
                .regimeFiscalePm("RF01")
                .naturaIvaEsente("N2.1")
                .sdiAutoSend(true)
                .derogaRicevutaEnabled(false)
                .numerazioneAutomatica(true)
                .alertScadenzeDocumenti(true)
                .alertScadenzeF24(true)
                .notificheEmail(true)
                .build();
    }

    private TenantSettingsDTO toDTO(Tenant tenant, TenantSettings s) {
        return TenantSettingsDTO.builder()
                .legalName(tenant.getLegalName())
                .displayName(tenant.getDisplayName())
                .taxCode(tenant.getTaxCode())
                .vatNumber(tenant.getVatNumber())
                .administrativeEmail(tenant.getAdministrativeEmail())
                .pec(tenant.getPec())
                .phone(tenant.getPhone())
                .legalAddress(tenant.getLegalAddress())
                .withholdingRatePrimary(s.getWithholdingRatePrimary())
                .withholdingRateSecondary(s.getWithholdingRateSecondary())
                .codiceTributoF24(s.getCodiceTributoF24())
                .documentWindowDays(s.getDocumentWindowDays())
                .cedolareSeccaEnabled(s.getCedolareSeccaEnabled())
                .bolloImporto(s.getBolloImporto())
                .bolloSoglia(s.getBolloSoglia())
                .bolloAddebitatoCliente(s.getBolloAddebitatoCliente())
                .regimeFiscalePm(s.getRegimeFiscalePm())
                .naturaIvaEsente(s.getNaturaIvaEsente())
                .sdiAutoSend(s.getSdiAutoSend())
                .derogaRicevutaEnabled(s.getDerogaRicevutaEnabled())
                .numerazioneAutomatica(s.getNumerazioneAutomatica())
                .alertScadenzeDocumenti(s.getAlertScadenzeDocumenti())
                .alertScadenzeF24(s.getAlertScadenzeF24())
                .notificheEmail(s.getNotificheEmail())
                .build();
    }
}

package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.TenantSettingsRowMapper;
import it.gavia.sostitutoincloud.model.TenantSettings;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Log4j2
@Repository
public class TenantSettingsDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_tenant_id, withholding_rate_primary, withholding_rate_secondary, " +
            "codice_tributo_f24, document_window_days, cedolare_secca_enabled, " +
            "bollo_importo, bollo_soglia, bollo_addebitato_cliente, " +
            "regime_fiscale_pm, natura_iva_esente, " +
            "sdi_auto_send, deroga_ricevuta_enabled, numerazione_automatica, " +
            "alert_scadenze_documenti, alert_scadenze_f24, notifiche_email, " +
            "created_at, updated_at FROM tenant_settings";

    private final JdbcTemplate jdbcTemplate;
    private final TenantSettingsRowMapper rowMapper = new TenantSettingsRowMapper();

    public TenantSettingsDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TenantSettings> findByTenantId(Integer tenantId) {
        log.debug("TenantSettingsDAO.findByTenantId() - tenantId={}", tenantId);
        List<TenantSettings> result = jdbcTemplate.query(
                SELECT_ALL + " WHERE fk_tenant_id = ?", rowMapper, tenantId);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public TenantSettings save(TenantSettings s) {
        log.debug("TenantSettingsDAO.save() - tenantId={}", s.getFkTenantId());
        String sql =
            "INSERT INTO tenant_settings (" +
            "  fk_tenant_id, withholding_rate_primary, withholding_rate_secondary, " +
            "  codice_tributo_f24, document_window_days, cedolare_secca_enabled, " +
            "  bollo_importo, bollo_soglia, bollo_addebitato_cliente, " +
            "  regime_fiscale_pm, natura_iva_esente, " +
            "  sdi_auto_send, deroga_ricevuta_enabled, numerazione_automatica, " +
            "  alert_scadenze_documenti, alert_scadenze_f24, notifiche_email" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
            " ON CONFLICT (fk_tenant_id) DO UPDATE SET" +
            "  withholding_rate_primary    = EXCLUDED.withholding_rate_primary," +
            "  withholding_rate_secondary  = EXCLUDED.withholding_rate_secondary," +
            "  codice_tributo_f24          = EXCLUDED.codice_tributo_f24," +
            "  document_window_days        = EXCLUDED.document_window_days," +
            "  cedolare_secca_enabled      = EXCLUDED.cedolare_secca_enabled," +
            "  bollo_importo               = EXCLUDED.bollo_importo," +
            "  bollo_soglia                = EXCLUDED.bollo_soglia," +
            "  bollo_addebitato_cliente    = EXCLUDED.bollo_addebitato_cliente," +
            "  regime_fiscale_pm           = EXCLUDED.regime_fiscale_pm," +
            "  natura_iva_esente           = EXCLUDED.natura_iva_esente," +
            "  sdi_auto_send               = EXCLUDED.sdi_auto_send," +
            "  deroga_ricevuta_enabled     = EXCLUDED.deroga_ricevuta_enabled," +
            "  numerazione_automatica      = EXCLUDED.numerazione_automatica," +
            "  alert_scadenze_documenti    = EXCLUDED.alert_scadenze_documenti," +
            "  alert_scadenze_f24          = EXCLUDED.alert_scadenze_f24," +
            "  notifiche_email             = EXCLUDED.notifiche_email," +
            "  updated_at                  = NOW()" +
            " RETURNING " +
            "  id, fk_tenant_id, withholding_rate_primary, withholding_rate_secondary, " +
            "  codice_tributo_f24, document_window_days, cedolare_secca_enabled, " +
            "  bollo_importo, bollo_soglia, bollo_addebitato_cliente, " +
            "  regime_fiscale_pm, natura_iva_esente, " +
            "  sdi_auto_send, deroga_ricevuta_enabled, numerazione_automatica, " +
            "  alert_scadenze_documenti, alert_scadenze_f24, notifiche_email, " +
            "  created_at, updated_at";

        return jdbcTemplate.queryForObject(sql, rowMapper,
                s.getFkTenantId(),
                s.getWithholdingRatePrimary(), s.getWithholdingRateSecondary(),
                s.getCodiceTributoF24(), s.getDocumentWindowDays(),
                s.getCedolareSeccaEnabled(),
                s.getBolloImporto(), s.getBolloSoglia(), s.getBolloAddebitatoCliente(),
                s.getRegimeFiscalePm(), s.getNaturaIvaEsente(),
                s.getSdiAutoSend(),
                s.getDerogaRicevutaEnabled(), s.getNumerazioneAutomatica(),
                s.getAlertScadenzeDocumenti(), s.getAlertScadenzeF24(),
                s.getNotificheEmail());
    }
}

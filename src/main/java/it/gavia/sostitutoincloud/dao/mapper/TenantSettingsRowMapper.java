package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.TenantSettings;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TenantSettingsRowMapper implements RowMapper<TenantSettings> {

    @Override
    public TenantSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
        return TenantSettings.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .withholdingRatePrimary(rs.getBigDecimal("withholding_rate_primary"))
                .withholdingRateSecondary(rs.getBigDecimal("withholding_rate_secondary"))
                .codiceTributoF24(rs.getString("codice_tributo_f24"))
                .documentWindowDays(rs.getInt("document_window_days"))
                .cedolareSeccaEnabled(rs.getBoolean("cedolare_secca_enabled"))
                .bolloImporto(rs.getBigDecimal("bollo_importo"))
                .bolloSoglia(rs.getBigDecimal("bollo_soglia"))
                .bolloAddebitatoCliente(rs.getBoolean("bollo_addebitato_cliente"))
                .regimeFiscalePm(rs.getString("regime_fiscale_pm"))
                .naturaIvaEsente(rs.getString("natura_iva_esente"))
                .sdiAutoSend(rs.getBoolean("sdi_auto_send"))
                .derogaRicevutaEnabled(rs.getBoolean("deroga_ricevuta_enabled"))
                .numerazioneAutomatica(rs.getBoolean("numerazione_automatica"))
                .alertScadenzeDocumenti(rs.getBoolean("alert_scadenze_documenti"))
                .alertScadenzeF24(rs.getBoolean("alert_scadenze_f24"))
                .notificheEmail(rs.getBoolean("notifiche_email"))
                .createdAt(rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                .updatedAt(rs.getTimestamp("updated_at") != null
                        ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                .build();
    }
}

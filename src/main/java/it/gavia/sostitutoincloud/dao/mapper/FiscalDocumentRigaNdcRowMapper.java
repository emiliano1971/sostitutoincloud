package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.FiscalDocumentRigaNdc;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class FiscalDocumentRigaNdcRowMapper implements RowMapper<FiscalDocumentRigaNdc> {

    @Override
    public FiscalDocumentRigaNdc mapRow(ResultSet rs, int rowNum) throws SQLException {
        return FiscalDocumentRigaNdc.builder()
                .id(rs.getInt("id"))
                .fkFiscalDocumentId(rs.getInt("fk_fiscal_document_id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkSplitEconomicoId(rs.getObject("fk_split_economico_id", Integer.class))
                .descrizione(rs.getString("descrizione"))
                .importoStornato(rs.getBigDecimal("importo_stornato"))
                .imponibileStornato(rs.getBigDecimal("imponibile_stornato"))
                .aliquotaIva(rs.getBigDecimal("aliquota_iva"))
                .ordinamento(rs.getInt("ordinamento"))
                .createdAt(rs.getTimestamp("created_at") != null
                        ? rs.getTimestamp("created_at").toLocalDateTime() : null)
                .updatedAt(rs.getTimestamp("updated_at") != null
                        ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
                .createdBy(rs.getObject("created_by", Integer.class))
                .build();
    }
}

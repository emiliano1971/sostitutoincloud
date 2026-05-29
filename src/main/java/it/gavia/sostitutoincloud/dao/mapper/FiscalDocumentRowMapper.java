package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.FiscalDocument;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Log4j2
public class FiscalDocumentRowMapper implements RowMapper<FiscalDocument> {

    @Override
    public FiscalDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
        return FiscalDocument.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkBookingId(rs.getInt("fk_booking_id"))
                .fkTipoDocumentoId(rs.getInt("fk_tipo_documento_id"))
                .fkSdiEsitoId(rs.getObject("fk_sdi_esito_id", Integer.class))
                .documentNumber(rs.getString("document_number"))
                .issueDate(rs.getObject("issue_date", LocalDate.class))
                .recipientName(rs.getString("recipient_name"))
                .recipientTaxCode(rs.getString("recipient_tax_code"))
                .totalAmount(rs.getBigDecimal("total_amount"))
                .vatAmount(rs.getBigDecimal("vat_amount"))
                .fkStatoDocumentoId(rs.getInt("fk_stato_documento_id"))
                .sdiIdentifier(rs.getString("sdi_identifier"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}

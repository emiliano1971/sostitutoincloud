package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.BookingSplitEconomico;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class BookingSplitEconomicoRowMapper implements RowMapper<BookingSplitEconomico> {

    @Override
    public BookingSplitEconomico mapRow(ResultSet rs, int rowNum) throws SQLException {
        return BookingSplitEconomico.builder()
                .id(rs.getInt("id"))
                .fkBookingId(rs.getInt("fk_booking_id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkPropertyContractRuleId(rs.getObject("fk_property_contract_rule_id", Integer.class))
                .tipoVoce(rs.getString("tipo_voce"))
                .descrizione(rs.getString("descrizione"))
                .importo(rs.getBigDecimal("importo"))
                .aliquotaIva(rs.getBigDecimal("aliquota_iva"))
                .includeInFatturaPm(rs.getBoolean("include_in_fattura_pm"))
                .ordinamento(rs.getInt("ordinamento"))
                .source(rs.getString("source"))
                .deletedAt(rs.getObject("deleted_at", LocalDateTime.class))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .createdBy(rs.getObject("created_by", Integer.class))
                .updatedBy(rs.getObject("updated_by", Integer.class))
                .build();
    }
}

package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.WithholdingLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Log4j2
public class WithholdingLedgerRowMapper implements RowMapper<WithholdingLedger> {

    @Override
    public WithholdingLedger mapRow(ResultSet rs, int rowNum) throws SQLException {
        return WithholdingLedger.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkOwnerId(rs.getInt("fk_owner_id"))
                .fkBookingId(rs.getInt("fk_booking_id"))
                .fkFiscalDocumentId(rs.getInt("fk_fiscal_document_id"))
                .periodoMese(rs.getObject("periodo_mese", Integer.class))
                .periodoAnno(rs.getObject("periodo_anno", Integer.class))
                .canoneLocazione(rs.getBigDecimal("canone_locazione"))
                .aliquotaRitenuta(rs.getBigDecimal("aliquota_ritenuta"))
                .ritenutaAmount(rs.getBigDecimal("ritenuta_amount"))
                .dataEvento(rs.getObject("data_evento", LocalDate.class))
                .stato(rs.getString("stato"))
                .fkF24RecordId(rs.getObject("fk_f24_record_id", Integer.class))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}

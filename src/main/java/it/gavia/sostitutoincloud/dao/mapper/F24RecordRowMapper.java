package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.F24Record;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Log4j2
public class F24RecordRowMapper implements RowMapper<F24Record> {

    @Override
    public F24Record mapRow(ResultSet rs, int rowNum) throws SQLException {
        return F24Record.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkCodiceTributoId(rs.getInt("fk_codice_tributo_id"))
                .period(rs.getString("period"))
                .totalAmount(rs.getBigDecimal("total_amount"))
                .withholdingsCount(rs.getInt("withholdings_count"))
                .stato(rs.getString("stato"))
                .deadlineDate(rs.getObject("deadline_date", LocalDate.class))
                .paymentDate(rs.getObject("payment_date", LocalDate.class))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}

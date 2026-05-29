package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.Settlement;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Log4j2
public class SettlementRowMapper implements RowMapper<Settlement> {

    @Override
    public Settlement mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Settlement.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkOwnerId(rs.getInt("fk_owner_id"))
                .period(rs.getString("period"))
                .totalAmount(rs.getBigDecimal("total_amount"))
                .withholdingAmount(rs.getBigDecimal("withholding_amount"))
                .netAmount(rs.getBigDecimal("net_amount"))
                .stato(rs.getString("stato"))
                .paymentDate(rs.getObject("payment_date", LocalDate.class))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}

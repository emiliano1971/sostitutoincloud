package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.Tenant;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Log4j2
public class TenantRowMapper implements RowMapper<Tenant> {

    @Override
    public Tenant mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Tenant.builder()
                .id(rs.getInt("id"))
                .legalName(rs.getString("legal_name"))
                .displayName(rs.getString("display_name"))
                .taxCode(rs.getString("tax_code"))
                .vatNumber(rs.getString("vat_number"))                          // nullable
                .stato(rs.getString("stato"))
                .administrativeEmail(rs.getString("administrative_email"))
                .pec(rs.getString("pec"))                                       // nullable
                .phone(rs.getString("phone"))                                   // nullable
                .legalAddress(rs.getString("legal_address"))
                .activatedAt(rs.getObject("activated_at", LocalDate.class))     // nullable DATE
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}

package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.OwnerProfile;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class OwnerProfileRowMapper implements RowMapper<OwnerProfile> {

    @Override
    public OwnerProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
        return OwnerProfile.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .ownerType(rs.getString("owner_type"))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .legalName(rs.getString("legal_name"))
                .taxCode(rs.getString("tax_code"))
                .vatNumber(rs.getString("vat_number"))
                .fkRegimeFiscaleId(rs.getInt("fk_regime_fiscale_id"))
                .email(rs.getString("email"))
                .phone(rs.getString("phone"))
                .iban(rs.getString("iban"))
                .attivo(rs.getBoolean("attivo"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}

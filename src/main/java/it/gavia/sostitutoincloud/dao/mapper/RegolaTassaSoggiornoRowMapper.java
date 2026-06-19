package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.RegolaTassaSoggiorno;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Log4j2
public class RegolaTassaSoggiornoRowMapper implements RowMapper<RegolaTassaSoggiorno> {

    @Override
    public RegolaTassaSoggiorno mapRow(ResultSet rs, int rowNum) throws SQLException {
        return RegolaTassaSoggiorno.builder()
                .id(rs.getInt("id"))
                .comune(rs.getString("comune"))
                .provincia(rs.getString("provincia"))
                .importoPerNotte((java.math.BigDecimal) rs.getObject("importo_per_notte"))
                .maxNotti(rs.getInt("max_notti"))
                .etaEsenzione((Integer) rs.getObject("eta_esenzione"))
                .validaDal(rs.getObject("valida_dal", LocalDate.class))
                .validaAl(rs.getObject("valida_al", LocalDate.class))
                .attivo(rs.getBoolean("attivo"))
                .region(rs.getString("region"))
                .maxAmountPerPerson((java.math.BigDecimal) rs.getObject("max_amount_per_person"))
                .exemptions(rs.getString("exemptions"))
                .notes(rs.getString("notes"))
                .fkTenantId((Integer) rs.getObject("fk_tenant_id"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}

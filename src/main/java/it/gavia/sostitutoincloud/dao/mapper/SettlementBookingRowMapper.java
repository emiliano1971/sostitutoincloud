package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.SettlementBooking;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Log4j2
public class SettlementBookingRowMapper implements RowMapper<SettlementBooking> {

    @Override
    public SettlementBooking mapRow(ResultSet rs, int rowNum) throws SQLException {
        return SettlementBooking.builder()
                .id(rs.getInt("id"))
                .fkSettlementId(rs.getInt("fk_settlement_id"))
                .fkBookingId(rs.getInt("fk_booking_id"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .build();
    }
}

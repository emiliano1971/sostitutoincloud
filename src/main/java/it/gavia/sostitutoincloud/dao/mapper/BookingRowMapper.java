package it.gavia.sostitutoincloud.dao.mapper;

import it.gavia.sostitutoincloud.model.Booking;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Log4j2
public class BookingRowMapper implements RowMapper<Booking> {

    @Override
    public Booking mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Booking.builder()
                .id(rs.getInt("id"))
                .fkTenantId(rs.getInt("fk_tenant_id"))
                .fkPropertyId(rs.getInt("fk_property_id"))
                .fkOwnerId(rs.getObject("fk_owner_id", Integer.class))
                .fkCanaleOtaId(rs.getObject("fk_canale_ota_id", Integer.class))
                .fkScenarioFiscaleId(rs.getObject("fk_scenario_fiscale_id", Integer.class))
                .externalBookingId(rs.getString("external_booking_id"))
                .guestName(rs.getString("guest_name"))
                .guestTaxCode(rs.getString("guest_tax_code"))
                .checkinDate(rs.getObject("checkin_date", LocalDate.class))
                .checkoutDate(rs.getObject("checkout_date", LocalDate.class))
                .nights(rs.getInt("nights"))
                .guests(rs.getInt("guests"))
                .grossAmount(rs.getBigDecimal("gross_amount"))
                .otaCommissionAmount(rs.getObject("ota_commission_amount", BigDecimal.class))
                .cleaningAmount(rs.getObject("cleaning_amount", BigDecimal.class))
                .pmFeeAmount(rs.getObject("pm_fee_amount", BigDecimal.class))
                .ownerNetAmount(rs.getObject("owner_net_amount", BigDecimal.class))
                .withholdingAmount(rs.getObject("withholding_amount", BigDecimal.class))
                .aliquotaRitenuta(rs.getObject("aliquota_ritenuta", BigDecimal.class))
                .touristTaxAmount(rs.getObject("tourist_tax_amount", BigDecimal.class))
                .touristTaxIncludedInGross(rs.getBoolean("tourist_tax_included_in_gross"))
                .touristTaxCollection(rs.getString("tourist_tax_collection"))
                .fkStatoPrenotazioneId(rs.getInt("fk_stato_prenotazione_id"))
                .paymentStatus(rs.getString("payment_status"))
                .settlementStatus(rs.getString("settlement_status"))
                .createdAt(rs.getObject("created_at", LocalDateTime.class))
                .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
                .build();
    }
}

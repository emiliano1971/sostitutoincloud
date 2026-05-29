package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingListDTO {

    private Integer id;
    private String externalBookingId;
    private String guestName;
    private String propertyName;
    private String ownerName;
    private String channelName;
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private Integer nights;
    private Integer guests;
    private BigDecimal grossAmount;
    private BigDecimal ownerNetAmount;
    private String statoPrenotazione;
    private String paymentStatus;
    private String documentStatus;
    private String settlementStatus;
    private LocalDateTime createdAt;
}

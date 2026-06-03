package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingImportRowDTO {

    private String externalBookingId;
    private String channelCode;
    private String propertyCode;
    private String guestName;
    private String guestEmail;
    private String guestPhone;
    private String guestTaxCode;
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private Integer nights;
    private Integer guests;
    private String status;
    private BigDecimal grossAmount;
    private BigDecimal otaCommissionAmount;
    private BigDecimal cleaningAmount;
    private BigDecimal touristTaxAmount;
    private Boolean touristTaxIncluded;
    private String currency;
}

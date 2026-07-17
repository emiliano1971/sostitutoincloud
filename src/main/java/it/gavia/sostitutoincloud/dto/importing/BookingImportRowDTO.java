package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

    // V2: id risolti in fase di preview tramite match canale_ota.nome + property_ota_code.external_id
    private Integer fkPropertyId;
    private Integer fkCanaleOtaId;

    // V2: dati ospite dal merge con il secondo file (per BOOKING_ID)
    private String guestFirstName;
    private String guestLastName;
    private String guestBirthDate;
    private String guestGender;
    private String guestBirthPlace;
    private String guestDocType;
    private String guestDocNumber;
    private String guestCountry;

    // V2: avvisi del calcolo split economico
    private List<String> splitWarnings;
}

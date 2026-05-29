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
public class BookingDetailDTO {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkPropertyId;
    private Integer fkOwnerId;
    private String externalBookingId;
    private String guestName;
    private String guestTaxCode;
    private String propertyName;
    private String ownerName;
    private String channelName;
    private String fiscalScenarioCode;
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private Integer nights;
    private Integer guests;
    private BigDecimal grossAmount;
    private BigDecimal otaCommissionAmount;
    private BigDecimal cleaningAmount;
    private BigDecimal pmFeeAmount;
    private BigDecimal ownerNetAmount;
    private BigDecimal withholdingAmount;
    private BigDecimal touristTaxAmount;
    private Boolean touristTaxIncludedInGross;
    private String touristTaxCollection;
    private String statoPrenotazione;
    private String paymentStatus;
    private String documentStatus;
    private String settlementStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private SplitEconomicoDTO splitEconomico;
}

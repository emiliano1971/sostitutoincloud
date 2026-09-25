package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class Booking {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkPropertyId;
    private Integer fkOwnerId;
    private Integer fkCanaleOtaId;
    // Regime fiscale del proprietario al momento della prenotazione (lookup regime_fiscale
    // con metadata='REGIME_FISCALE'): fotografato qui perché l'owner può cambiarlo nel tempo.
    private Integer fkRegimeFiscaleId;
    private String externalBookingId;
    private String guestName;
    private String guestTaxCode;
    private LocalDate guestBirthDate;
    private String guestSesso;
    private String guestBirthPlace;
    private String guestBirthBelfiore;
    private String guestDocType;
    private String guestDocNumber;
    private String guestCountry;
    private String guestAddress;
    private String guestPhone;
    // Somma delle righe booking_split_economico in fattura PM (migration 018)
    private BigDecimal totalCostiPm;
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
    private BigDecimal aliquotaRitenuta;
    private BigDecimal touristTaxAmount;
    private Boolean touristTaxIncludedInGross;
    private String touristTaxCollection;
    private Integer fkStatoPrenotazioneId;
    private String paymentStatus;
    private String settlementStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

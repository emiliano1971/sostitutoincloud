package it.gavia.sostitutoincloud.dto.booking;

import it.gavia.sostitutoincloud.dto.document.FiscalDocumentSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    private java.time.LocalDate guestBirthDate;
    private String guestSesso;
    private String guestBirthPlace;
    private String guestBirthBelfiore;
    private String guestDocType;
    private String guestDocNumber;
    private String guestCountry;
    private String guestAddress;
    private String guestPhone;
    private String propertyName;
    private String ownerName;
    private String channelName;
    private String regimeFiscaleCodice;
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
    // booking.total_costi_pm: somma delle righe split in fattura PM (migration 018)
    private BigDecimal totalCostiPm;
    private Boolean touristTaxIncludedInGross;
    private String touristTaxCollection;
    private String statoPrenotazione;
    private String paymentStatus;
    private String documentStatus;
    private String settlementStatus;
    private String settlementStato;   // stato del settlement reale associato (null se nessuno)
    private Integer settlementId;     // id del settlement reale associato (null se nessuno)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private SplitEconomicoDTO splitEconomico;
    // Righe di booking_split_economico ordinate per ordinamento. In aggiunta ai campi flat
    // di splitEconomico (che restano per retrocompatibilità del frontend): lista vuota per
    // le prenotazioni create prima della migration 018.
    private List<BookingSplitEconomicoDTO> righeSplit;

    // Dati immobile (per dialog generazione documenti)
    private String propertyAddress;
    private String propertyCity;
    private String propertyInternalCode;

    // Dati proprietario (per dialog generazione documenti)
    private String ownerTaxCode;
    private String ownerIban;
    private String ownerEmail;

    // Dati tenant (per dialog fattura PM)
    private String tenantLegalName;
    private String tenantVatNumber;
    private String tenantTaxCode;
    private String tenantLegalAddress;
    private String tenantPec;

    // Documenti fiscali associati alla prenotazione
    private List<FiscalDocumentSummaryDTO> documenti;
}

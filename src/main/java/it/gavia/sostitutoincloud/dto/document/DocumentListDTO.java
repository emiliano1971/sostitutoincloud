package it.gavia.sostitutoincloud.dto.document;

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
public class DocumentListDTO {

    private Integer id;
    private String documentNumber;
    private String documentType;
    private LocalDate issueDate;
    private String recipientName;
    private String recipientTaxCode;
    private BigDecimal totalAmount;
    private BigDecimal vatAmount;
    private String statoDocumento;
    private String sdiIdentifier;
    private String sdiEsito;
    // Invio SDI (migration 010)
    private String sdiProgressivo;
    private String sdiFilePath;
    private LocalDateTime sdiSentAt;
    private String sdiErrorMsg;
    // Ultimo invio email (migration 037); null nel portale proprietario
    private LocalDateTime emailInviataAt;
    private String emailInviataA;
    /** Ultimo annullo di un invio manuale allo SDI (migration 038). */
    private LocalDateTime sdiManualeAnnullatoAt;
    private String propertyName;
    private String channelName;
    private Integer fkBookingId;
    private String externalBookingId;   // id OTA della prenotazione, per il link dalla lista
    private Integer fkOwnerId;
    private String ownerName;     // first_name + ' ' + last_name (o legalName); null se documento non collegato a booking
    private LocalDateTime createdAt;

    // Liquidazione che include la prenotazione del documento (null se non ancora liquidata)
    private Integer settlementId;
    private String settlementStato;
    // Fattura con una nota di credito attiva (non annullata) collegata
    private Boolean stornata;
    // Fattura stornata: NDC attiva collegata
    private Integer ndcId;
    private String ndcNumber;
    // NDC: fattura originale stornata
    private Integer fatturaCollegataId;
    private String fatturaCollegataNumber;
    // Fattura PM attiva: ricevuta attiva della stessa prenotazione (null se assente)
    private Integer ricevutaCollegataId;
    private String ricevutaCollegataNumber;
    // Ricevuta attiva: fattura PM attiva della stessa prenotazione (null se assente)
    private Integer fatturaDellaPrenotazioneId;
    private String fatturaDellaPrenotazioneNumber;
}

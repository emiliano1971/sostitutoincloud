package it.gavia.sostitutoincloud.dto.fiscal;

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
public class WithholdingLedgerDTO {

    private Integer id;
    private String ownerName;
    // Dati della prenotazione collegata: servono al dettaglio F24 (link alla scheda,
    // stampa) e arrivano dalla JOIN in sql/withholding_ledger/righe_f24.sql.
    private Integer bookingId;
    private String bookingExternalId;
    private String guestName;
    private String propertyName;
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private String documentNumber;
    private LocalDate dataEvento;
    private Integer periodoMese;
    private Integer periodoAnno;
    private BigDecimal canoneLocazione;
    private BigDecimal aliquotaRitenuta;
    private BigDecimal ritenutaAmount;
    private String stato;
    private Integer fkF24RecordId;
}

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
    private String bookingExternalId;
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

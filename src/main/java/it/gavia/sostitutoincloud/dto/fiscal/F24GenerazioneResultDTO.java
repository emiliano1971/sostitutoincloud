package it.gavia.sostitutoincloud.dto.fiscal;

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
public class F24GenerazioneResultDTO {

    private Integer f24RecordId;
    private Integer periodoMese;
    private Integer periodoAnno;
    private BigDecimal totaleRitenute;
    private Integer numeroRitenute;
    private LocalDate scadenza;
    private String stato;
    private List<WithholdingLedgerDTO> ritenute;
}

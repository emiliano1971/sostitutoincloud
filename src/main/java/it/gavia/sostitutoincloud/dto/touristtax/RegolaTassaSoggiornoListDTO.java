package it.gavia.sostitutoincloud.dto.touristtax;

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
public class RegolaTassaSoggiornoListDTO {

    private Integer id;
    private String comune;
    private String provincia;
    private String region;
    private BigDecimal importoPerNotte;
    private Integer maxNotti;
    private BigDecimal maxAmountPerPerson;
    private Boolean attivo;
    private LocalDate validaDal;
    private LocalDate validaAl;
    private Integer fascieEtaCount;
    private Integer stagioniCount;
    private Integer zoneCount;
}

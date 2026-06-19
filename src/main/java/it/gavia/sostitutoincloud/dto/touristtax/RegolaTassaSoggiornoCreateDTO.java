package it.gavia.sostitutoincloud.dto.touristtax;

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
public class RegolaTassaSoggiornoCreateDTO {

    private String comune;            // obbligatorio
    private String provincia;         // obbligatorio, 2 char
    private String region;
    private BigDecimal importoPerNotte; // obbligatorio
    private Integer maxNotti;
    private BigDecimal maxAmountPerPerson;
    private LocalDate validaDal;      // obbligatorio
    private LocalDate validaAl;
    private String exemptions;
    private String notes;
    private List<TassaFasciaEtaDTO> fascieEta;
    private List<TassaStagioneDTO> stagioni;
    private List<TassaZonaDTO> zone;
}

package it.gavia.sostitutoincloud.dto.touristtax;

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
public class RegolaTassaSoggiornoDetailDTO {

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

    private List<TassaFasciaEtaDTO> fascieEta;
    private List<TassaStagioneDTO> stagioni;
    private List<TassaZonaDTO> zone;
    private String exemptions;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

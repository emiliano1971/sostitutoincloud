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
public class RegolaTassaSoggiorno {

    private Integer id;
    private String comune;
    private String provincia;
    private BigDecimal importoPerNotte;
    private Integer maxNotti;
    private Integer etaEsenzione;
    private LocalDate validaDal;
    private LocalDate validaAl;
    private Boolean attivo;
    private String region;
    private BigDecimal maxAmountPerPerson;
    private String exemptions;
    private String notes;
    private Integer fkTenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

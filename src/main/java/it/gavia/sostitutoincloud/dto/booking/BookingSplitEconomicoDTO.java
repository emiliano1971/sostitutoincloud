package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Riga dello split economico di una prenotazione esposta dalle API. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSplitEconomicoDTO {

    private Integer id;
    private Integer fkBookingId;
    private Integer fkPropertyContractRuleId;   // null per le righe manuali / da import
    private String tipoVoce;
    private String descrizione;
    private BigDecimal importo;
    private BigDecimal aliquotaIva;
    private Boolean includeInFatturaPm;
    private Integer ordinamento;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

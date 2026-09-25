package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Riga di costo dello split economico di una prenotazione (tabella booking_split_economico).
 * Le righe con includeInFatturaPm=true entrano nella fattura PM; deletedAt valorizzato = riga
 * eliminata logicamente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class BookingSplitEconomico {

    private Integer id;
    private Integer fkBookingId;
    private Integer fkTenantId;
    private Integer fkPropertyContractRuleId;   // null per le righe manuali / da import
    private String tipoVoce;                    // commissione_ota | pulizie | cambio_biancheria | commissione_pm | extra | tassa_soggiorno
    private String descrizione;
    private BigDecimal importo;
    private BigDecimal aliquotaIva;
    private Boolean includeInFatturaPm;
    private Integer ordinamento;
    private String source;                      // calcolato | manuale | import
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer createdBy;
    private Integer updatedBy;
}

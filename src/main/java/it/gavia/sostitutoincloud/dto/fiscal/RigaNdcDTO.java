package it.gavia.sostitutoincloud.dto.fiscal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Riga di nota di credito esposta nel dettaglio booking e documento: importi POSITIVI. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RigaNdcDTO {

    private Integer id;
    private Integer fkFiscalDocumentId;
    private String documentNumber;
    private Integer fkSplitEconomicoId;
    private String descrizione;
    private BigDecimal importoStornato;
    private BigDecimal imponibileStornato;
    private BigDecimal aliquotaIva;
    private Integer ordinamento;
}

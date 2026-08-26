package it.gavia.sostitutoincloud.dto.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Riga "Locazioni brevi" della CU (pagina 15): un immobile del proprietario. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuLocazioneBreviDTO {

    private String comune;
    private String codiceComuneBelfiore;
    private String importoCorrespettivo;
    private String ritenuta;
    private String codiceCin;
}

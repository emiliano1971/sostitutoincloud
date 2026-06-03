package it.gavia.sostitutoincloud.dto.lookup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LookupItemDTO {

    private Integer id;
    private String codice;
    private String descrizione;
    private Boolean attivo;
}

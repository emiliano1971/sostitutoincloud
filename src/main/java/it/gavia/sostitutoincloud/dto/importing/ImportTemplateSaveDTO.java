package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportTemplateSaveDTO {

    private Integer id;
    private String nome;            // obbligatorio
    private String descrizione;     // opzionale
    private Integer headerRow;      // default 0
    private Map<String, String> bookingMapping;
    private Map<String, String> guestMapping;
}

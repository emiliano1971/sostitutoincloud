package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body della richiesta di anteprima import V2: sessioni file raw + mapping colonne.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewV2RequestDTO {

    private String bookingSessionId;
    private String guestSessionId;
    private ImportColumnMappingDTO mapping;
}

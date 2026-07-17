package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body della richiesta di aggiornamento stato documento: { "stato": "sent_sdi" }.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStatoUpdateDTO {

    private String stato;
}

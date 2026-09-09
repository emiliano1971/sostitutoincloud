package it.gavia.sostitutoincloud.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di cleanup di un modello F24 generato da un test.
 * L'F24 deve appartenere al tenant del chiamante.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupF24DTO {

    private Integer f24Id;

    /**
     * Consente di eliminare anche un F24 in stato 'paid'. Di norma è false — un F24
     * pagato non si tocca — ma il test della fase 05 marca come pagato l'F24 che ha
     * appena generato lui e deve poterlo rimuovere per restare ripetibile.
     */
    private Boolean forzaSePagato;
}

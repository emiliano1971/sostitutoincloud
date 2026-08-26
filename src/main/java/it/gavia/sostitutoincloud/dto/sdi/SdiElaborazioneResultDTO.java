package it.gavia.sostitutoincloud.dto.sdi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Esito di un giro di elaborazione delle risposte SDI presenti in incoming/.
 *
 * `dettagli` contiene solo le voci che l'utente deve leggere (scarti, mancate consegne,
 * file ignorati, documenti non trovati): le consegne andate a buon fine non generano
 * righe, così il frontend può mostrare il dialog solo quando c'è davvero qualcosa.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SdiElaborazioneResultDTO {

    private int elaborati;
    private int accettati;
    private int scartati;
    private int metadati;
    private int errori;
    private List<String> dettagli;
}

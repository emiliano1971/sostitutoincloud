package it.gavia.sostitutoincloud.service;

import java.util.List;

/**
 * Eliminazione di un documento fiscale non consentita: porta l'elenco dei blocchi, ricalcolati
 * dentro il lock di numerazione. DocumentController la mappa su 422 con i blocchi nel body.
 */
public class EliminazioneNonConsentitaException extends IllegalStateException {

    private final List<String> blocchi;

    public EliminazioneNonConsentitaException(List<String> blocchi) {
        super("Eliminazione non consentita: " + String.join("; ", blocchi));
        this.blocchi = List.copyOf(blocchi);
    }

    public List<String> getBlocchi() {
        return blocchi;
    }
}

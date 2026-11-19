package it.gavia.sostitutoincloud.service;

import java.util.List;

/**
 * Annullo dell'invio manuale allo SDI non consentito: porta l'elenco dei blocchi, ricalcolati
 * dentro la transazione. DocumentController la mappa su 422 con i blocchi nel body.
 */
public class AnnulloNonConsentitoException extends IllegalStateException {

    private final List<String> blocchi;

    public AnnulloNonConsentitoException(List<String> blocchi) {
        super("Annullo invio manuale non consentito: " + String.join("; ", blocchi));
        this.blocchi = List.copyOf(blocchi);
    }

    public List<String> getBlocchi() {
        return blocchi;
    }
}

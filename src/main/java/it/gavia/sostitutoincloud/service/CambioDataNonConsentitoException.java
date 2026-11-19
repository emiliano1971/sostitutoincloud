package it.gavia.sostitutoincloud.service;

import java.util.List;

/**
 * Cambio della data di un documento non consentito: porta l'elenco dei blocchi, ricalcolati
 * dentro il lock di numerazione. DocumentController la mappa su 422 con i blocchi nel body.
 */
public class CambioDataNonConsentitoException extends IllegalStateException {

    private final List<String> blocchi;

    public CambioDataNonConsentitoException(List<String> blocchi) {
        super("Cambio data non consentito: " + String.join("; ", blocchi));
        this.blocchi = List.copyOf(blocchi);
    }

    public List<String> getBlocchi() {
        return blocchi;
    }
}

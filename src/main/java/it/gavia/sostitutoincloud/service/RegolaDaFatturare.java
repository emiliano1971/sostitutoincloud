package it.gavia.sostitutoincloud.service;

import java.time.LocalDate;
import java.util.Set;

/**
 * Regola unica "prenotazione da fatturare", usata dal conteggio della dashboard
 * (DashboardService) e dal filtro stati=da_fatturare della lista prenotazioni (BookingService):
 * i due numeri devono coincidere.
 *
 * <p>Da fatturare = check-out passato (strettamente prima di oggi), nessuna fattura PM attiva
 * (non annullata), stato ready / doc_issued / settled. doc_issued e settled coprono le
 * prenotazioni con la sola ricevuta (es. fattura eliminata lasciando la ricevuta); restano
 * escluse stornata, cancelled e gli stati precedenti allo split calcolato.
 */
public final class RegolaDaFatturare {

    /** Valore speciale del filtro stati della lista prenotazioni. */
    public static final String FILTRO = "da_fatturare";

    public static final Set<String> STATI_PRENOTAZIONE = Set.of("ready", "doc_issued", "settled");

    private RegolaDaFatturare() { }

    public static boolean daFatturare(String codiceStatoPrenotazione, LocalDate checkout,
                                      boolean haFatturaAttiva, LocalDate oggi) {
        return codiceStatoPrenotazione != null
                && STATI_PRENOTAZIONE.contains(codiceStatoPrenotazione)
                && checkout != null
                && checkout.isBefore(oggi)
                && !haFatturaAttiva;
    }
}

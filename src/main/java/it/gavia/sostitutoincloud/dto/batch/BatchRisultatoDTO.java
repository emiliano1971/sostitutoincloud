package it.gavia.sostitutoincloud.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Esito di un'azione massiva: prenotazioni elaborate / scartate con il motivo. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRisultatoDTO {
    private int elaborati;
    private int scartati;
    @Builder.Default
    private List<String> dettagliScartati = new ArrayList<>();
    /** Segnalazioni non bloccanti sul lotto, es. "2 fatture con data anteriore al check-out: …" */
    @Builder.Default
    private List<String> avvisi = new ArrayList<>();

    public void elaborato() {
        elaborati++;
    }

    /** Prenotazione scartata, es. "Booking BC001: documenti già emessi" */
    public void scarta(String codiceBooking, String motivo) {
        scartaVoce("Booking " + codiceBooking, motivo);
    }

    /** Avviso sul lotto: non conta come scarto. */
    public void avvisa(String messaggio) {
        avvisi.add(messaggio);
    }

    /** Voce scartata con etichetta libera, es. "FT-2026-0003: fattura stornata da nota di credito" */
    public void scartaVoce(String etichetta, String motivo) {
        scartati++;
        dettagliScartati.add(etichetta + ": " + motivo);
    }
}

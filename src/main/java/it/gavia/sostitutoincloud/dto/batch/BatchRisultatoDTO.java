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

    public void elaborato() {
        elaborati++;
    }

    /** Prenotazione scartata, es. "Booking BC001: documenti già emessi" */
    public void scarta(String codiceBooking, String motivo) {
        scartaVoce("Booking " + codiceBooking, motivo);
    }

    /** Voce scartata con etichetta libera, es. "FT-2026-0003: fattura stornata da nota di credito" */
    public void scartaVoce(String etichetta, String motivo) {
        scartati++;
        dettagliScartati.add(etichetta + ": " + motivo);
    }
}

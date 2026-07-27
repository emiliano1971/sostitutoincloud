package it.gavia.sostitutoincloud.dto.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Dati per la compilazione dei campi AcroForm del modello F24 Semplificato. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class F24PdfDataDTO {

    // Contribuente (PM — sostituto d'imposta)
    private String contribuenteCf;
    private String contribuenteCognomeDenominazione;
    private String contribuenteNome;
    private String contribuenteDataNascitaGg;   // "01"
    private String contribuenteDataNascitaMm;   // "01"
    private String contribuenteDataNascitaAa;   // "1980"
    private String contribuenteSesso;           // "M" o "F"
    private String contribuenteComuneNascita;
    private String contribuenteProvincia;

    // Motivo del pagamento (una riga)
    private String motivoSezione;               // "Erario"
    private String motivoCodTributo;            // "1919"
    private String motivoCodiceEnte;            // vuoto
    private String motivoMeseRif;               // "06"
    private String motivoAnnoRif;               // "2026"
    private String motivoImportoDebito;         // "97,44"
    private String motivoImportoCredito;        // vuoto

    // Saldo finale
    private String saldoFinaleEuro;             // "97"
    private String saldoFinaleCent;             // "44"
}

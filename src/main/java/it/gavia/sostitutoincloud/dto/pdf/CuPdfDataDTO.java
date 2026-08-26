package it.gavia.sostitutoincloud.dto.pdf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Dati per la compilazione dell'AcroForm del modello CU ordinario. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuPdfDataDTO {

    // Sostituto d'imposta (PM) — pagina 3
    private String sostitutoCf;
    private String sostitutoDenominazione;
    private String sostitutiIndirizzo;
    private String sostitutiComune;
    private String sostitutiProvincia;
    private String sostitutiCap;

    // Percipiente (proprietario) — pagina 3
    private String percipienteCf;
    private String percipienteCognome;
    private String percipientiNome;
    private String percipientiDataNascitaGg;
    private String percipientiDataNascitaMm;
    private String percipientiDataNascitaAa;
    private String percipientiSesso;
    private String percipientiComuneNascita;
    private String percipientiProvinciaNascita;

    // Lavoro autonomo — pagina 14
    private String lavAutoCausale;
    private String lavAutoAnno;
    private String lavAutoAmmontareLordo;
    private String lavAutoImponibile;
    private String lavAutoRitenuteAcconto;

    // Locazioni brevi — pagina 15 (il modello ha 4 blocchi: loc_01..loc_04)
    private List<CuLocazioneBreviDTO> locazioni;
}

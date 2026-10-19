package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nazione forzata dall'utente in anteprima import su una riga: "IT" (italiano) o "EST" (estero).
 * Applicata in BookingImportService.confirm() a nazione e CF della prenotazione.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForzaturaNazione {
    private Integer rowNumber;
    private String tipo;   // "IT" | "EST"
}

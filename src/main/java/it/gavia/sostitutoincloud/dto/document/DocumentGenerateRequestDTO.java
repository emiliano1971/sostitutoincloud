package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGenerateRequestDTO {

    /** Prenotazione per cui generare il documento (obbligatorio) */
    private Integer bookingId;

    /** Tipo documento richiesto: "ricevuta_owner" | "fattura_pm" */
    private String tipoDocumento;

    /** Data di emissione; se null viene usata la data odierna */
    private LocalDate dataEmissione;
}

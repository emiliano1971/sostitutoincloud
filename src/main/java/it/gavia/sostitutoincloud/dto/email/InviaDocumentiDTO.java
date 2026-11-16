package it.gavia.sostitutoincloud.dto.email;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body di POST /api/bookings/{id}/invia-documenti: invio via email di fattura PM e/o ricevuta owner.
 * Il bookingId del path ha la precedenza su quello del body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviaDocumentiDTO {
    private Integer bookingId;
    /** Uno o più indirizzi separati da ; */
    private String destinatari;
    private String oggetto;
    /** Testo semplice (non HTML) */
    private String testo;
    /** Fattura PM da allegare; null = non allegarla. Almeno uno tra fatturaId e ricevutaId. */
    private Integer fatturaId;
    /** Ricevuta owner da allegare; null = non allegarla. Almeno uno tra fatturaId e ricevutaId. */
    private Integer ricevutaId;
}

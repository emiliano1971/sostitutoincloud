package it.gavia.sostitutoincloud.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Body di POST /api/bookings/invia-email-batch: fattura + ricevuta agli ospiti di più prenotazioni. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviaEmailBatchDTO {
    private List<Integer> bookingIds;
    private String oggetto;
    private String testo;
}

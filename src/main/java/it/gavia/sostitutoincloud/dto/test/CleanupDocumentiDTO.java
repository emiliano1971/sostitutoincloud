package it.gavia.sostitutoincloud.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di cleanup dei documenti fiscali emessi da un test su un booking.
 * Il booking deve appartenere al tenant del chiamante.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupDocumentiDTO {

    private Integer bookingId;
}

package it.gavia.sostitutoincloud.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di cleanup dei booking creati dai test E2E.
 * Il pattern deve contenere "E2E-" o "TEST-".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupBookingsDTO {

    /** Pattern LIKE su external_booking_id, es. "E2E-%". */
    private String externalIdPattern;
}

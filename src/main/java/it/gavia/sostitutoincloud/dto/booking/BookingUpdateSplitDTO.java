package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Body della modifica dello split economico di una prenotazione
 * (PATCH /api/bookings/{id}/split).
 *
 * <p>PATCH parziale: entrambi i campi sono nullable e null non significa "azzera".
 * Gli importi dello split non arrivano mai dal client — sono ricalcolati dal server
 * a partire da questi due input e dalle regole del contratto immobile.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingUpdateSplitDTO {

    /** null = lascia invariato il flag già presente sulla prenotazione. */
    private Boolean touristTaxIncludedInGross;

    /** null = nessun override, la commissione OTA torna a quella delle regole di contratto. */
    private BigDecimal otaCommissionOverride;
}

package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Body della modifica dello split economico di una prenotazione
 * (PATCH /api/bookings/{id}/split).
 *
 * <p>PATCH parziale: i campi sono nullable e null non significa "azzera".
 * Gli importi dello split sono ricalcolati dal server a partire da questi input e dalle
 * regole del contratto immobile; gli override sostituiscono il valore della regola.
 *
 * <p>Per un override, null (o campo assente) = torna alla regola. Per tenere un importo
 * impostato a mano il client deve quindi ripassarlo a ogni PATCH: BookingDetail lo fa
 * per l'OTA sempre, per pulizie e PM quando la riga split è 'manuale'.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingUpdateSplitDTO {

    /** null = lascia invariato il flag già presente sulla prenotazione. */
    private Boolean touristTaxIncludedInGross;

    /** null = nessun override, la commissione OTA torna a quella delle regole di contratto. */
    private BigDecimal otaCommissionOverride;

    /** Totale pulizie (pulizie + cambio biancheria) impostato a mano; null = dalle regole. */
    private BigDecimal cleaningOverride;

    /** Provvigione PM impostata a mano; null = dalle regole (anche 'percentuale_netto'). */
    private BigDecimal pmFeeOverride;
}

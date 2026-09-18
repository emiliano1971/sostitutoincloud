package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body dell'inserimento manuale di una prenotazione (POST /api/bookings).
 *
 * <p>Solo i dati che l'operatore digita: split economico, ritenuta, tassa di soggiorno
 * e stato sono calcolati lato server, non arrivano mai dal client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreateDTO {

    // ── dati prenotazione ───────────────────────────────────────────────────
    private Integer fkPropertyId;        // obbligatorio
    private Integer fkCanaleOtaId;       // opzionale — null = canale generico
    private String externalBookingId;    // opzionale — se assente viene generato "MAN-{timestamp}"
    private LocalDate checkinDate;       // obbligatorio
    private LocalDate checkoutDate;      // obbligatorio
    private Integer guests;              // obbligatorio, min 1
    private BigDecimal grossAmount;      // obbligatorio, > 0

    // ── dati ospite ─────────────────────────────────────────────────────────
    private String guestName;            // obbligatorio — "Cognome Nome"
    private String guestTaxCode;         // opzionale — se assente si tenta il calcolo dall'anagrafica
    private LocalDate guestBirthDate;
    private String guestSesso;           // M / F
    private String guestBirthPlace;
    private String guestDocType;
    private String guestDocNumber;
    private String guestCountry;
    private String guestAddress;
    private String guestPhone;
}

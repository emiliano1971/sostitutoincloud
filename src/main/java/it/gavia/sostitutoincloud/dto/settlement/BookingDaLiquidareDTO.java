package it.gavia.sostitutoincloud.dto.settlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Prenotazione con ricevuta emessa ancora fuori da ogni liquidazione: alimenta
 * l'avviso e il dettaglio nella lista liquidazioni.
 * {@code periodoLedger} è il periodo di competenza della ritenuta (MM/yyyy),
 * che può essere precedente al periodo che verrà liquidato (arretrato).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDaLiquidareDTO {

    private Integer bookingId;
    private String externalBookingId;
    private String ownerName;
    private String propertyName;
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private BigDecimal canoneLocazione;
    private BigDecimal ritenutaAmount;
    /** canoneLocazione - ritenutaAmount */
    private BigDecimal nettoProprietario;
    /** Periodo di competenza della ritenuta, es. "08/2026". */
    private String periodoLedger;
}

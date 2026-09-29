package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Body di creazione / modifica di una voce extra dello split economico
 * (POST /api/bookings/{id}/split/extra, PATCH /api/bookings/{id}/split/{rigaId}).
 * La validazione (descrizione non vuota, importo &gt; 0) è nel service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingVoceExtraDTO {

    /** Obbligatoria, es. "Parcheggio". */
    private String descrizione;

    /**
     * Obbligatorio, &gt; 0. Imponibile inserito dal PM (netto IVA esclusa): il lordo in fattura
     * lo calcola il sistema con l'aliquota del regime PM (22% RF01, 0 RF19).
     */
    private BigDecimal imponibile;

    /**
     * Deprecato: importo lordo (IVA inclusa). Usato solo se imponibile manca, per i client
     * che non sono ancora passati all'imponibile: il netto si ricava scorporando l'IVA.
     */
    @Deprecated
    private BigDecimal importo;

    /** null = true in creazione, invariato in modifica. */
    private Boolean includeInFatturaPm;
}

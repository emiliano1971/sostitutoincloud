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

    /** Obbligatorio, &gt; 0. Importo lordo (IVA inclusa, come le altre voci della fattura PM). */
    private BigDecimal importo;

    /** null = true in creazione, invariato in modifica. */
    private Boolean includeInFatturaPm;
}

package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Filtri e ordinamento della lista prenotazioni (GET /api/bookings). Campi null = nessun filtro. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingFilterDTO {

    // Codici stato_prenotazione separati da virgola; 'da_completare' = checkout passato e
    // stato non finale (esclusivo con gli altri)
    private String status;
    private String channel;         // codice canale_ota
    private String q;               // ID prenotazione, ospite, immobile, proprietario, canale
    private LocalDate dataFrom;     // data check-in, estremi inclusi
    private LocalDate dataTo;
    private Integer propertyId;
    private Integer ownerId;
    private String sort;            // campo di BookingListDTO; default checkinDate
    private String dir;             // asc | desc (default desc)
}

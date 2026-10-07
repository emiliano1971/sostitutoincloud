package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Pagina della lista prenotazioni (GET /api/bookings). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingPageDTO {

    private List<BookingListDTO> content;
    private int page;               // pagina corrente, 0-based
    private int size;               // dimensione pagina effettiva (parametro o tenant_settings.page_size)
    private long totalElements;     // prenotazioni che soddisfano i filtri
    private int totalPages;
}

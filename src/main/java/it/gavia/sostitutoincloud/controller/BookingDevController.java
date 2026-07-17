package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.service.BookingService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Endpoint di sviluppo per la cancellazione dei booking con cascata sui dati collegati.
 * Annotato con @Profile({"local","test"}) a livello di classe: l'intero controller
 * NON viene registrato in produzione (stesso pattern di TestController).
 * @Profile non è valutato sui singoli metodi handler, va sulla classe/bean.
 */
@Log4j2
@Profile({"local", "test"})
@RestController
@RequestMapping("/api/bookings")
public class BookingDevController {

    private final BookingService bookingService;

    public BookingDevController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        log.info("BookingDevController.delete() - id={}", id);
        try {
            Integer tenantId = SecurityUtils.getCurrentTenantId();
            bookingService.deleteWithCascade(tenantId, id);
            return ResponseEntity.ok(Map.of("message", "Booking eliminato", "id", id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("BookingDevController.delete() - errore id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Errore durante la cancellazione del booking id=" + id));
        }
    }
}

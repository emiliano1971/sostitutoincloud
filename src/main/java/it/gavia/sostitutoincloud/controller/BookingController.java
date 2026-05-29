package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.booking.BookingDetailDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingFilterDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingListDTO;
import it.gavia.sostitutoincloud.service.BookingService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@Log4j2
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ResponseEntity<List<BookingListDTO>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        BookingFilterDTO filter = BookingFilterDTO.builder()
                .status(status)
                .channel(channel)
                .q(q)
                .page(page)
                .size(size)
                .build();
        return ResponseEntity.ok(bookingService.findByTenantId(tenantId, filter));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return bookingService.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Booking non trovato: id=" + id));
    }
}

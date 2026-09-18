package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.booking.BookingCreateDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingDetailDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingFilterDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingListDTO;
import it.gavia.sostitutoincloud.dto.booking.GuestUpdateDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportConfirmDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportPreviewDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportResultDTO;
import it.gavia.sostitutoincloud.dto.importing.ImportPreviewV2RequestDTO;
import it.gavia.sostitutoincloud.dto.importing.ImportUploadResponseDTO;
import it.gavia.sostitutoincloud.service.BookingImportService;
import it.gavia.sostitutoincloud.service.BookingService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/bookings")
@Log4j2
public class BookingController {

    private final BookingService bookingService;
    private final BookingImportService bookingImportService;

    public BookingController(BookingService bookingService, BookingImportService bookingImportService) {
        this.bookingService = bookingService;
        this.bookingImportService = bookingImportService;
    }

    @PostMapping("/import")
    public ResponseEntity<?> importPreview(
            @RequestParam("file") MultipartFile file) {
        try {
            Integer tenantId = SecurityUtils.getCurrentTenantId();
            BookingImportPreviewDTO preview = bookingImportService.preview(tenantId, file);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/import/confirm")
    public ResponseEntity<BookingImportResultDTO> importConfirm(
            @RequestBody BookingImportConfirmDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(bookingImportService.confirm(tenantId, dto));
    }

    // ── Import V2 (doppio file + mapping colonne manuale) ──────────────────

    @PostMapping("/import/upload")
    public ResponseEntity<?> importUpload(
            @RequestParam("bookingFile") MultipartFile bookingFile,
            @RequestParam(value = "guestFile", required = false) MultipartFile guestFile,
            @RequestParam(value = "headerRow", required = false, defaultValue = "0") Integer headerRow) {
        try {
            Integer tenantId = SecurityUtils.getCurrentTenantId();
            ImportUploadResponseDTO result = bookingImportService.uploadFiles(tenantId, bookingFile, guestFile, headerRow);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("BookingController.importUpload() - errore: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/import/preview-v2")
    public ResponseEntity<?> importPreviewV2(@RequestBody ImportPreviewV2RequestDTO request) {
        try {
            Integer tenantId = SecurityUtils.getCurrentTenantId();
            BookingImportPreviewDTO preview = bookingImportService.previewWithMapping(
                    tenantId, request.getBookingSessionId(), request.getGuestSessionId(), request.getMapping());
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            log.warn("BookingController.importPreviewV2() - errore: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
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

    /**
     * Inserimento manuale di una prenotazione. L'accesso per ruolo è già filtrato dalla
     * SecurityChain su /api/bookings/** (tenant_admin, pm_user, super_admin).
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody BookingCreateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            BookingDetailDTO created = bookingService.createManuale(tenantId, dto);
            log.info("BookingController.create() - tenantId={} bookingId={}", tenantId, created.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.warn("BookingController.create() - tenantId={} richiesta non valida: {}", tenantId, e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return bookingService.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Booking non trovato: id=" + id));
    }

    @PatchMapping("/{id}/guest")
    public ResponseEntity<?> updateGuest(@PathVariable Integer id, @RequestBody GuestUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            return bookingService.updateBookingGuest(tenantId, id, dto)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}

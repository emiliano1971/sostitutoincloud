package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.ota.CanaleOtaCreateDTO;
import it.gavia.sostitutoincloud.dto.ota.CanaleOtaDTO;
import it.gavia.sostitutoincloud.dto.ota.CanaleOtaUpdateDTO;
import it.gavia.sostitutoincloud.service.CanaleOtaService;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/canali-ota")
@Log4j2
public class CanaleOtaController {

    private final CanaleOtaService canaleOtaService;

    public CanaleOtaController(CanaleOtaService canaleOtaService) {
        this.canaleOtaService = canaleOtaService;
    }

    @GetMapping
    public ResponseEntity<List<CanaleOtaDTO>> findAll() {
        return ResponseEntity.ok(canaleOtaService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CanaleOtaDTO> findById(@PathVariable Integer id) {
        return ResponseEntity.ok(canaleOtaService.findById(id));
    }

    @PostMapping
    public ResponseEntity<CanaleOtaDTO> create(@RequestBody CanaleOtaCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(canaleOtaService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CanaleOtaDTO> update(
            @PathVariable Integer id,
            @RequestBody CanaleOtaUpdateDTO dto) {
        return ResponseEntity.ok(canaleOtaService.update(id, dto));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<CanaleOtaDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody Map<String, Boolean> body) {
        Boolean attivo = body.get("attivo");
        return ResponseEntity.ok(canaleOtaService.updateStatus(id, attivo));
    }
}

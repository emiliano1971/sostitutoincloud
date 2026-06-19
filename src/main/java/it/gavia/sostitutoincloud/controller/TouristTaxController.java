package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.touristtax.RegolaTassaSoggiornoCreateDTO;
import it.gavia.sostitutoincloud.dto.touristtax.RegolaTassaSoggiornoDetailDTO;
import it.gavia.sostitutoincloud.dto.touristtax.RegolaTassaSoggiornoListDTO;
import it.gavia.sostitutoincloud.dto.touristtax.TouristTaxCalculationDTO;
import it.gavia.sostitutoincloud.dto.touristtax.TouristTaxCalculationRequestDTO;
import it.gavia.sostitutoincloud.service.TouristTaxCalculatorService;
import it.gavia.sostitutoincloud.service.TouristTaxService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tourist-tax")
@Log4j2
public class TouristTaxController {

    private final TouristTaxService touristTaxService;
    private final TouristTaxCalculatorService calculatorService;

    public TouristTaxController(TouristTaxService touristTaxService,
                                TouristTaxCalculatorService calculatorService) {
        this.touristTaxService = touristTaxService;
        this.calculatorService = calculatorService;
    }

    @GetMapping
    public ResponseEntity<List<RegolaTassaSoggiornoListDTO>> findAll() {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(touristTaxService.findByTenantId(tenantId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegolaTassaSoggiornoDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(touristTaxService.findById(tenantId, id));
    }

    @PostMapping
    public ResponseEntity<RegolaTassaSoggiornoDetailDTO> create(
            @RequestBody RegolaTassaSoggiornoCreateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.status(HttpStatus.CREATED).body(touristTaxService.create(tenantId, dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RegolaTassaSoggiornoDetailDTO> update(
            @PathVariable Integer id,
            @RequestBody RegolaTassaSoggiornoCreateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(touristTaxService.update(tenantId, id, dto));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RegolaTassaSoggiornoDetailDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody Map<String, Boolean> body) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        Boolean attivo = body.get("attivo");
        return ResponseEntity.ok(touristTaxService.updateStatus(tenantId, id, attivo));
    }

    @PostMapping("/{id}/calculate")
    public ResponseEntity<TouristTaxCalculationDTO> calculate(
            @PathVariable Integer id,
            @RequestBody TouristTaxCalculationRequestDTO request) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        RegolaTassaSoggiornoDetailDTO regola = touristTaxService.findById(tenantId, id);
        TouristTaxCalculationDTO result = calculatorService.calculate(
                regola,
                request.getNights(),
                request.getCheckinDate(),
                request.getZona(),
                request.getGuestAges());
        return ResponseEntity.ok(result);
    }
}

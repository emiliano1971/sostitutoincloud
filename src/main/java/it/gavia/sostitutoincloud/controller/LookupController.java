package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.lookup.LookupCollectionDTO;
import it.gavia.sostitutoincloud.service.LookupService;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/lookup")
@Log4j2
public class LookupController {

    private final LookupService lookupService;

    public LookupController(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping
    public ResponseEntity<LookupCollectionDTO> getAll() {
        return ResponseEntity.ok(lookupService.getAll());
    }
}

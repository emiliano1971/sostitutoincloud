package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dao.ComuneItalianoDAO;
import it.gavia.sostitutoincloud.model.ComuneItaliano;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint pubblici per la ricerca dei comuni italiani (autocomplete form ospite/import).
 */
@RestController
@RequestMapping("/api/public/comuni")
@Log4j2
public class ComuneController {

    private final ComuneItalianoDAO comuneItalianoDAO;

    public ComuneController(ComuneItalianoDAO comuneItalianoDAO) {
        this.comuneItalianoDAO = comuneItalianoDAO;
    }

    @GetMapping
    public ResponseEntity<List<ComuneItaliano>> cerca(@RequestParam("q") String q) {
        log.debug("ComuneController.cerca() - q={}", q);
        return ResponseEntity.ok(comuneItalianoDAO.findByNome(q));
    }

    @GetMapping("/{belfiore}")
    public ResponseEntity<ComuneItaliano> byBelfiore(@PathVariable("belfiore") String belfiore) {
        log.debug("ComuneController.byBelfiore() - belfiore={}", belfiore);
        return comuneItalianoDAO.findByBelfiore(belfiore)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

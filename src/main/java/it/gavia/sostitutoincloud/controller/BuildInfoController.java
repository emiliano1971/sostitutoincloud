package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.service.BuildInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Versione dell'applicazione — endpoint pubblico (coperto da /api/public/** in SecurityConfig).
 */
@RestController
@RequestMapping("/api/public/version")
public class BuildInfoController {

    private final BuildInfoService buildInfoService;

    public BuildInfoController(BuildInfoService buildInfoService) {
        this.buildInfoService = buildInfoService;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getVersion() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("version", buildInfoService.getBuildVersion());
        body.put("commitHash", buildInfoService.getCommitHash());
        body.put("commitDate", buildInfoService.getCommitDate());
        return ResponseEntity.ok(body);
    }
}

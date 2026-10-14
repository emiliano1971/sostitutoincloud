package it.gavia.sostitutoincloud.service;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Versione dell'applicazione nel formato V{yy}.{mmdd}-{hash}, calcolata dalla data
 * e dall'hash dell'ultimo commit (git.properties generato da git-commit-id-maven-plugin).
 * Es. commit del "2026-10-14 15:32" con hash a3f2c1d → V26.1014-a3f2c1d.
 * Senza git.properties la versione è "dev".
 */
@Service
@Getter
@Log4j2
public class BuildInfoService {

    private static final DateTimeFormatter COMMIT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String buildVersion;
    private final String commitHash;
    private final String commitDate;

    public BuildInfoService() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                log.warn("git.properties non trovato: versione 'dev'");
            }
        } catch (Exception e) {
            log.warn("Lettura git.properties fallita: {}", e.getMessage());
        }

        this.commitHash = props.getProperty("git.commit.id.abbrev", "dev");
        this.commitDate = props.getProperty("git.commit.time", "");

        String versionStr = "dev";
        if (!commitDate.isEmpty()) {
            try {
                LocalDateTime dt = LocalDateTime.parse(commitDate, COMMIT_TIME_FORMAT);
                versionStr = String.format("V%02d.%02d%02d-%s",
                        dt.getYear() % 100, dt.getMonthValue(), dt.getDayOfMonth(), commitHash);
            } catch (Exception e) {
                versionStr = "V-" + commitHash;
            }
        }
        this.buildVersion = versionStr;
        log.info("Versione applicazione: {}", buildVersion);
    }
}

package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class Tenant {

    private Integer id;

    /** Ragione sociale */
    private String legalName;

    /** Nome visualizzato in UI */
    private String displayName;

    /** Codice fiscale — 16 caratteri */
    private String taxCode;

    /** Partita IVA — 11 cifre senza prefisso IT, nullable */
    private String vatNumber;

    /** Stato workflow: draft | active | suspended | closed */
    private String stato;

    private String administrativeEmail;

    /** PEC — nullable */
    private String pec;

    /** Telefono — nullable */
    private String phone;

    private String legalAddress;

    /** Data attivazione — nullable finché in stato draft */
    private LocalDate activatedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

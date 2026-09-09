package it.gavia.sostitutoincloud.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di cleanup di una Certificazione Unica generata da un test.
 * La CU deve appartenere al tenant del chiamante e non essere già stata trasmessa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupCuDTO {

    private Integer cuId;
}

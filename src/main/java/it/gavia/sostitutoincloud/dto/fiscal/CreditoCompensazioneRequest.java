package it.gavia.sostitutoincloud.dto.fiscal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Credito da usare in un F24 (POST /api/f24/{id}/crediti). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditoCompensazioneRequest {

    private Integer ledgerId;
    /** Importo da compensare: > 0 e non oltre il residuo del credito. */
    private BigDecimal importoUsato;
}

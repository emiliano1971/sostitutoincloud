package it.gavia.sostitutoincloud.dto.touristtax;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestTaxDTO {

    private Integer age;
    private Integer nightsCharged;
    private BigDecimal ratePerNight;
    private BigDecimal total;
    private Boolean esente;
}

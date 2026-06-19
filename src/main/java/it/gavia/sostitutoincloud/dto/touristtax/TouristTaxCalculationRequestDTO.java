package it.gavia.sostitutoincloud.dto.touristtax;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TouristTaxCalculationRequestDTO {

    private Integer nights;
    private LocalDate checkinDate;
    private String zona;
    private List<Integer> guestAges;
}

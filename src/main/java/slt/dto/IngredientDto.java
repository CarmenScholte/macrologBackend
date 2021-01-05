package slt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class IngredientDto {

    private Long id;
    private Double multiplier;
    private FoodDto food;
    private PortionDto portion;
}

package csl.dto;

import io.swagger.annotations.ApiModelProperty;
import org.joda.time.DateTime;

/**
 * Class voor het bewaren van de macros
 */
public class LogEntry {

    private FoodMacros food;
    private FoodAlias foodAlias;

    @ApiModelProperty(notes = "Multiplier of the measurement",required=true, example = "1.7")
    private Double multiplier;
    @ApiModelProperty(notes = "Time of log",required=true)
    private DateTime timestamp;

    public FoodMacros getFood() {
        return food;
    }

    public void setFood(FoodMacros food) {
        this.food = food;
    }

    public FoodAlias getFoodAlias() {
        return foodAlias;
    }

    public void setFoodAlias(FoodAlias foodAlias) {
        this.foodAlias = foodAlias;
    }

    public Double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(Double multiplier) {
        this.multiplier = multiplier;
    }

    public DateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }
}

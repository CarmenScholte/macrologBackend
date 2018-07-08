package csl.rest;

import csl.database.FoodAliasRepository;
import csl.database.FoodRepository;
import csl.database.LogEntryRepository;
import csl.database.model.Food;
import csl.database.model.FoodAlias;
import csl.dto.AddLogEntryRequest;
import csl.dto.FoodMacros;
import csl.dto.LogEntry;
import csl.dto.Macro;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.joda.time.DateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/logs")
@Api(value="logs", description="Operations pertaining to logentries in the macro logger applications")
public class LogsService {

    private FoodRepository foodRepository = new FoodRepository();
    private FoodAliasRepository foodAliasRepository = new FoodAliasRepository();
    private LogEntryRepository logEntryRepository = new LogEntryRepository();


    @ApiOperation(value = "Retrieve all stored logentries")
    @CrossOrigin(origins = "http://localhost:4200")
    @RequestMapping(value = "",
            method = GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getAllLogEntries() {

        List<csl.database.model.LogEntry> allLogEntries = logEntryRepository.getAllLogEntries();

        List<LogEntry> allDtos = new ArrayList<>();
        for (csl.database.model.LogEntry logEntry : allLogEntries) {

            Food food = foodRepository.getFoodById(logEntry.getFoodId());
            FoodMacros curr = new FoodMacros();
            curr.setFoodId(food.getId());
            curr.setName(food.getName());
            curr.setAmountUnit(food.getMeasurementUnit().toString());

            Macro macro = new Macro();
            macro.setCarbs(food.getCarbs());
            macro.setFat(food.getFat());
            macro.setProteins(food.getProtein());
            curr.addMacroPerUnit(food.getUnitGrams(), macro);

            FoodAlias foodAlias = foodAliasRepository.getFoodAlias(logEntry.getAliasIdUsed());
            csl.dto.FoodAlias currDto = new csl.dto.FoodAlias();
            currDto.setAliasName(foodAlias.getAliasname());
            currDto.setAmountNumber(foodAlias.getAmountNumber());
            currDto.setAmountUnit(foodAlias.getAmountUnit());

            currDto.setAliasCarbs(food.getCarbs()/100 * currDto.getAmountNumber());
            currDto.setAliasProtein(food.getProtein()/100 * currDto.getAmountNumber());
            currDto.setAliasFat(food.getFat()/100 * currDto.getAmountNumber());

            LogEntry dto = new LogEntry();
            dto.setFoodId(curr.getFoodId());
            dto.setFoodName(curr.getName());
            dto.setFoodAlias(currDto);
            Double multiplier = logEntry.getMultiplier();
            dto.setMultiplier(multiplier);
            dto.setDay(logEntry.getDay());
            dto.setMeal(logEntry.getMeal());

            Macro macrosCalculated = new Macro();
            if (foodAlias != null){
                Double defaultProtein = (foodAlias.getAmountNumber()* food.getProtein())/food.getUnitGrams();
                Double defaultFat = (foodAlias.getAmountNumber() * food.getFat())/food.getUnitGrams();
                Double defaultCarbs = (foodAlias.getAmountNumber() * food.getCarbs())/food.getUnitGrams();
                macrosCalculated.setCarbs(multiplier * defaultCarbs);
                macrosCalculated.setFat(multiplier * defaultFat);
                macrosCalculated.setProteins(multiplier * defaultProtein);

            } else {
                macrosCalculated.setCarbs(multiplier * food.getCarbs());
                macrosCalculated.setFat(multiplier * food.getFat());
                macrosCalculated.setProteins(multiplier * food.getProtein());
            }
            dto.setMacrosCalculated(macrosCalculated);


            allDtos.add(dto);

        }


        return ResponseEntity.ok(allDtos);


    }

    @ApiOperation(value = "Store new logentry")
    @RequestMapping(value = "",
            method = POST,
            headers = {"Content-Type=application/json"})
    public ResponseEntity storeLogEntry(@RequestBody AddLogEntryRequest logEntry) {

        csl.database.model.LogEntry entry = new csl.database.model.LogEntry();
        entry.setAliasIdUsed(logEntry.getAliasIdUsed());
        entry.setFoodId(logEntry.getFoodId());
        entry.setMultiplier(logEntry.getMultiplier());
        entry.setDay(new Date(logEntry.getDay().getTime()));
        entry.setMeal(logEntry.getMeal());
        logEntryRepository.insertLogEntry(entry);

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}

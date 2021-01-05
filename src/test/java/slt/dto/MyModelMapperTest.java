package slt.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import slt.database.FoodRepository;
import slt.database.PortionRepository;
import slt.database.entities.*;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RunWith(MockitoJUnitRunner.class)
class MyModelMapperTest {

    @Mock
    FoodRepository foodRepository;

    @Mock
    PortionRepository portionRepository;

    @InjectMocks
    MyModelMapper mapper;

    @BeforeAll
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testLocalDateSQLMapping() {
        LocalDate input = LocalDate.parse("2013-01-02");
        Date dateConverted = mapper.getConfiguredMapper().map(input, Date.class);
        LocalDate dateConvertedBack = mapper.getConfiguredMapper().map(dateConverted, LocalDate.class);
        assertThat(dateConvertedBack).isEqualTo(input);
    }

    @Test
    public void testWeightMapping() {
        WeightDto weightDto = WeightDto.builder()
                .weight(20.0)
                .day(LocalDate.parse("2010-04-01"))
                .remark("remark")
                .build();

        Weight mappedWeight = mapper.getConfiguredMapper().map(weightDto, Weight.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mappedWeight.getRemark()).isEqualTo(weightDto.getRemark());
        assertThat(mappedWeight.getDay()).isInSameDayAs(Date.valueOf(weightDto.getDay()));
        assertThat(mappedWeight.getValue()).isEqualTo(weightDto.getWeight());

        // and back to original
        WeightDto mappedBack = mapper.getConfiguredMapper().map(mappedWeight, WeightDto.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mappedBack.getWeight()).isEqualTo(weightDto.getWeight());
        assertThat(mappedBack.getRemark()).isEqualTo(weightDto.getRemark());
        assertThat(mappedBack.getDay()).isEqualTo(weightDto.getDay());
    }

    @Test
    public void testActivityMapping() {
        LocalDate localDate = LocalDate.parse("2010-01-02");
        java.util.Date day = Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        LogActivityDto dto = LogActivityDto.builder()
                .calories(1.0)
                .day(day)
                .name("running")
                .syncedId(12l)
                .syncedWith("STRAVA")
                .build();

        LogActivity mapped = mapper.getConfiguredMapper().map(dto, LogActivity.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mapped.getDay().toLocalDate()).isEqualTo(localDate);
        assertThat(mapped.getName()).isEqualTo(dto.getName());
        assertThat(mapped.getCalories()).isEqualTo(dto.getCalories());
        assertThat(mapped.getSyncedId()).isEqualTo(dto.getSyncedId());
        assertThat(mapped.getSyncedWith()).isEqualTo(dto.getSyncedWith());

        LogActivity mappedBack = mapper.getConfiguredMapper().map(mapped, LogActivity.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mappedBack.getDay().toLocalDate()).isEqualTo(localDate);
        assertThat(mappedBack.getName()).isEqualTo(dto.getName());
        assertThat(mappedBack.getCalories()).isEqualTo(dto.getCalories());
        assertThat(mappedBack.getSyncedId()).isEqualTo(dto.getSyncedId());
        assertThat(mappedBack.getSyncedWith()).isEqualTo(dto.getSyncedWith());
    }

    @Test
    public void testAddDishRequestMapping() {
        DishRequest dto = DishRequest.builder()
                .name("dish")
                .ingredients(Arrays.asList(
                        AddDishIngredientDto.builder()
                                .food(FoodDto.builder().id(1L).build())
                                .portion(PortionDto.builder().id(1l).build())
                                .multiplier(2.0).build()
                )).build();

        Dish mapped = mapper.getConfiguredMapper().map(dto, Dish.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mapped.getName()).isEqualTo("dish");
        assertThat(mapped.getIngredients()).hasSize(1);

        assertThat(mapped.getIngredients().get(0).getDish()).isNotNull();
        assertThat(mapped.getIngredients().get(0).getFoodId()).isEqualTo(dto.getIngredients().get(0).getFood().getId());
        assertThat(mapped.getIngredients().get(0).getMultiplier()).isEqualTo(dto.getIngredients().get(0).getMultiplier());
        assertThat(mapped.getIngredients().get(0).getPortionId()).isEqualTo(dto.getIngredients().get(0).getPortion().getId());
    }

    @Test
    public void testDishMapping() {
        DishDto dto = DishDto.builder()
                .name("nameDish")
                .id(1L)
                .ingredients(Arrays.asList(
                        IngredientDto.builder()
//                                .id(20L)
                                .multiplier(3.0)
                                .portion(PortionDto.builder().id(2L).build())
                                .food(
                                        FoodDto.builder()
                                                .id(30L)
                                                .protein(4.0)
                                                .carbs(2.0)
                                                .fat(3.0)
                                                .portions(Arrays.asList(
                                                        PortionDto.builder()
                                                                .id(2L)
                                                                .grams(30.0)
                                                                .description("portion")
                                                                .build())
                                                ).build()
                                ).build()
                )).build();


        Food foodEntity = Food.builder().id(2L).build();
        Mockito.when(foodRepository.getFoodById(isNull(), eq(2L))).thenReturn(foodEntity);
        Dish mapped = mapper.getConfiguredMapper().map(dto, Dish.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mapped.getName()).isEqualTo(dto.getName());
        assertThat(mapped.getIngredients()).isNotEmpty();
        assertThat(mapped.getIngredients()).hasSize(1);
        Ingredient ingredientMapped = mapped.getIngredients().get(0);
        assertThat(ingredientMapped.getPortionId()).isEqualTo(2L);
        assertThat(ingredientMapped.getFoodId()).isEqualTo(30L);
        assertThat(ingredientMapped.getMultiplier()).isEqualTo(3.0);
        assertThat(ingredientMapped.getDish()).isEqualTo(mapped);

        // UserId needs to be set
        mapped.setUserId(2);
        Mockito.when(foodRepository.getFoodById(eq(2), eq(30L)))
                .thenReturn(Food.builder()
                        .name("foodrepo")
                        .fat(3.0)
                        .protein(1.0)
                        .carbs(2.0)
                        .build());
        Mockito.when(portionRepository.getPortions(eq(30L))).thenReturn(Arrays.asList(
                Portion.builder().description("portionrepo").id(2L).grams(10.0).build()
        ));

        DishDto mappedBack = mapper.getConfiguredMapper().map(mapped, DishDto.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mappedBack.getName()).isEqualTo(dto.getName());
        assertThat(mappedBack.getIngredients()).isNotEmpty();
        assertThat(mappedBack.getIngredients()).hasSize(1);
        Ingredient ingredientMappedBack = mapped.getIngredients().get(0);
        assertThat(ingredientMappedBack.getPortionId()).isEqualTo(2L);
        assertThat(ingredientMappedBack.getFoodId()).isEqualTo(30L);
        assertThat(ingredientMappedBack.getMultiplier()).isEqualTo(3.0);
        assertThat(ingredientMappedBack.getDish()).isEqualTo(mapped);

        assertThat(mappedBack.getMacrosCalculated()).isNotNull();
        // 3* 10 gram met 1/2/3
//        assertThat(mappedBack.getMacrosCalculated().getProtein()).isEqualTo(1.0 * 0.10 * 3);
//        assertThat(mappedBack.getMacrosCalculated().getFat()).isEqualTo(3.0 * 0.10 * 3);
//        assertThat(mappedBack.getMacrosCalculated().getCarbs()).isEqualTo(2.0 * 0.10 * 3);


    }

    @Test
    public void testFoodMapping() {

        FoodDto dto = FoodDto.builder()
                .id(3000L)
                .fat(1.0)
                .protein(2.0)
                .carbs(3.0)
                .name("food")
                .portions(
                        Arrays.asList(
                                PortionDto.builder()
                                        .grams(5.0)
                                        .description("portion")
                                        .macros(Macro.builder()
                                                .carbs(3.0)
                                                .fat(45.0)
                                                .protein(13.0)
                                                .build()
                                        )
                                        .build()
                        ))
                .build();

        Food mapped = mapper.getConfiguredMapper().map(dto, Food.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mapped.getName()).isEqualTo(dto.getName());
        assertThat(mapped.getCarbs()).isEqualTo(dto.getCarbs());
        assertThat(mapped.getFat()).isEqualTo(dto.getFat());
        assertThat(mapped.getProtein()).isEqualTo(dto.getProtein());
        assertThat(mapped.getId()).isEqualTo(dto.getId());

        FoodDto mappedBack = mapper.getConfiguredMapper().map(mapped, FoodDto.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mappedBack.getName()).isEqualTo(dto.getName());
        assertThat(mappedBack.getCarbs()).isEqualTo(dto.getCarbs());
        assertThat(mappedBack.getFat()).isEqualTo(dto.getFat());
        assertThat(mappedBack.getProtein()).isEqualTo(dto.getProtein());
        assertThat(mappedBack.getId()).isEqualTo(dto.getId());
    }

    @Test
    public void testPortionMapping() {
        Portion portion = Portion.builder()
                .id(1L)
                .description("desc")
                .foodId(2)
                .grams(30.0)
                .build();

        PortionDto map = mapper.getConfiguredMapper().map(portion, PortionDto.class);
        mapper.getConfiguredMapper().validate();

        assertThat(map.getId()).isEqualTo(portion.getId());
        assertThat(map.getDescription()).isEqualTo(portion.getDescription());
        assertThat(map.getGrams()).isEqualTo(portion.getGrams());
        assertThat(map.getMacros()).isNull();

        map.setMacros(Macro.builder().protein(1.0).fat(2.0).carbs(3.0).build());

        final PortionDto mappedBack = mapper.getConfiguredMapper().map(map, PortionDto.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mappedBack.getId()).isEqualTo(portion.getId());
        assertThat(mappedBack.getDescription()).isEqualTo(portion.getDescription());
        assertThat(mappedBack.getGrams()).isEqualTo(portion.getGrams());
    }

    @Test
    public void testSettingMapping() {
        LocalDate localDate = LocalDate.parse("2010-01-02");

        Setting setting = Setting.builder()
                .day(Date.valueOf(localDate))
                .name("setting")
                .userId(1)
                .value("waarde")
                .build();

        SettingDto dto = mapper.getConfiguredMapper().map(setting, SettingDto.class);
        mapper.getConfiguredMapper().validate();

        assertThat(dto.getDay().toLocalDate()).isEqualTo(localDate);
        assertThat(dto.getName()).isEqualTo(setting.getName());
        assertThat(dto.getValue()).isEqualTo(setting.getValue());
        assertThat(dto.getId()).isEqualTo(setting.getId());

        Setting mappedBack = mapper.getConfiguredMapper().map(dto, Setting.class);
        mapper.getConfiguredMapper().validate();
        assertThat(mappedBack.getDay().toLocalDate()).isEqualTo(localDate);
        assertThat(mappedBack.getName()).isEqualTo(setting.getName());
        assertThat(mappedBack.getValue()).isEqualTo(setting.getValue());
        assertThat(mappedBack.getId()).isEqualTo(setting.getId());
    }

    @Test
    public void testLogEntryMapping() {
        int userId = 100;
        Food food1 = Food.builder()
                .id(1L)
                .carbs(2.0)
                .fat(3.0)
                .protein(4.0)
                .userId(userId)
                .name("food1")
                .build();

        Portion food1Portion = Portion.builder()
                .foodId(food1.getId().intValue())
                .description("p1f1")
                .grams(30.0)
                .id(20L)
                .build();

        Portion food1Portion2 = Portion.builder()
                .foodId(food1.getId().intValue())
                .description("p2f1")
                .grams(40.0)
                .id(30L)
                .build();

        LocalDate localDate = LocalDate.parse("2010-04-01");
        LogEntry logEntry = LogEntry.builder()
                .day(Date.valueOf(localDate))
                .meal("BREAKFAST")
                .multiplier(3.0)
                .foodId(food1.getId())
                .portionId(food1Portion.getId())
                .id(30L)
                .userId(userId)
                .build();

        Mockito.when(foodRepository.getFoodById(eq(userId), eq(food1.getId()))).thenReturn(food1);

        List<Portion> portionList = Arrays.asList(food1Portion, food1Portion2);
        Mockito.when(portionRepository.getPortions(eq(food1.getId()))).thenReturn(portionList);

        LogEntryDto mapped = mapper.getConfiguredMapper().map(logEntry, LogEntryDto.class);
        mapper.getConfiguredMapper().validate();

        assertThat(mapped.getId()).isEqualTo(logEntry.getId());
        assertThat(mapped.getMeal()).isEqualTo(logEntry.getMeal());
        assertThat(mapped.getMultiplier()).isEqualTo(logEntry.getMultiplier());
        assertThat(mapped.getDay()).isInSameDayAs(Date.valueOf(localDate));

        assertThat(mapped.getPortion()).isNotNull();
        assertThat(mapped.getPortion().getId()).isEqualTo(food1Portion.getId());
        assertThat(mapped.getPortion().getDescription()).isEqualTo(food1Portion.getDescription());
        assertThat(mapped.getPortion().getMacros()).isNotNull();
        assertThat(mapped.getPortion().getMacros().getCalories()).isGreaterThan(1.0);

        assertThat(mapped.getFood()).isNotNull();
        assertThat(mapped.getFood().getId()).isEqualTo(food1.getId());
        assertThat(mapped.getFood().getPortions()).isNotEmpty();
        assertThat(mapped.getFood().getPortions()).hasSize(2);
        assertThat(mapped.getFood().getName()).isEqualTo(food1.getName());

        assertThat(mapped.getFood().getPortions().get(0).getMacros()).isNotNull();
        assertThat(mapped.getFood().getPortions().get(0).getMacros().getCalories()).isGreaterThan(1);
        assertThat(mapped.getFood().getPortions().get(1).getMacros()).isNotNull();
        assertThat(mapped.getFood().getPortions().get(1).getMacros().getCalories()).isGreaterThan(1);

    }

}
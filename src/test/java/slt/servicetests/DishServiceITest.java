package slt.servicetests;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import slt.dto.*;
import slt.security.ThreadLocalHolder;
import slt.security.UserInfo;
import slt.servicetests.utils.AbstractApplicationIntegrationTest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DishServiceITest extends AbstractApplicationIntegrationTest {

    private Integer userId;

    @BeforeAll
    public void setUserContext() {

        if (this.userId == null) {
            log.debug("Creating test user for test " + this.getClass().getName());
            this.userId = createUser(this.getClass().getName());
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(Integer.valueOf(this.userId));
        ThreadLocalHolder.getThreadLocal().set(userInfo);
    }

    @Test
    public void testCreateAndDeleteEmptyDish() {

        String dishName = "emptyDish";
        DishRequest newDishRequest = DishRequest.builder().name(dishName).build();
        ResponseEntity responseEntity = dishService.storeDish(newDishRequest);
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.CREATED.value());

        ResponseEntity<List<DishDto>> allDishes = dishService.getAllDishes();
        List<DishDto> foundDishes = allDishes.getBody();
        Optional<DishDto> emptyDish = foundDishes.stream().filter(m -> m.getName().equals(dishName)).findFirst();
        assertThat(emptyDish.isPresent()).isTrue();
        assertThat(emptyDish.get().getId()).isNotNull();
        assertThat(emptyDish.get().getIngredients()).isEmpty();

        responseEntity = dishService.deleteDish(emptyDish.get().getId());
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    public void testCreateSameDish() {

        String dishName = "sameDish";
        DishRequest newDish = DishRequest.builder().name(dishName).build();
        ResponseEntity responseEntity = dishService.storeDish(newDish);
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.CREATED.value());

        ResponseEntity<List<DishDto>> allDishes = dishService.getAllDishes();
        List<DishDto> foundDishes = allDishes.getBody();
        Optional<DishDto> emptyDish = foundDishes.stream().filter(m -> m.getName().equals(dishName)).findFirst();
        assertThat(emptyDish.isPresent()).isTrue();
        assertThat(emptyDish.get().getId()).isNotNull();
        assertThat(emptyDish.get().getIngredients()).isEmpty();

        DishRequest sameDish = DishRequest.builder().name(dishName).build();
        responseEntity = dishService.storeDish(sameDish);
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void testCreateDishWithMultipleFood() {

        String dishName = "with1Food";

        FoodDto food1 = createFood(FoodRequest.builder().name("food1").carbs(1.0).fat(2.0).protein(3.0).build());
        FoodDto food2 = createFood(FoodRequest.builder().name("food2").carbs(4.0).fat(5.0).protein(6.0).build());

        DishRequest newDish = DishRequest.builder()
                .name(dishName)
                .ingredients(
                        Arrays.asList(
                                AddDishIngredientDto.builder().food(food1).multiplier(1.0).build(),
                                AddDishIngredientDto.builder().food(food2).multiplier(3.0).build()
                        )
                )
                .build();

        ResponseEntity responseEntity = dishService.storeDish(newDish);
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.CREATED.value());

        ResponseEntity<List<DishDto>> allDishes = dishService.getAllDishes();
        List<DishDto> foundDishes = allDishes.getBody();
        Optional<DishDto> matchedDish = foundDishes.stream().filter(m -> m.getName().equals(dishName)).findFirst();
        assertThat(matchedDish.isPresent()).isTrue();
        assertThat(matchedDish.get().getId()).isNotNull();
        assertThat(matchedDish.get().getIngredients()).hasSize(2);

        assertThat(matchedDish.get().getIngredients().get(0).getFood()).isNotNull();
        assertThat(matchedDish.get().getIngredients().get(1).getFood()).isNotNull();

        Optional<IngredientDto> food1Optional = matchedDish.get().getIngredients().stream().filter(i -> i.getFood().getName().equals("food1")).findFirst();
        Optional<IngredientDto> food2Optional = matchedDish.get().getIngredients().stream().filter(i -> i.getFood().getName().equals("food2")).findFirst();

        assertThat(food1Optional.get().getMultiplier()).isEqualTo(1.0);
        assertThat(food2Optional.get().getMultiplier()).isEqualTo(3.0);

        responseEntity = dishService.deleteDish(matchedDish.get().getId());

        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    public void testCreateDishFoodWithPortion() {

        String dishName = "withPortions";

        FoodDto food1 = createFood(FoodRequest.builder().name("food3").carbs(1.0).fat(2.0).protein(3.0).
                portions(Arrays.asList(
                        PortionDto.builder().description("p1").grams(200.0).build(),
                        PortionDto.builder().description("p2").grams(600.0).build()
                )).build());
        FoodDto food2 = createFood(FoodRequest.builder().name("food4").carbs(4.0).fat(5.0).protein(6.0).build());

        PortionDto food1Portion1 = food1.getPortions().stream().filter(p -> p.getDescription().equals("p1")).findFirst().get();
        DishRequest newDish = DishRequest.builder()
                .name(dishName)
                .ingredients(
                        Arrays.asList(
                                AddDishIngredientDto.builder()
                                        .food(food1)
                                        .portion(food1Portion1)
                                        .multiplier(1.0)
                                        .build(),
                                AddDishIngredientDto.builder().food(food2).multiplier(3.0).build()
                        )
                )
                .build();

        ResponseEntity<DishDto> responseEntity = dishService.storeDish(newDish);
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.CREATED.value());

        ResponseEntity<List<DishDto>> allDishes = dishService.getAllDishes();
        List<DishDto> foundDishes = allDishes.getBody();
        Optional<DishDto> matchedDish = foundDishes.stream().filter(m -> m.getName().equals(dishName)).findFirst();
        assertThat(matchedDish.isPresent()).isTrue();
        assertThat(matchedDish.get().getId()).isNotNull();
        assertThat(matchedDish.get().getIngredients()).hasSize(2);

        assertThat(matchedDish.get().getIngredients().get(0).getFood()).isNotNull();
        assertThat(matchedDish.get().getIngredients().get(1).getFood()).isNotNull();

        Optional<IngredientDto> ingredient1Food1 = matchedDish.get().getIngredients().stream().filter(i -> i.getFood().getName().equals("food3")).findFirst();
        assertThat(ingredient1Food1.isPresent()).isTrue();

        assertThat(ingredient1Food1.get().getMultiplier()).isEqualTo(1.0);
        assertThat(ingredient1Food1.get().getPortion().getId()).isEqualTo(food1Portion1.getId());
        // check portion exists with food:
        assertThat(ingredient1Food1.get().getFood().getPortions()
                .stream()
                .anyMatch(p -> p.getId().equals(ingredient1Food1.get().getPortion().getId()))).isTrue();

        responseEntity = dishService.deleteDish(matchedDish.get().getId());

        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.OK.value());
    }


}

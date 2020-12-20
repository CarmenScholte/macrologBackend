package slt.servicetests.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Assert;
import slt.Application;
import slt.dto.*;
import slt.rest.*;
import slt.security.SecurityConstants;
import slt.security.ThreadLocalHolder;
import slt.security.UserInfo;
import slt.service.GoogleMailService;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@Slf4j
@ExtendWith(SpringExtension.class)
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@SpringBootTest(classes = {Application.class, IntegrationConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.version=1.0",
                "actievePus=ZTW"
        })
public abstract class AbstractApplicationIntegrationTest {

    @Autowired
    protected GoogleMailService mailService;

    @Autowired
    protected ActivityService activityService;

    @Autowired
    protected LogEntryService logEntryService;

    @Autowired
    protected FoodService foodService;

    @Autowired
    protected AuthenticationService authenticationService;

    @Autowired
    protected SettingsService settingsService;

    @Autowired
    protected WeightService weightService;

    @Autowired
    protected ImportService importService;

    @Autowired
    protected ExportService exportService;

    @Autowired
    protected DishService dishService;

    @Autowired
    protected AdminService adminService;

    protected Integer createUser(String userEmail) {
        RegistrationRequest registrationRequest = RegistrationRequest.builder().email(userEmail).password("testpassword").username(userEmail).build();
        ResponseEntity responseEntity = authenticationService.signUp(registrationRequest);
        Assert.isTrue(202 == responseEntity.getStatusCodeValue());
        return getUserIdFromResponseHeaderJWT(responseEntity);
    }

    protected Integer getUserIdFromResponseHeaderJWT(ResponseEntity responseEntity) {
        String jwtToken = responseEntity.getHeaders().get("token").get(0);
        Jws<Claims> claims = getClaimsJws(jwtToken);
        Integer userId = (Integer) claims.getBody().get("userId");
        log.debug("User id = " + userId);
        Assert.notNull(userId, "Geen UserID te herleiden");
        return userId;
    }

    protected void setUserContextFromJWTResponseHeader(ResponseEntity responseEntity) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(Integer.valueOf(getUserIdFromResponseHeaderJWT(responseEntity)));
        ThreadLocalHolder.getThreadLocal().set(userInfo);
    }

    protected void deleteAccount(String password) {
        ResponseEntity responseEntity;
        responseEntity = authenticationService.deleteAccount(password);
        Assertions.assertEquals(HttpStatus.OK.value(), responseEntity.getStatusCodeValue());
    }

    protected Jws<Claims> getClaimsJws(String jwtToken) {
        try {
            return Jwts.parser()
                    .setSigningKey(SecurityConstants.SECRET.getBytes("UTF-8"))
                    .parseClaimsJws(jwtToken);
        } catch (UnsupportedEncodingException uoe) {
            throw new RuntimeException("Unabled to read token");
        }
    }

    protected boolean isEqualDate(Date date, LocalDate localDate) {
        return Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault()).toLocalDate().equals(localDate);
    }


    protected FoodDto createFood(AddFoodRequest addFoodRequestZonderPortions) {
        ResponseEntity responseEntity = foodService.addFood(addFoodRequestZonderPortions);
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.CREATED.value());
        ResponseEntity allFoodEntity = foodService.getAllFood();
        assertThat(allFoodEntity.getStatusCodeValue()).isEqualTo(HttpStatus.OK.value());
        List<FoodDto> foodDtos = (List<FoodDto>) allFoodEntity.getBody();
        return foodDtos.stream().filter(f -> f.getName().equals(addFoodRequestZonderPortions.getName())).findFirst().get();
    }

    protected void createLogEntry(String day, FoodDto savedFood, Long portionId, double multiplier) {
        List<StoreLogEntryRequest> newLogEntries = Arrays.asList(
                StoreLogEntryRequest.builder()
                        .day(java.sql.Date.valueOf(LocalDate.parse(day)))
                        .meal("BREAKFAST")
                        .portionId(portionId)
                        .foodId(savedFood.getId())
                        .multiplier(multiplier)
                        .build()
        );
        ResponseEntity responseEntity = logEntryService.storeLogEntries(newLogEntries);
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.OK.value()); // why not CREATED?
    }

    protected void storeSetting(String name, String value) {
        SettingDto settingDto = SettingDto.builder().name(name).value(value).build();
        ResponseEntity responseEntity = settingsService.storeSetting(settingDto);
        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(HttpStatus.OK.value());
    }
}

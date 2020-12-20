package slt.servicetests;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import slt.dto.LogActivityDto;
import slt.security.ThreadLocalHolder;
import slt.security.UserInfo;
import slt.servicetests.utils.AbstractApplicationIntegrationTest;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ActivityServiceITest extends AbstractApplicationIntegrationTest {

    private Integer userId;

    @BeforeEach
    public void setUserContext() {
        if (this.userId == null) {
            log.debug("Creating test user for test " + this.getClass().getName());
            this.userId = createUser(this.getClass().getName());
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(this.userId);
        ThreadLocalHolder.getThreadLocal().set(userInfo);
    }

    @Test
    public void testActivities() {
        List<LogActivityDto> newActivities = Arrays.asList(
                LogActivityDto.builder()
                        .day(Date.valueOf(LocalDate.parse("2001-01-01")))
                        .name("Running")
                        .calories(20.0)
                        .build(),
                LogActivityDto.builder()
                        .day(Date.valueOf(LocalDate.parse("2001-01-01")))
                        .name("Cycling")
                        .calories(30.0)
                        .build()

        );
        ResponseEntity responseEntity = activityService.storeActivities(newActivities);

        // Check response object from store call
        List<LogActivityDto> newEntries = (List<LogActivityDto>) responseEntity.getBody();
        assert newEntries != null;
        assertEquals(2, newEntries.size());

        Optional<LogActivityDto> running = newEntries.stream().filter(a -> a.getName().equals("Running")).findFirst();
        assertTrue(running.isPresent(), "Running");

        assertEquals(20.0, running.get().getCalories().doubleValue());
        assertEquals("Running", running.get().getName());
        isEqualDate(running.get().getDay(), LocalDate.parse("2001-01-01"));
        Assertions.assertNotNull(running.get().getId());

        Optional<LogActivityDto> cycling = newEntries.stream().filter(a -> a.getName().equals("Cycling")).findFirst();
        assertTrue(cycling.isPresent(), "Cycling");

        assertEquals(30.0, cycling.get().getCalories().doubleValue());
        assertEquals("Cycling", cycling.get().getName());
        isEqualDate(cycling.get().getDay(), LocalDate.parse("2001-01-01"));
        Assertions.assertNotNull(cycling.get().getId());

        // Check response object from get activities for day
        ResponseEntity activitiesForDay = activityService.getActivitiesForDay("2001-01-01", false);
        Assert.isTrue(200 == activitiesForDay.getStatusCodeValue(), "message");

        List<LogActivityDto> newResponseEntries = (List<LogActivityDto>) activitiesForDay.getBody();
        assert newResponseEntries != null;
        assertEquals(2, newResponseEntries.size());

        Optional<LogActivityDto> runningResponse = newResponseEntries.stream().filter(a -> a.getName().equals("Running")).findFirst();
        assertTrue(runningResponse.isPresent(), "Running");

        assertEquals(20.0, runningResponse.get().getCalories().doubleValue());
        assertEquals("Running", runningResponse.get().getName());
        isEqualDate(runningResponse.get().getDay(), LocalDate.parse("2001-01-01"));
        Assertions.assertNotNull(runningResponse.get().getId());

        Optional<LogActivityDto> cyclingResponse = newResponseEntries.stream().filter(a -> a.getName().equals("Cycling")).findFirst();
        assertTrue(cyclingResponse.isPresent(), "Cycling");

        assertEquals(30.0, cyclingResponse.get().getCalories().doubleValue());
        assertEquals("Cycling", cyclingResponse.get().getName());
        isEqualDate(cyclingResponse.get().getDay(), LocalDate.parse("2001-01-01"));
        Assertions.assertNotNull(cyclingResponse.get().getId());

        // delete 1 activity. remove cycling
        ResponseEntity responseEntity1 = activityService.deleteActivity(cyclingResponse.get().getId());
        Assert.isTrue(200 == activitiesForDay.getStatusCodeValue(), "message");

        // Check response object from get activities for day.
        activitiesForDay = activityService.getActivitiesForDay("2001-01-01", false);
        Assert.isTrue(200 == activitiesForDay.getStatusCodeValue(), "message");

        newResponseEntries = (List<LogActivityDto>) activitiesForDay.getBody();
        assert newResponseEntries != null;
        assertEquals(1, newResponseEntries.size());

        runningResponse = newResponseEntries.stream().filter(a -> a.getName().equals("Running")).findFirst();
        assertTrue(runningResponse.isPresent(), "Running");

        // Update Running
        runningResponse.get().setCalories(44.0);
        responseEntity = activityService.storeActivities(Collections.singletonList(runningResponse.get()));
        Assert.isTrue(200 == responseEntity.getStatusCodeValue());
        newResponseEntries = (List<LogActivityDto>) responseEntity.getBody();
        assert newResponseEntries != null;
        assertEquals(1, newResponseEntries.size());

        activitiesForDay = activityService.getActivitiesForDay("2001-01-01", false);
        Assert.isTrue(200 == activitiesForDay.getStatusCodeValue());
        newResponseEntries = (List<LogActivityDto>) activitiesForDay.getBody();
        assert newResponseEntries != null;
        assertEquals(1, newResponseEntries.size());
        runningResponse = newResponseEntries.stream().filter(a -> a.getName().equals("Running")).findFirst();
        assertTrue(runningResponse.isPresent(), "Running");
        assertTrue(runningResponse.get().getCalories().equals(44.0), "Calorien bijgewerkt");


    }

//    @Test
//    public void testPostActivities() {
//        List<LogActivityDto> newActivities = Arrays.asList(
//                LogActivityDto.builder()
//                        .day(Date.valueOf(LocalDate.parse("2001-01-01")))
//                        .name("Running")
//                        .calories(20.0)
//                        .build(),
//                LogActivityDto.builder()
//                        .day(Date.valueOf(LocalDate.parse("2001-01-01")))
//                        .name("Cycling")
//                        .calories(30.0)
//                        .build()
//
//        );
//        ResponseEntity<List<LogActivityDto>> responseEntity = activityService.postActivities("2001-01-01", newActivities);
//
//    }
}

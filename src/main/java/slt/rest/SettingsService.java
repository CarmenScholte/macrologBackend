package slt.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import slt.connectivity.strava.StravaActivityService;
import slt.database.SettingsRepository;
import slt.database.WeightRepository;
import slt.database.entities.Setting;
import slt.database.entities.Weight;
import slt.dto.*;
import slt.security.ThreadLocalHolder;
import slt.security.UserInfo;
import slt.util.LocalDateParser;

import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/settings")
@Api(value = "settings")
public class SettingsService {

    @Autowired
    private SettingsRepository settingsRepo;

    @Autowired
    private WeightRepository weightRepo;

    @Autowired
    private WeightService weightService;

    @Autowired
    private MyModelMapper myModelMapper;

    @Autowired
    private StravaActivityService stravaActivityService;

    @ApiOperation(value = "Store new settingDto or change existing one")
    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity storeSetting(@RequestBody SettingDto settingDto) {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        if ("weight".equals(settingDto.getName())) {
            weightService.storeWeightEntry(
                    new WeightDto(null,
                            Double.valueOf(settingDto.getValue()),
                            settingDto.getDay() == null ? LocalDate.now() : settingDto.getDay().toLocalDate(),
                            null));
        }
        if (settingDto.getDay() == null) {
            settingDto.setDay(Date.valueOf(LocalDate.now()));
        }
        Setting setting = myModelMapper.getConfiguredMapper().map(settingDto, Setting.class);
        settingsRepo.putSetting(userInfo.getUserId(), setting);
        return ResponseEntity.status(HttpStatus.OK).build();
    }

//    @ApiOperation(value = "Store new UserSettingsDto or change existing values")
//    @PutMapping(produces = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity storeUserSettings(@RequestBody UserSettingsDto userSettingsDto) {
//        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
//
//        //TODO make put method with UserSettingsDto (don't forget weight handling)
//
//        return ResponseEntity.status(HttpStatus.OK).build();
//    }

    @ApiOperation(value = "Get connectivity settings")
    @GetMapping(path = "/connectivity/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SyncedAccount> getConnectivitySetting(@PathVariable("name") String name) {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        SyncedAccount syncedAccount = stravaActivityService.getStravaConnectivity(userInfo.getUserId());

        if (syncedAccount == null) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.ok(syncedAccount);
        }
    }
    @ApiOperation(value = "Store connectivity settings")
    @PostMapping(path = "/connectivity/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SyncedAccount> storeConnectivitySetting(@RequestBody SettingDto code) {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        final SyncedAccount syncedAccount = stravaActivityService.registerStravaConnectivity(userInfo.getUserId(), code.getValue());
        return ResponseEntity.ok(syncedAccount);
    }


    @ApiOperation(value = "Disconnect from platform")
    @DeleteMapping(path = "/connectivity/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SyncedAccount> disConnectConnectivitySetting(@PathVariable("name") String name) {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        stravaActivityService.unRegisterStrava(userInfo.getUserId());
        return ResponseEntity.ok().build();
    }

    @ApiOperation(value = "Get user settings")
    @GetMapping(path = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserSettingsDto> getUserSetting() {
        UserSettingsDto userSettingsDto = getUserSettingsDto();
        return ResponseEntity.ok(userSettingsDto);
    }

    @ApiOperation(value = "Get setting")
    @GetMapping(path = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSetting(@PathVariable("name") String name,
                                             @RequestParam(value = "date", required = false) String toDate) {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        slt.database.entities.Setting setting;
        if (StringUtils.isEmpty(toDate)) {
            setting = settingsRepo.getLatestSetting(userInfo.getUserId(), name);
        } else {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                sdf.setLenient(false);
                java.util.Date utilDate = sdf.parse(toDate);
                setting = settingsRepo.getValidSetting(userInfo.getUserId(), name, new Date(utilDate.getTime()));
            } catch (ParseException pe) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        }
        if (setting == null) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.ok(setting.getValue());
        }
    }

    private UserSettingsDto getUserSettingsDto() {
        UserInfo userInfo = ThreadLocalHolder.getThreadLocal().get();
        List<slt.database.entities.Setting> settings = settingsRepo.getAllSettings(userInfo.getUserId());
        List<Weight> weight = weightRepo.getAllWeightEntries(userInfo.getUserId());
        Weight currentWeight = weight.stream().max(Comparator.comparing(Weight::getDay)).orElse(new Weight());
        UserSettingsDto userSettingsDto = mapToUserSettingsDto(settings);
        userSettingsDto.setCurrentWeight(currentWeight.getValue());
        return userSettingsDto;
    }

    private UserSettingsDto mapToUserSettingsDto(List<slt.database.entities.Setting> settings) {
        UserSettingsDto dto = new UserSettingsDto();
        dto.setName(mapSetting(settings, "name"));
        dto.setGender(mapSetting(settings, "gender"));
        String ageSetting = mapSetting(settings, "age");
        dto.setAge(StringUtils.isEmpty(ageSetting) ? null : Integer.valueOf(ageSetting));
        String birthdaySetting = mapSetting(settings, "birthday");
        dto.setBirthday(StringUtils.isEmpty(birthdaySetting) ? null : LocalDateParser.parse(birthdaySetting));
        String heightSetting = mapSetting(settings, "height");
        dto.setHeight(StringUtils.isEmpty(heightSetting) ? null : Integer.valueOf(heightSetting));
        String activitySetting = mapSetting(settings, "activity");
        dto.setActivity(StringUtils.isEmpty(activitySetting) ? null : Double.valueOf(activitySetting));

        String goalProteinSetting = mapSetting(settings, "goalProtein");
        dto.setGoalProtein(StringUtils.isEmpty(goalProteinSetting) ? null : Integer.valueOf(goalProteinSetting));
        String goalFatSetting = mapSetting(settings, "goalFat");
        dto.setGoalFat(StringUtils.isEmpty(goalFatSetting) ? null : Integer.valueOf(goalFatSetting));
        String goalCarbsSetting = mapSetting(settings, "goalCarbs");
        dto.setGoalCarbs(StringUtils.isEmpty(goalCarbsSetting) ? null : Integer.valueOf(goalCarbsSetting));

        return dto;
    }

    private String mapSetting(List<slt.database.entities.Setting> settings, String identifier) {
        return settings.stream()
                .filter(s -> s.getName().equals(identifier))
                .max(Comparator.comparing(slt.database.entities.Setting::getDay))
                .orElse(new slt.database.entities.Setting()).getValue();
    }

}

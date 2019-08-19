package slt.connectivity.strava;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import slt.config.StravaConfig;
import slt.connectivity.strava.dto.*;
import slt.database.ActivityRepository;
import slt.database.SettingsRepository;
import slt.database.entities.LogActivity;
import slt.database.entities.Setting;
import slt.dto.SyncedAccount;
import slt.rest.ActivityService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
class StravaActivityServiceTest {

    @Mock
    SettingsRepository settingsRepository;

    @Mock
    ActivityService activityService;

    @Mock
    ActivityRepository activityRepository;

    @Mock
    StravaConfig stravaConfig;

    @Mock
    StravaClient stravaClient;

    @InjectMocks
    StravaActivityService stravaActivityService;

    @BeforeEach
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void isStravaConnectedTrue() {

        when(settingsRepository.getLatestSetting(eq(1), eq("STRAVA_ATHLETE_ID"))).thenReturn(Setting.builder().value("dad").build());
        final boolean stravaConnected = stravaActivityService.isStravaConnected(1);
        assertThat(stravaConnected).isEqualTo(true);
    }

    @Test
    void isStravaConnectedFalse() {

        when(settingsRepository.getLatestSetting(eq(1), eq("STRAVA_ATHLETE_ID"))).thenReturn(null);
        final boolean stravaConnected = stravaActivityService.isStravaConnected(1);
        assertThat(stravaConnected).isEqualTo(false);
    }

    @Test
    void getStravaConnectivityWhenConnected() {

        mockSetting(1, "STRAVA_ATHLETE_ID", "101");
        mockSetting(1, "STRAVA_FIRSTNAME", "B");
        mockSetting(1, "STRAVA_LASTNAME", "C");
        mockSetting(1, "STRAVA_PROFILE", "D");

        when(activityRepository.countByUserIdAndSyncedWith(eq(1), eq("STRAVA"))).thenReturn(1L);

        final SyncedAccount stravaConnectivity = stravaActivityService.getStravaConnectivity(1);
        assertThat(stravaConnectivity.getSyncedAccountId()).isEqualTo(101);
        assertThat(stravaConnectivity.getName()).isEqualTo("B C");
        assertThat(stravaConnectivity.getImage()).isEqualTo("D");
        assertThat(stravaConnectivity.getNumberActivitiesSynced()).isEqualTo(1L);
        assertThat(stravaConnectivity.getSyncedApplicationId()).isNull();
    }

    @Test
    void getStravaConnectivityNotConnected() {

        mockSetting(1, "STRAVA_ATHLETE_ID", null);

        when(activityRepository.countByUserIdAndSyncedWith(eq(1), eq("STRAVA"))).thenReturn(1L);
        when(stravaConfig.getClientId()).thenReturn(201);

        final SyncedAccount stravaConnectivity = stravaActivityService.getStravaConnectivity(1);
        assertThat(stravaConnectivity.getSyncedAccountId()).isNull();
        assertThat(stravaConnectivity.getName()).isNull();
        assertThat(stravaConnectivity.getImage()).isNull();
        assertThat(stravaConnectivity.getNumberActivitiesSynced()).isNull();
        assertThat(stravaConnectivity.getSyncedApplicationId()).isEqualTo(201);
    }


    private void mockSetting(Integer userId, String name, String value) {
        if (value == null) {
            when(settingsRepository.getLatestSetting(eq(userId), eq(name))).thenReturn(null);

        } else {
            when(settingsRepository.getLatestSetting(eq(userId), eq(name))).thenReturn(Setting.builder().value(value).build());
        }
    }

    @Test
    void registerStravaConnectivityTokenOK() {

        settingsRepository.putSetting(eq(1), any(Setting.class));
        settingsRepository.putSetting(eq(1), any(Setting.class));

        Mockito.times(8);
        final StravaToken stravaToken = StravaToken.builder()
                .access_token("a")
                .expires_at(1L)
                .expires_in(2L)
                .athlete(StravaAthleteDto.builder().firstname("jan").lastname("patat").profile("profile").id(20L).build())
                .build();
        when(stravaClient.getStravaToken(eq("appelflap"))).thenReturn(stravaToken);
        when(activityRepository.countByUserIdAndSyncedWith(eq(1), eq("STRAVA"))).thenReturn(1L);

        final SyncedAccount syncedAccount = stravaActivityService.registerStravaConnectivity(1, "appelflap");
        assertThat(syncedAccount.getNumberActivitiesSynced()).isEqualTo(1L);
        assertThat(syncedAccount.getSyncedAccountId()).isEqualTo(20L);
        assertThat(syncedAccount.getName()).isEqualTo("jan patat");
    }


    @Test
    void registerStravaConnectivityTokenNietOk() {

        settingsRepository.putSetting(eq(1), any(Setting.class));

        Mockito.times(8);
        when(stravaClient.getStravaToken(eq("appelflap"))).thenReturn(null);
        when(activityRepository.countByUserIdAndSyncedWith(eq(1), eq("STRAVA"))).thenReturn(1L);

        final SyncedAccount syncedAccount = stravaActivityService.registerStravaConnectivity(1, "appelflap");
        assertThat(syncedAccount).isNull();

    }

    @Test
    void unRegisterStravaOK() {

        mockSetting(1, "STRAVA_ATHLETE_ID", "12");
        mockSetting(1, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(1, "STRAVA_REFRESH_TOKEN", "B");

        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(1, "STRAVA_EXPIRES_AT", "" + toEpochSecond);
        when(stravaClient.unregister(any(StravaToken.class))).thenReturn(true);
        settingsRepository.deleteAllForUser(eq(1), any(String.class));
        times(8);

        stravaActivityService.unRegisterStrava(1);

        verify(settingsRepository, times(8)).deleteAllForUser(eq(1), any(String.class));
    }

    @Test
    void unRegisterStravaNietOK() {

        // Voor ingelogd zijn:
        mockSetting(1, "STRAVA_ATHLETE_ID", null);

        stravaActivityService.unRegisterStrava(1);

        verify(settingsRepository).getLatestSetting(eq(1), any(String.class));
        verifyNoMoreInteractions(settingsRepository);

    }

    @Test
    void getStravaActivitiesForDay() {

        when(stravaClient.getActivitiesForDay(eq("a"), any(LocalDate.class))).thenReturn(new ArrayList<>());

        final List<ListedActivityDto> results = stravaActivityService.getStravaActivitiesForDay(StravaToken.builder().access_token("a").build(), LocalDate.now());
        assertThat(results.isEmpty()).isTrue();
    }

    @Test
    void testExtraStravaActivitiesStravaConnectedNoForceNoStravaResults() {

        // Voor ingelogd zijn:
        mockSetting(1, "STRAVA_ATHLETE_ID", "A");

        // Voor token:
        mockSetting(1, "STRAVA_ATHLETE_ID", "12");
        mockSetting(1, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(1, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(1, "STRAVA_EXPIRES_AT", "" + toEpochSecond);

        when(stravaClient.getActivitiesForDay(eq("a"), any(LocalDate.class))).thenReturn(Arrays.asList(

        ));


        List<LogActivity> storedMacroLogActivities = Arrays.asList(LogActivity.builder().build());
        final List<LogActivity> responseActivities = stravaActivityService.getExtraStravaActivities(storedMacroLogActivities, 1, LocalDate.parse("2001-01-01"), false);

        assertThat(responseActivities).hasSize(0);
    }

    @Test
    void testExtraStravaActivitiesStravaConnectedNoForceWithStravaResults() {

        // Voor ingelogd zijn:
        mockSetting(1, "STRAVA_ATHLETE_ID", "A");

        // Voor token:
        mockSetting(1, "STRAVA_ATHLETE_ID", "12");
        mockSetting(1, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(1, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(1, "STRAVA_EXPIRES_AT", "" + toEpochSecond);


        List<LogActivity> storedMacroLogActivities = Arrays.asList(LogActivity.builder().build());
        final List<LogActivity> responseActivities = stravaActivityService.getExtraStravaActivities(storedMacroLogActivities, 1, LocalDate.parse("2001-01-01"), false);

        assertThat(responseActivities).hasSize(0);
    }

    @Test
    void testExtraStravaActivitiesStravaConnectedNoForceWithStravaResultsAlreadyKnown() {

        // Voor ingelogd zijn:
        mockSetting(1, "STRAVA_ATHLETE_ID", "A");

        // Voor token:
        mockSetting(1, "STRAVA_ATHLETE_ID", "12");
        mockSetting(1, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(1, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(1, "STRAVA_EXPIRES_AT", "" + toEpochSecond);

        when(stravaClient.getActivitiesForDay(eq("A"), any(LocalDate.class))).thenReturn(Arrays.asList(
                ListedActivityDto.builder().id(1L).build()
        ));

        when(stravaClient.getActivityDetail(eq("A"), eq(1L))).thenReturn(
                ActivityDetailsDto.builder().id(1L).build());


        List<LogActivity> storedMacroLogActivities = Arrays.asList(
                LogActivity.builder().syncedId(1L).build());
        final List<LogActivity> responseActivities = stravaActivityService.getExtraStravaActivities(storedMacroLogActivities, 1, LocalDate.parse("2001-01-01"), false);

        assertThat(responseActivities).hasSize(0);
    }

    @Test
    void testExtraStravaActivitiesStravaConnectedWithForceWithStravaResultsAlreadyKnownDeleted() {

        // Voor ingelogd zijn:
        mockSetting(1, "STRAVA_ATHLETE_ID", "A");

        // Voor token:
        mockSetting(1, "STRAVA_ATHLETE_ID", "12");
        mockSetting(1, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(1, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(1, "STRAVA_EXPIRES_AT", "" + toEpochSecond);

        when(stravaClient.getActivitiesForDay(eq("A"), any(LocalDate.class))).thenReturn(Arrays.asList(
                ListedActivityDto.builder().id(1L).build()
        ));

        when(stravaClient.getActivityDetail(eq("A"), eq(1L))).thenReturn(
                ActivityDetailsDto.builder().id(1L).build());


        List<LogActivity> storedMacroLogActivities = Arrays.asList(
                LogActivity.builder().syncedId(1L).status("DELETED").build());
        final List<LogActivity> responseActivities = stravaActivityService.getExtraStravaActivities(storedMacroLogActivities, 1, LocalDate.parse("2001-01-01"), true);

        when(activityRepository.saveActivity(eq(1), any(LogActivity.class))).thenReturn(LogActivity.builder().build());


        // dirty aanpassing van de parameter lijst naar niet meer gedelete
        assertThat(storedMacroLogActivities.get(0).getStatus()).isNull();

        // already in result, not in extra results
        assertThat(responseActivities).hasSize(0);
    }

    @Test
    public void testExpiredMechanisme() {
        // Voor ingelogd zijn:
        mockSetting(1, "STRAVA_ATHLETE_ID", "A");

        // Voor token nu op expired:
        mockSetting(1, "STRAVA_ATHLETE_ID", "12");
        mockSetting(1, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(1, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().minusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(1, "STRAVA_EXPIRES_AT", "" + toEpochSecond);

        when(stravaClient.refreshToken(eq("B"))).thenReturn(StravaToken.builder().expires_at(LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC)).build());


        stravaActivityService.unRegisterStrava(1);

        verify(settingsRepository, times(3)).saveSetting(eq(1), any(Setting.class));

    }

    @Test
    public void setupStravaWebhooksubscriptionUit() {

        when(stravaConfig.getClientId()).thenReturn(2);
        when(stravaConfig.getClientSecret()).thenReturn("a");
        when(stravaConfig.getVerifytoken()).thenReturn("uit");

        StravaActivityService myStravaActivityService = new StravaActivityService(settingsRepository, activityRepository, stravaConfig, stravaClient);

        verify(stravaConfig, times(3)).getVerifytoken();
        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig, stravaClient);
    }

    @Test
    public void setupStravaWebhooksubscriptionAan() {

        when(stravaConfig.getClientId()).thenReturn(2);
        when(stravaConfig.getClientSecret()).thenReturn("a");
        when(stravaConfig.getVerifytoken()).thenReturn("iets");
        when(stravaClient.viewWebhookSubscription(any(), any())).thenReturn(SubscriptionInformation.builder().build());
        StravaActivityService myStravaActivityService = new StravaActivityService(settingsRepository, activityRepository, stravaConfig, stravaClient);

        verify(stravaConfig, times(3)).getVerifytoken();
        verify(stravaConfig, times(1)).getClientId();
        verify(stravaConfig, times(1)).getClientSecret();
        verify(stravaClient).viewWebhookSubscription(any(), any());
        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig);
    }

    @Test
    public void setupStravaWebhooksubscriptionAanNietGevonden() {

        when(stravaConfig.getClientId()).thenReturn(2);
        when(stravaConfig.getClientSecret()).thenReturn("a");
        when(stravaConfig.getVerifytoken()).thenReturn("iets");
        when(stravaClient.viewWebhookSubscription(any(), any())).thenReturn(null);
        StravaActivityService myStravaActivityService = new StravaActivityService(settingsRepository, activityRepository, stravaConfig, stravaClient);

        verify(stravaConfig, times(3)).getVerifytoken();
        verify(stravaConfig, times(1)).getClientId();
        verify(stravaConfig, times(1)).getClientSecret();
        verify(stravaClient).viewWebhookSubscription(any(), any());
        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig);
    }

    @Test
    public void receiveWebhookWrongSubscription() {

        stravaActivityService.stravaSubscriptionId = 1;
        stravaActivityService.receiveWebHookEvent(WebhookEvent.builder().subscription_id(2).build());

        verify(stravaConfig, times(1)).getVerifytoken(); // Initialization of StravaActivityService
        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig);
    }

    @Test
    public void receiveWebhookAthleteNotFound() {

        stravaActivityService.stravaSubscriptionId = 1;
        when(settingsRepository.findByKeyValue(any(), any())).thenReturn(Optional.empty());

        stravaActivityService.receiveWebHookEvent(WebhookEvent.builder().subscription_id(1).owner_id(20L).build());

        verify(stravaConfig, times(1)).getVerifytoken(); // Initialization of StravaActivityService
        verify(settingsRepository).findByKeyValue(any(), any());

        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig);
    }

    @Test
    public void receiveWebhookNotActivity() {

        stravaActivityService.stravaSubscriptionId = 1;
        when(settingsRepository.findByKeyValue(any(), any())).thenReturn(Optional.of(Setting.builder().userId(123).build()));

        // Voor token:
        mockSetting(123, "STRAVA_ATHLETE_ID", "20");
        mockSetting(123, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(123, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(123, "STRAVA_EXPIRES_AT", "" + toEpochSecond);

        stravaActivityService.receiveWebHookEvent(WebhookEvent.builder().subscription_id(1).owner_id(20L).build());

        verify(stravaConfig, times(1)).getVerifytoken(); // Initialization of StravaActivityService
        verify(settingsRepository).findByKeyValue(any(), any());
        verify(settingsRepository, times(3)).getLatestSetting(any(), any());

        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig);
    }

    @Test
    public void receiveWebhookNoMatchingStravaActivity() {

        stravaActivityService.stravaSubscriptionId = 1;
        when(settingsRepository.findByKeyValue(any(), any())).thenReturn(Optional.of(Setting.builder().userId(123).build()));

        // Voor token:
        mockSetting(123, "STRAVA_ATHLETE_ID", "20");
        mockSetting(123, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(123, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(123, "STRAVA_EXPIRES_AT", "" + toEpochSecond);

        when(activityRepository.findByUserIdAndSyncIdAndSyncedWith(eq(123), eq("STRAVA"), any())).thenReturn(Optional.empty());

        stravaActivityService.receiveWebHookEvent(WebhookEvent.builder().subscription_id(1).owner_id(20L).object_id(3l).object_type("activity").build());

        verify(stravaConfig, times(1)).getVerifytoken(); // Initialization of StravaActivityService
        verify(settingsRepository).findByKeyValue(any(), any());
        verify(settingsRepository, times(3)).getLatestSetting(any(), any());
        verify(activityRepository).findByUserIdAndSyncIdAndSyncedWith(any(), any(), anyLong());

        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig);
    }

    @Test
    public void receiveWebhookUpdateActivity() {

        stravaActivityService.stravaSubscriptionId = 1;
        when(settingsRepository.findByKeyValue(any(), any())).thenReturn(Optional.of(Setting.builder().userId(123).build()));

        // Voor token:
        mockSetting(123, "STRAVA_ATHLETE_ID", "20");
        mockSetting(123, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(123, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(123, "STRAVA_EXPIRES_AT", "" + toEpochSecond);

        when(activityRepository.findByUserIdAndSyncIdAndSyncedWith(eq(123), eq("STRAVA"), any())).thenReturn(Optional.of(LogActivity.builder().build()));
        when(stravaClient.getActivityDetail(any(), eq(3L))).thenReturn(ActivityDetailsDto.builder().build());

        stravaActivityService.receiveWebHookEvent(WebhookEvent.builder().subscription_id(1).owner_id(20L).object_id(3L).object_type("activity").aspect_type("create").build());

        verify(stravaConfig, times(1)).getVerifytoken(); // Initialization of StravaActivityService
        verify(settingsRepository).findByKeyValue(any(), any());
        verify(settingsRepository, times(3)).getLatestSetting(any(), any());
        verify(activityRepository).findByUserIdAndSyncIdAndSyncedWith(any(), any(), anyLong());
        verify(stravaClient).getActivityDetail(any(), any());
        verify(activityRepository).saveActivity(eq(123), any());

        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig);
    }

    @Test
    public void receiveWebhookSaveNewActivity() {

        stravaActivityService.stravaSubscriptionId = 1;
        when(settingsRepository.findByKeyValue(any(), any())).thenReturn(Optional.of(Setting.builder().userId(123).build()));

        // Voor token:
        mockSetting(123, "STRAVA_ATHLETE_ID", "20");
        mockSetting(123, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(123, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(123, "STRAVA_EXPIRES_AT", "" + toEpochSecond);

        when(activityRepository.findByUserIdAndSyncIdAndSyncedWith(eq(123), eq("STRAVA"), any())).thenReturn(Optional.empty());
        when(stravaClient.getActivityDetail(any(), eq(3L))).thenReturn(ActivityDetailsDto.builder().start_date("2018-02-16T14:52:54Z").build());

        stravaActivityService.receiveWebHookEvent(WebhookEvent.builder().subscription_id(1).owner_id(20L).object_id(3L).object_type("activity").aspect_type("update").build());

        verify(stravaConfig, times(1)).getVerifytoken(); // Initialization of StravaActivityService
        verify(settingsRepository).findByKeyValue(any(), any());
        verify(settingsRepository, times(3)).getLatestSetting(any(), any());
        verify(activityRepository).findByUserIdAndSyncIdAndSyncedWith(any(), any(), anyLong());
        verify(stravaClient).getActivityDetail(any(), any());
        verify(activityRepository).saveActivity(eq(123), any());

        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig);
    }

    @Test
    public void receiveWebhookDeleteKnownActivty() {

        stravaActivityService.stravaSubscriptionId = 1;
        when(settingsRepository.findByKeyValue(any(), any())).thenReturn(Optional.of(Setting.builder().userId(123).build()));

        // Voor token:
        mockSetting(123, "STRAVA_ATHLETE_ID", "20");
        mockSetting(123, "STRAVA_ACCESS_TOKEN", "A");
        mockSetting(123, "STRAVA_REFRESH_TOKEN", "B");
        final long toEpochSecond = LocalDateTime.now().plusDays(1).toEpochSecond(ZoneOffset.UTC);
        mockSetting(123, "STRAVA_EXPIRES_AT", "" + toEpochSecond);

        when(activityRepository.findByUserIdAndSyncIdAndSyncedWith(eq(123), eq("STRAVA"), any())).thenReturn(Optional.of(LogActivity.builder().id(300L).build()));

        stravaActivityService.receiveWebHookEvent(WebhookEvent.builder().subscription_id(1).owner_id(20L).object_id(3L).object_type("activity").aspect_type("delete").build());

        verify(stravaConfig, times(1)).getVerifytoken(); // Initialization of StravaActivityService
        verify(settingsRepository).findByKeyValue(any(), any());
        verify(settingsRepository, times(3)).getLatestSetting(any(), any());
        verify(activityRepository).findByUserIdAndSyncIdAndSyncedWith(any(), any(), anyLong());
        verify(activityRepository).deleteLogActivity(eq(123), eq(300L));

        verifyNoMoreInteractions(stravaClient, settingsRepository, activityRepository, stravaConfig);
    }

}
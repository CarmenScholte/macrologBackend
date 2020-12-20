package slt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import slt.config.GoogleConfig;
import slt.connectivity.google.GoogleClient;
import slt.connectivity.oath2.Oath2Token;
import slt.database.SettingsRepository;
import slt.database.entities.Setting;
import slt.database.entities.UserAccount;
import slt.dto.ConnectivityStatusDto;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
class GoogleMailServiceTest {

    @Mock
    SettingsRepository settingsRepository;

    @Mock
    GoogleConfig googleConfig;

    @Mock
    GoogleClient googleClient;

    @InjectMocks
    GoogleMailService googleMailService;

    @BeforeEach
    void beforEach(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void mailStatusStaatUit() {
        when(googleConfig.getClientSecret()).thenReturn("uit");
        GoogleMailService mailService = new GoogleMailService(settingsRepository, googleConfig, googleClient);
        final ConnectivityStatusDto mailStatus = mailService.getMailStatus();
        assertThat(mailStatus.isConnected()).isEqualTo(false);
    }

    @Test
    void getMailStatusUitMaarGeenSetting() {
            when(googleConfig.getClientSecret()).thenReturn("a");
            when(settingsRepository.getLatestSetting(any(), any())).thenReturn(null);
            GoogleMailService mailService = new GoogleMailService(settingsRepository, googleConfig, googleClient);
            final ConnectivityStatusDto mailStatus = mailService.getMailStatus();
            assertThat(mailStatus.isConnected()).isEqualTo(false);
    }

    @Test
    void getMailStatusAanMetSetting() {
        when(googleConfig.getClientSecret()).thenReturn("a");
        when(settingsRepository.getLatestSetting(any(), any())).thenReturn(Setting.builder().build());
        GoogleMailService mailService = new GoogleMailService(settingsRepository, googleConfig, googleClient);
        final ConnectivityStatusDto mailStatus = mailService.getMailStatus();
        assertThat(mailStatus.isConnected()).isEqualTo(true);
    }

    @Test
    void registerWithCodeNoSetting() {

        when(googleClient.getAuthorizationToken(eq("code"))).thenReturn(null);
        googleMailService.registerWithCode("code");

        verify(settingsRepository,times(1)).putSetting(eq(-1), any());
    }

    @Test
    void registerWithCodeWithSetting() {

        when(googleConfig.getClientSecret()).thenReturn("s");
        assertThat(googleMailService.getMailStatus().isConnected()).isEqualTo(false);

        when(googleClient.getAuthorizationToken(eq("code"))).thenReturn(Oath2Token.builder().expires_in(200L).build());
        googleMailService.registerWithCode("code");
        assertThat(googleMailService.getMailStatus().isConnected()).isEqualTo(true);

        verify(settingsRepository,times(4)).putSetting(eq(-1), any());
    }

    @Test
    void sendPasswordRetrievalMail() throws MessagingException, IOException, GeneralSecurityException {
        when(googleConfig.getClientSecret()).thenReturn("secret");
        MimeMessage mimemessage = mock(MimeMessage.class);
        when(googleClient.createEmail(any(), any(), any(), any())).thenReturn(mimemessage);

        // Niet expired token:
        Instant instant = Instant.now();
        instant = instant.plus(20, ChronoUnit.MINUTES);
        when(settingsRepository.getLatestSetting(eq(-1), any())).thenReturn(Setting.builder().value(String.valueOf(instant.getEpochSecond())).build());

        GoogleMailService mailService = new GoogleMailService(settingsRepository, googleConfig, googleClient);
        mailService.sendPasswordRetrievalMail("mail",null,UserAccount.builder().build());

        verify(settingsRepository,times(5)).getLatestSetting(any(), any());

        verify(googleClient,times(1)).sendMail(any(Oath2Token.class), any(MimeMessage.class));    }

    @Test
    void sendConfirmationMail() throws MessagingException, IOException, GeneralSecurityException {
        when(googleConfig.getClientSecret()).thenReturn("secret");
        MimeMessage mimemessage = mock(MimeMessage.class);
        when(googleClient.createEmail(any(), any(), any(), any())).thenReturn(mimemessage);

        // Niet expired token:
        Instant instant = Instant.now();
        instant = instant.plus(20, ChronoUnit.MINUTES);
        when(settingsRepository.getLatestSetting(eq(-1), any())).thenReturn(Setting.builder().value(String.valueOf(instant.getEpochSecond())).build());

        GoogleMailService mailService = new GoogleMailService(settingsRepository, googleConfig, googleClient);
        mailService.sendConfirmationMail("mail",UserAccount.builder().build());

        verify(settingsRepository,times(5)).getLatestSetting(any(), any());

        verify(googleClient,times(1)).sendMail(any(Oath2Token.class), any(MimeMessage.class));    }

    @Test
    void sendTestMail() throws MessagingException, IOException, GeneralSecurityException {

        when(googleConfig.getClientSecret()).thenReturn("secret");
        MimeMessage mimemessage = mock(MimeMessage.class);
        when(googleClient.createEmail(any(), any(), any(), any())).thenReturn(mimemessage);
        // Niet expired token:
        Instant instant = Instant.now();
        instant = instant.plus(20, ChronoUnit.MINUTES);
        when(settingsRepository.getLatestSetting(eq(-1), any())).thenReturn(Setting.builder().value(String.valueOf(instant.getEpochSecond())).build());
        GoogleMailService mailService = new GoogleMailService(settingsRepository, googleConfig, googleClient);

        mailService.sendTestMail("mail");

        verify(settingsRepository,times(5)).getLatestSetting(any(), any());

        verify(googleClient,times(1)).sendMail(any(Oath2Token.class), any(MimeMessage.class));
    }
}
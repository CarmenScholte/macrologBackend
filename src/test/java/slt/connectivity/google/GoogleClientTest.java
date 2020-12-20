package slt.connectivity.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import slt.config.GoogleConfig;
import slt.connectivity.oath2.Oath2Token;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
class GoogleClientTest {

    @Mock
    GoogleConfig googleConfig;

    @Mock
    RestTemplate restTemplate;

    @InjectMocks
    GoogleClient googleClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void refreshToken() {
        when(googleConfig.getClientId()).thenReturn("1");
        when(googleConfig.getClientSecret()).thenReturn("2");

        ResponseEntity<Oath2Token> response = mock(ResponseEntity.class);
        when(response.getBody()).thenReturn(Oath2Token.builder().build());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(Oath2Token.class))).thenReturn(response);

        final Oath2Token refreshedToken = googleClient.refreshToken("r");
        assertThat(refreshedToken).isNotNull();
    }

    @Test
    void refreshTokenClientError() {
        when(googleConfig.getClientId()).thenReturn("1");
        when(googleConfig.getClientSecret()).thenReturn("2");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(Oath2Token.class))).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        final Oath2Token refreshedToken = googleClient.refreshToken("r");
        assertThat(refreshedToken).isNull();
    }

    @Test
    void refreshTokenRestError() {
        when(googleConfig.getClientId()).thenReturn("1");
        when(googleConfig.getClientSecret()).thenReturn("2");

        ArgumentCaptor<HttpEntity<HashMap>> argumentCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), argumentCaptor.capture(), eq(Oath2Token.class))).thenThrow(new RestClientException("a"));

        final Oath2Token refreshedToken = googleClient.refreshToken("r");
        assertThat(refreshedToken).isNull();
        assertThat(argumentCaptor.getValue().getBody().get("grant_type")).isEqualTo("refresh_token");
        assertThat(argumentCaptor.getValue().getBody().get("refresh_token")).isEqualTo("r");
    }

    @Test
    void getAuthorizationToken() {
        when(googleConfig.getClientId()).thenReturn("1");
        when(googleConfig.getClientSecret()).thenReturn("2");

        ArgumentCaptor<HttpEntity<HashMap>> argumentCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), argumentCaptor.capture(), eq(Oath2Token.class))).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        final Oath2Token refreshedToken = googleClient.getAuthorizationToken("r");
        assertThat(refreshedToken).isNull();
        assertThat(argumentCaptor.getValue().getBody().get("grant_type")).isEqualTo("authorization_code");
        assertThat(argumentCaptor.getValue().getBody().get("code")).isEqualTo("r");
    }

    @Test
    void sendMailNoToken() throws IOException, GeneralSecurityException {
        Message message = mock(Message.class);
        googleClient.sendMail(null, message);
        verify(googleConfig, times(0)).getApplicationName();
    }

    @Test
    void sendMailWithToken() {
        Message message = mock(Message.class);


        assertThrows(GoogleJsonResponseException.class, () -> {
            googleClient.sendMail(Oath2Token.builder().build(),message);
        });
    }

    @Test
    void createMail() throws MessagingException {
        final MimeMessage email = googleClient.createEmail("to", "from", "subject", "body");
        assertThat(email.getFrom().length).isEqualTo(1);
        assertThat(email.getAllRecipients().length).isEqualTo(1);
    }

    @Test
    void createGoogleMail() throws IOException, MessagingException {
        Message message = mock(MimeMessage.class);
        final com.google.api.services.gmail.model.Message messageWithEmail = googleClient.createMessageWithEmail(message);

        assertThat(messageWithEmail.getRaw()).isNotNull();
    }
}
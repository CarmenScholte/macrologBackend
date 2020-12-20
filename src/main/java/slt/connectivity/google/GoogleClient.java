package slt.connectivity.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import slt.config.GoogleConfig;
import slt.connectivity.oath2.Oath2Token;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Component
public class GoogleClient {


    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String GRANT_TYPE = "grant_type";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String ERROR_MESSAGE = "Fout bij versturen.";

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    GoogleConfig googleConfig;

    public void sendMail(Oath2Token oath2Token, Message message) throws IOException, GeneralSecurityException {

        if (oath2Token == null){
            log.error("Unable to send mail. No token available");
            return;
        }

        GoogleCredential credential = new GoogleCredential().setAccessToken(oath2Token.getAccess_token());

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        try {
            com.google.api.services.gmail.model.Message googleMessage = createMessageWithEmail(message);
            final Gmail service = new Gmail.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName( googleConfig.getApplicationName())
                    .build();
            final com.google.api.services.gmail.model.Message execute = service
                    .users()
                    .messages()
                    .send("me", googleMessage)
                    .execute();
            log.debug("Mail {} send.",execute.getId());
        } catch (MessagingException e) {
            log.error("Error during sending mail",e);
        }
    }

    public MimeMessage createEmail(String to,
                                          String from,
                                          String subject,
                                          String bodyText)
            throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session); // NOSONAR

        email.setFrom(new InternetAddress(from));
        email.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(to));
        email.setSubject(subject);
        email.setContent(bodyText, "text/html");
        return email;
    }

    protected com.google.api.services.gmail.model.Message createMessageWithEmail(Message emailContent)
            throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        com.google.api.services.gmail.model.Message message = new com.google.api.services.gmail.model.Message();
        message.setRaw(encodedEmail);
        return message;
    }

    public Oath2Token getAuthorizationToken(String authorizationCode)  {

        String clientId = googleConfig.getClientId();
        String clientSecret = googleConfig.getClientSecret();

        Map<String,String> reqPayload = new HashMap();
        reqPayload.put(CLIENT_ID, clientId);
        reqPayload.put(CLIENT_SECRET, clientSecret);
        reqPayload.put("code", authorizationCode);
        reqPayload.put(GRANT_TYPE, "authorization_code");
        reqPayload.put("redirect_uri",googleConfig.getRedirectUri());

        return getAuthorizationToken(reqPayload);
    }

    private Oath2Token getAuthorizationToken(Map<String,String> reqPayload) {

        try {
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            final HttpEntity<HashMap> entity = new HttpEntity(reqPayload, headers);
            ResponseEntity<Oath2Token> responseEntity = restTemplate.exchange(TOKEN_URL, HttpMethod.POST, entity, Oath2Token.class);

            return responseEntity.getBody();

        } catch (HttpClientErrorException httpClientErrorException) {
            log.error(httpClientErrorException.getResponseBodyAsString());
            log.error(ERROR_MESSAGE + " {}", httpClientErrorException.getLocalizedMessage(), httpClientErrorException);
            return null;
        } catch (RestClientException restClientException) {
            log.error(ERROR_MESSAGE + " {}", restClientException.getLocalizedMessage(), restClientException);
            return null;
        }
    }

    public Oath2Token refreshToken(String refreshToken) {
        String grantType = "refresh_token";

        String clientId = googleConfig.getClientId();
        String clientSecret = googleConfig.getClientSecret();

        Map<String,String> reqPayload = new HashMap();
        reqPayload.put(CLIENT_ID, clientId);
        reqPayload.put(CLIENT_SECRET, clientSecret);
        reqPayload.put("refresh_token", refreshToken);
        reqPayload.put(GRANT_TYPE, grantType);

        return getAuthorizationToken(reqPayload);
    }
}

package slt.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import slt.config.GoogleConfig;
import slt.connectivity.google.GoogleClient;
import slt.connectivity.oath2.Oath2Token;
import slt.database.SettingsRepository;
import slt.database.entities.Setting;
import slt.database.entities.UserAccount;
import slt.dto.ConnectivityStatusDto;

import javax.mail.internet.MimeMessage;
import javax.transaction.Transactional;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class GoogleMailService {

    private static final String GMAIL_CLIENT_AUTHORIZATION_CODE = "GMAIL_CLIENT_AUTHORIZATION_CODE";
    private static final String GMAIL_ACCESS_TOKEN = "GMAIL_ACCESS_TOKEN";
    private static final String GMAIL_EXPIRES_AT = "GMAIL_EXPIRES_AT";
    private static final String GMAIL_REFRESH_TOKEN = "GMAIL_REFRESH_TOKEN";
    private static final String MACROLOG_FROM_ADDRESS = "macrologwebapp@gmail.com";
    private static final String MAIL_SEND_TO_DEBUGLINE = "Mail send to";
    private static final Integer adminUserId = -1;

    private final SettingsRepository settingsRepository;
    private final GoogleConfig googleConfig;
    private final GoogleClient googleClient;

    private Boolean connected;

    public GoogleMailService(SettingsRepository settingsRepository,
                             GoogleConfig googleConfig,
                             GoogleClient googleClient) {
        this.settingsRepository = settingsRepository;
        this.googleConfig = googleConfig;
        this.googleClient = googleClient;
        if (googleConfig == null || "UIT".equalsIgnoreCase(googleConfig.getClientSecret())) {
            log.info("Google Mail turned off");
            this.connected = false;
        } else {
            log.debug("Setting up Mail");
            this.connected = (settingsRepository.getLatestSetting(adminUserId, GMAIL_ACCESS_TOKEN) != null);
            log.debug("Status Google Mail {}", this.connected);
        }
    }

    public ConnectivityStatusDto getMailStatus() {
        return ConnectivityStatusDto.builder()
                .connected(this.connected)
                .syncedApplicationId(googleConfig.getClientId())
                .build();
    }

    public void registerWithCode(String clientAuthorizationCode) {
        Setting setting = Setting.builder()
                .name(GMAIL_CLIENT_AUTHORIZATION_CODE)
                .value(clientAuthorizationCode)
                .day(Date.valueOf(LocalDate.now()))
                .build();
        settingsRepository.putSetting(adminUserId, setting);

        Oath2Token token = googleClient.getAuthorizationToken(clientAuthorizationCode);

        if (token != null) {

            Long expiresIn = Long.valueOf(token.getExpires_in().toString());
            final long expiresAt = getExpiresAtFromExpiresIn(expiresIn);

            saveSetting(adminUserId, GMAIL_ACCESS_TOKEN, token.getAccess_token());
            saveSetting(adminUserId, GMAIL_REFRESH_TOKEN, token.getRefresh_token());
            saveSetting(adminUserId, GMAIL_EXPIRES_AT, String.valueOf(expiresAt));
            connected = true;
            log.info("Connected to Google!");

        } else {
            log.error("Unable to get token for gmail");
        }
    }

    private long getExpiresAtFromExpiresIn(Long expiresIn) {
        Instant instant = Instant.now();
        return instant.plus(expiresIn, ChronoUnit.SECONDS).getEpochSecond();
    }

    private void saveSetting(Integer userId, String name, String value) {

        settingsRepository.putSetting(userId, Setting.builder()
                .userId(userId)
                .name(name)
                .value(value)
                .day(Date.valueOf(LocalDate.now())).build());
    }

    private Oath2Token getOath2Token(Integer userId) {
        final Setting accessToken = settingsRepository.getLatestSetting(userId, GMAIL_ACCESS_TOKEN);
        final Setting refreshToken = settingsRepository.getLatestSetting(userId, GMAIL_REFRESH_TOKEN);
        final Setting expiresAt = settingsRepository.getLatestSetting(userId, GMAIL_EXPIRES_AT);

        if (accessToken == null ||
                refreshToken == null ||
                expiresAt == null) {
            log.error("Gmail session not initialized");
            return null;
        }

        Oath2Token token = Oath2Token.builder()
                .access_token(accessToken.getValue())
                .refresh_token(refreshToken.getValue())
                .expires_at(Long.valueOf(expiresAt.getValue()))
                .build();

        if (isExpired(token)) {
            log.debug("Token is expired. Refreshing..");
            token = googleClient.refreshToken(token.getRefresh_token());
            if (token == null) {
                log.error("Unable to get new token");
                return null;
            } else if (isExpired(token)) {
                log.error("New token also expired. wtf...");
                return null;
            }
            storeTokenSettings(userId, token);
        }
        return token;
    }

    @Transactional
    private void storeTokenSettings(Integer userId, Oath2Token oath2Token) {
        log.debug("Storing token update");

        final Setting accessToken = settingsRepository.getLatestSetting(userId, GMAIL_ACCESS_TOKEN);
        final Setting refreshToken = settingsRepository.getLatestSetting(userId, GMAIL_REFRESH_TOKEN);
        final Setting expireAt = settingsRepository.getLatestSetting(userId, GMAIL_EXPIRES_AT);

        accessToken.setValue(oath2Token.getAccess_token());
        if (StringUtils.isNotEmpty(oath2Token.getRefresh_token())) {
            refreshToken.setValue(oath2Token.getRefresh_token());
        }
        if (oath2Token.getExpires_at() == null) {
            oath2Token.setExpires_at(getExpiresAtFromExpiresIn(oath2Token.getExpires_in()));
        }
        expireAt.setValue(oath2Token.getExpires_at().toString());

        settingsRepository.saveSetting(userId, accessToken);
        settingsRepository.saveSetting(userId, refreshToken);
        settingsRepository.saveSetting(userId, expireAt);
    }


    private boolean isExpired(Oath2Token token) {
        Long expiresAt = token.getExpires_at();
        if (expiresAt == null) {
            expiresAt = getExpiresAtFromExpiresIn(token.getExpires_in());
            token.setExpires_at(expiresAt);
        }
        Instant instant = Instant.ofEpochSecond(expiresAt);
        LocalDateTime timeTokenExpires = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime currentTime = LocalDateTime.now(ZoneId.systemDefault()).plusMinutes(10);
        log.debug("Token valid until [{}]", timeTokenExpires);
        return timeTokenExpires.isBefore(currentTime);
    }

    public void sendPasswordRetrievalMail(String email, String unhashedTemporaryPassword, UserAccount account) {
        if (isConnnectedToGmail()) {
            try {
                String subject = "Macrolog Credentials";
                String body = "<h3>Hello " + account.getUsername() + ", </h3>" +
                        "<p>A request has been made to reset your password. </p>" +
                        "<p>We have generated a new password for you: <i>" + unhashedTemporaryPassword + "</i>. </p>" +
                        "<p>You can use this within 30 minutes to log in and choose a new password of your own. </p>" +
                        "<p>If you did not request this password change, you can ignore this messsage. </p>" +
                        "<p>See you soon! </p>" +
                        "<p>Carmen and Arjan from Macrolog </p>";

                log.debug(MAIL_SEND_TO_DEBUGLINE + email);
                final MimeMessage email1 = googleClient.createEmail(email, MACROLOG_FROM_ADDRESS, subject, body);
                googleClient.sendMail(getOath2Token(adminUserId), email1);
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }

    public void sendConfirmationMail(String email, UserAccount account) {
        if (isConnnectedToGmail()) {
            try {
                String subject = "Welcome to Macrolog!";
                String body = "<p>Hello " + account.getUsername() + ", </p>" +
                        "<p>Thank you for using Macrolog!</p>" +
                        "<p>You are now ready to use both the app and the <a href=\"https://macrolog.herokuapp.com/\"> website</a>. " +
                        "Our aim is to make it as easy as possible to log your food intake on a daily basis. " +
                        "We hope this app ultimately helps you to achieve your goals, whatever they may be. </p>" +
                        "<p>All the best,</p>" +
                        "<p>Carmen and Arjan from Macrolog</p>";

                log.debug(MAIL_SEND_TO_DEBUGLINE + email);
                final MimeMessage email1 = googleClient.createEmail(email, MACROLOG_FROM_ADDRESS, subject, body);
                googleClient.sendMail(getOath2Token(adminUserId), email1);
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }

    private boolean isConnnectedToGmail() {
        if (!connected) {
            log.error("Uable to send mail. Google Mail is not connected.'");
            return false;
        }
        return true;
    }

    public void sendTestMail(String email) {
        if (isConnnectedToGmail()) {
            try {
                String subject = "Test mail from Macrolog!";
                String body = "<p>Hello,</p>" +
                        "<p>This is a testmail for Macrolog!</p>" +
                        "<p>And it works! Yay! </p>" +
                        "<p>All the best,</p>" +
                        "<p>Carmen and Arjan from Macrolog</p>";

                log.debug(MAIL_SEND_TO_DEBUGLINE + email);
                final MimeMessage email1 = googleClient.createEmail(email, MACROLOG_FROM_ADDRESS, subject, body);
                googleClient.sendMail(getOath2Token(adminUserId), email1);
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }

}
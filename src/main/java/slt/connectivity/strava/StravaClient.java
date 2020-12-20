package slt.connectivity.strava;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import slt.config.StravaConfig;
import slt.connectivity.strava.dto.ActivityDetailsDto;
import slt.connectivity.strava.dto.ListedActivityDto;
import slt.connectivity.strava.dto.SubscriptionInformation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * See https://developers.strava.com/docs/authentication/ for implementation used.
 */
@Slf4j
@Component
@AllArgsConstructor
@NoArgsConstructor
public class StravaClient {

    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String CALLBACK_URL = "callback_url";
    public static final String VERIFY_TOKEN = "verify_token";
    public static final String GRANT_TYPE = "grant_type";
    @Autowired
    RestTemplate restTemplate;

    @Autowired
    StravaConfig stravaConfig;

    private static final String ERROR_MESSAGE = "Fout bij versturen.";
    private static final String BEARER_MESSAGE = "Bearer %s";

    private static final String STRAVA_WEBHOOK_URL = "https://api.strava.com/api/v3/push_subscriptions";
    private static final String STRAVA_ACTIVITIES_URL = "https://www.strava.com/api/v3/activities";
    private static final String STRAVA_AUTHENTICATION_URL = "https://www.strava.com/oauth/token";


    public StravaToken getStravaToken(String authorizationCode) {
        String grantType = "authorization_code";

        String clientId = stravaConfig.getClientId().toString();
        String clientSecret = stravaConfig.getClientSecret();
        Map<String,String> reqPayload = new HashMap();
        reqPayload.put(CLIENT_ID, clientId);
        reqPayload.put(CLIENT_SECRET, clientSecret);
        reqPayload.put("code", authorizationCode);
        reqPayload.put(GRANT_TYPE, grantType);

        return getStravaToken(reqPayload);

    }

    private StravaToken getStravaToken(Map<String,String> reqPayload) {
        try {
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            final HttpEntity<HashMap> entity = new HttpEntity(reqPayload, headers);
            ResponseEntity<StravaToken> responseEntity = restTemplate.exchange(STRAVA_AUTHENTICATION_URL, HttpMethod.POST, entity, StravaToken.class);

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

    public StravaToken refreshToken(String refreshToken) {
        String grantType = "refresh_token";

        String clientId = stravaConfig.getClientId().toString();
        String clientSecret = stravaConfig.getClientSecret();

        Map<String,String> reqPayload = new HashMap();
        reqPayload.put(CLIENT_ID, clientId);
        reqPayload.put(CLIENT_SECRET, clientSecret);
        reqPayload.put("refresh_token", refreshToken);
        reqPayload.put(GRANT_TYPE, grantType);

        return getStravaToken(reqPayload);
    }

    private long getUTCEpoch(LocalDateTime localDateTime) {
        ZonedDateTime ldtZoned = localDateTime.atZone(ZoneId.systemDefault());
        ZonedDateTime utcZoned = ldtZoned.withZoneSameInstant(ZoneId.of("UTC"));
        return utcZoned.toEpochSecond();
    }

    public List<ListedActivityDto> getActivitiesForDay(String token, LocalDate date) {
        try {

            final LocalDateTime localDateTimeStartOfDay = date.atStartOfDay();
            final LocalDateTime localDateTimeEndOfDay = date.atStartOfDay().plusDays(1);

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(STRAVA_ACTIVITIES_URL)
                    .queryParam("before", getUTCEpoch(localDateTimeEndOfDay))
                    .queryParam("after", getUTCEpoch(localDateTimeStartOfDay));

            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(HttpHeaders.AUTHORIZATION, String.format(BEARER_MESSAGE, token));

            final HttpEntity entity = new HttpEntity<>(headers);
            ParameterizedTypeReference<List<ListedActivityDto>> parameterizedTypeReference = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<List<ListedActivityDto>> responseEntity = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity, parameterizedTypeReference);

            return responseEntity.getBody();
        } catch (HttpClientErrorException httpClientErrorException) {
            log.error(httpClientErrorException.getResponseBodyAsString());
            log.error(ERROR_MESSAGE + " {}", httpClientErrorException.getLocalizedMessage(), httpClientErrorException);
            return new ArrayList<>();
        } catch (RestClientException restClientException) {

            log.error(ERROR_MESSAGE + " {}", restClientException.getLocalizedMessage(), restClientException);
            return new ArrayList<>();
        }
    }

    public ActivityDetailsDto getActivityDetail(String token, Long activityDetailId) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(STRAVA_ACTIVITIES_URL + "/" + activityDetailId);

            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(HttpHeaders.AUTHORIZATION, String.format(BEARER_MESSAGE, token));

            final HttpEntity<ActivityDetailsDto> entity = new HttpEntity<>(headers);
            ResponseEntity<ActivityDetailsDto> responseEntity = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity, ActivityDetailsDto.class);

            ActivityDetailsDto gevondenActivity = responseEntity.getBody();

            log.debug(gevondenActivity.getStart_date() + " - " + gevondenActivity.getCalories() + " - " + gevondenActivity.getName() + " " + gevondenActivity.getType() + " " + gevondenActivity.getId());
            return gevondenActivity;

        } catch (HttpClientErrorException httpClientErrorException) {
            log.error(httpClientErrorException.getResponseBodyAsString());
            log.error(ERROR_MESSAGE + " {}", httpClientErrorException.getLocalizedMessage(), httpClientErrorException);
            return null;
        } catch (RestClientException restClientException) {
            log.error(ERROR_MESSAGE + " {}", restClientException.getLocalizedMessage(), restClientException);
            return null;
        }
    }

    public boolean unregister(StravaToken token) {
        try {
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(HttpHeaders.AUTHORIZATION, String.format(BEARER_MESSAGE, token.getAccess_token()));

            final HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> responseEntity = restTemplate.exchange("https://www.strava.com/oauth/deauthorize", HttpMethod.POST, entity, String.class);

            String gevondenToken = responseEntity.getBody();
            return gevondenToken != null;

        } catch (HttpClientErrorException httpClientErrorException) {
            log.error(httpClientErrorException.getResponseBodyAsString());
            log.error(ERROR_MESSAGE + " {}", httpClientErrorException.getLocalizedMessage(), httpClientErrorException);
            return false;
        } catch (RestClientException restClientException) {
            log.error(ERROR_MESSAGE + " {}", restClientException.getLocalizedMessage(), restClientException);
            return false;
        }
    }

    public SubscriptionInformation startWebhookSubscription(Integer clientId, String clientSecret, String callbackUrl, String subscribeVerifyToken) {

        MultiValueMap<String, String> reqPayload= new LinkedMultiValueMap<>();

        reqPayload.add(CLIENT_ID, clientId.toString());
        reqPayload.add(CLIENT_SECRET, clientSecret);
        reqPayload.add(CALLBACK_URL, callbackUrl);
        reqPayload.add(VERIFY_TOKEN, subscribeVerifyToken);

        try {
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            final HttpEntity<HashMap> entity = new HttpEntity(reqPayload, headers);
            ResponseEntity<SubscriptionInformation> responseEntity = restTemplate.exchange(STRAVA_WEBHOOK_URL, HttpMethod.POST, entity, SubscriptionInformation.class);

            SubscriptionInformation subscription = responseEntity.getBody();
            log.debug("Aangemaakte subscription {}", subscription.getId());
            return subscription;

        } catch (HttpClientErrorException httpClientErrorException) {
            log.error(httpClientErrorException.getResponseBodyAsString());
            log.error(ERROR_MESSAGE + " {}", httpClientErrorException.getLocalizedMessage(), httpClientErrorException);
            return null;
        } catch (RestClientException restClientException) {
            log.error(ERROR_MESSAGE + " {}", restClientException.getLocalizedMessage(), restClientException);
            return null;
        }
    }

    public SubscriptionInformation viewWebhookSubscription(Integer clientId, String clientSecret) {
        try {

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(STRAVA_WEBHOOK_URL)
                    .queryParam(CLIENT_ID, clientId)
                    .queryParam(CLIENT_SECRET, clientSecret);

            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            final HttpEntity entity = new HttpEntity<>(headers);
            ParameterizedTypeReference<List<SubscriptionInformation>> parameterizedTypeReference = new ParameterizedTypeReference<>() {
            };

            ResponseEntity<List<SubscriptionInformation>> responseEntity = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity, parameterizedTypeReference);
            final List<SubscriptionInformation> body = responseEntity.getBody();
            if (!body.isEmpty()){
                return body.get(0);
            }
            return null;
        } catch (HttpClientErrorException httpClientErrorException) {
            log.error(httpClientErrorException.getResponseBodyAsString());
            log.error(ERROR_MESSAGE + " {}", httpClientErrorException.getLocalizedMessage(), httpClientErrorException);
            return null;
        } catch (RestClientException restClientException) {

            log.error(ERROR_MESSAGE + " {}", restClientException.getLocalizedMessage(), restClientException);
            return null;
        }
    }

    public boolean deleteWebhookSubscription(Integer clientId, String clientSecret, Integer subscriptionId) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(STRAVA_WEBHOOK_URL + "/" + subscriptionId);

            MultiValueMap<String, Object> reqPayload= new LinkedMultiValueMap<>();
            reqPayload.add(CLIENT_ID, clientId);
            reqPayload.add(CLIENT_SECRET, clientSecret);

            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            final HttpEntity<String> entity = new HttpEntity(reqPayload,headers);
            ResponseEntity<String> responseEntity = restTemplate.exchange(builder.toUriString(), HttpMethod.DELETE, entity, String.class);
            log.debug("Received response for delete subscription : {}", responseEntity.getStatusCodeValue());
            return HttpStatus.NO_CONTENT.equals(responseEntity.getStatusCode());
        } catch (HttpClientErrorException httpClientErrorException) {
            log.error(httpClientErrorException.getResponseBodyAsString());
            log.error(ERROR_MESSAGE + " {}", httpClientErrorException.getLocalizedMessage(), httpClientErrorException);
            return false;
        } catch (RestClientException restClientException) {

            log.error(ERROR_MESSAGE + " {}", restClientException.getLocalizedMessage(), restClientException);
            return false;
        }
    }

}

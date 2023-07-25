/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service;

import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;

import io.harness.cistatus.GithubAppTokenCreationResponse;
import io.harness.cistatus.StatusCreationResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException.ReportTarget;
import io.harness.gitpolling.github.GitHubPollingWebhookEventDelivery;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.gitpolling.github.GitPollingWebhookEventMetadata;
import io.harness.network.Http;
import io.harness.remote.client.NGRestUtils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class GithubServiceImpl implements GithubService {
  private static final int EXP_TIME = 5 * 60 * 1000;
  private static final String MERGED = "merged";
  private static final String MESSAGE = "message";

  private static final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 10;

  @Override
  public String getToken(GithubAppConfig githubAppConfig) {
    log.info("Retrieving github installation token for installation id {}", githubAppConfig.getInstallationId());
    try {
      String jwtToken = generateTokenFromPrivateKey(githubAppConfig);
      String authToken = getAuthToken(jwtToken);
      Call<GithubAppTokenCreationResponse> responseCall;

      if (githubAppConfig.getGithubUrl().contains("github.com")) {
        responseCall =
            getGithubClient(githubAppConfig).createAccessToken(authToken, githubAppConfig.getInstallationId());
      } else {
        responseCall = getGithubClient(githubAppConfig)
                           .createAccessTokenForGithubEnterprise(authToken, githubAppConfig.getInstallationId());
      }

      GithubAppTokenCreationResponse response = NGRestUtils.getGeneralResponse(responseCall);
      return response.getToken();
    } catch (Exception ex) {
      throw new InvalidRequestException(
          format("Failed to generate token for url %s, installation id %s, cause: %s", githubAppConfig.getGithubUrl(),
              githubAppConfig.getInstallationId(), ex.getMessage()),
          ex);
    }
  }

  @Override
  public boolean sendStatus(GithubAppConfig githubAppConfig, String token, String sha, String owner, String repo,
      Map<String, Object> bodyObjectMap) {
    log.info("Sending status for sha {}", sha);

    try {
      Response<StatusCreationResponse> githubStatusCreationResponseResponse =
          getGithubClient(githubAppConfig).createStatus(getAuthToken(token), owner, repo, sha, bodyObjectMap).execute();
      if (!githubStatusCreationResponseResponse.isSuccessful()) {
        log.error("Please check if your github credentials has proper permissions");
        log.error("Failed to send status for github url {} and sha {} error {}, message {}",
            githubAppConfig.getGithubUrl(), sha, githubStatusCreationResponseResponse.errorBody().string(),
            githubStatusCreationResponseResponse.message());
      }
      return githubStatusCreationResponseResponse.isSuccessful();

    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Failed to send status for Github url %s and sha %s ", githubAppConfig.getGithubUrl(), sha), e);
    }
  }

  @Override
  public String findPR(String apiUrl, String token, String owner, String repo, String prNumber) {
    try {
      Response<Object> response = getGithubClient(GithubAppConfig.builder().githubUrl(apiUrl).build())
                                      .findPR(getAuthToken(token), owner, repo, prNumber)
                                      .execute();
      if (response.isSuccessful()) {
        return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(response.body());
      } else {
        log.error("Failed to find pr error {}, message {}", response.errorBody().string(), response.message());
        return null;
      }

    } catch (Exception e) {
      log.error("Failed to fetch PR details for github url {} and prNum {} ", apiUrl, prNumber, e);
      return "";
    }
  }

  @Override
  public JSONObject mergePR(String apiUrl, String token, String owner, String repo, String prNumber) {
    try {
      Response<Object> response = null;

      int i = MAX_ATTEMPTS;
      while (i > 0) {
        response = getGithubClient(GithubAppConfig.builder().githubUrl(apiUrl).build())
                       .mergePR(getAuthToken(token), owner, repo, prNumber)
                       .execute();
        i--;
        // This error code denotes that the base branch has been modified. This can happen if two merge requests
        // are sent for the same branch but the first one has not yet complete and second request reached github.
        // https://github.com/orgs/community/discussions/24462
        if (response.code() != 405) {
          break;
        }
        log.info(format(
            "Received code %s, retrying attempt %s after sleeping for %s", response.code(), i, RETRY_SLEEP_DURATION));
        sleep(RETRY_SLEEP_DURATION);
      }

      JSONObject json = new JSONObject();
      if (response.isSuccessful()) {
        json.put("sha", ((LinkedHashMap) response.body()).get("sha"));
        json.put(MERGED, ((LinkedHashMap) response.body()).get(MERGED));
        json.put(MESSAGE, ((LinkedHashMap) response.body()).get(MESSAGE));
      } else {
        JSONObject errObject = new JSONObject(response.errorBody().string());
        log.error("Failed to merge PR {}. error {}, code {}", prNumber, errObject.get(MESSAGE), response.code());
        json.put("error", errObject.get(MESSAGE));
        json.put("code", response.code());
        json.put(MERGED, false);
      }
      return json;
    } catch (Exception e) {
      log.error("Failed to merge PR for github url {} and prNum {} ", apiUrl, prNumber, e);
      JSONObject json = new JSONObject();
      json.put("error", e.getMessage());
      json.put(MERGED, false);
      return json;
    }
  }

  @Override
  public boolean deleteRef(String apiUrl, String token, String owner, String repo, String ref) {
    try {
      Response<Object> response = getGithubClient(GithubAppConfig.builder().githubUrl(apiUrl).build())
                                      .deleteRef(getAuthToken(token), owner, repo, ref)
                                      .execute();

      if (response.isSuccessful()) {
        return true;
      }
    } catch (Exception e) {
      log.error("Failed to delete ref for github url {} and ref {} ", apiUrl, ref, e);
    }
    return false;
  }

  public List<GitPollingWebhookData> getWebhookRecentDeliveryEvents(
      String apiUrl, String token, String repoOwner, String repoName, String webhookId) {
    try {
      Response<List<GitPollingWebhookEventMetadata>> response =
          getGithubClient(GithubAppConfig.builder().githubUrl(apiUrl).build())
              .getWebhookRecentDeliveryEventsIds(getBasicAuthHeader(repoOwner, token), repoOwner, repoName, webhookId)
              .execute();

      Predicate<GitPollingWebhookEventMetadata> filterHttpStatuses = filterStatusPredicates(delivery
          -> delivery.getStatusCode() != HttpStatus.SC_OK,
          delivery -> delivery.getStatusCode() != HttpStatus.SC_ACCEPTED);

      if (response.isSuccessful()) {
        List<GitPollingWebhookEventMetadata> filteredEvents =
            response.body().stream().filter(filterHttpStatuses).collect(Collectors.toList());
        log.info("Received {} webhook metadata filtered events successfully from github. "
                + "Url {}, repo {} and  webhookId {}",
            filteredEvents.size(), apiUrl, repoName, webhookId);
        return getWebhookDeliveryFullEvents(filteredEvents, apiUrl, token, repoOwner, repoName, webhookId);
      }

      /*
       This is a temporary code block for debugging Github connectivity issue for one of our customers.
       TODO: Remove this block
      */
      String headers = null;
      String handshake = null;
      String request = null;
      if (response.raw() != null) {
        headers = response.raw().headers().toString();
        handshake = response.raw().handshake() != null ? response.raw().handshake().toString() : null;
        request = response.raw().request().toString();
      }

      log.error(
          "Failed to fetch webhook metadata events for github url {}, repo {}, webhookId {}, response {}, headers {}, handshake {}, request {}",
          apiUrl, repoName, webhookId, response, headers, handshake, request);

      List<GitPollingWebhookEventMetadata> unfilteredEvents =
          getWebhookRecentDeliveryEventsViaCurl(apiUrl, token, repoOwner, webhookId, repoName);
      if (!unfilteredEvents.isEmpty()) {
        List<GitPollingWebhookEventMetadata> filteredEvents =
            unfilteredEvents.stream().filter(filterHttpStatuses).collect(Collectors.toList());
        log.info("Received {} webhook metadata filtered events successfully from github via curl. "
                + "Url {}, repo {} and  webhookId {}",
            filteredEvents.size(), apiUrl, repoName, webhookId);
        return getWebhookDeliveryFullEvents(filteredEvents, apiUrl, token, repoOwner, repoName, webhookId);
      }
    } catch (Exception e) {
      log.error("Exception while fetching webhook metadata events from github. "
              + "Url {}, webhookId {} and repo {} ",
          apiUrl, webhookId, repoName, e);
    }
    return Collections.emptyList();
  }

  private List<GitPollingWebhookEventMetadata> getWebhookRecentDeliveryEventsViaCurl(
      String url, String token, String repoOwner, String webhookId, String repoName) {
    String apiUrl = url + "repos/" + repoOwner + "/" + repoName + "/hooks/" + webhookId + "/deliveries";
    log.info("Trying to fetch to recent webhook delivery events for url {}", apiUrl);
    try {
      String result = executeCurl(apiUrl, repoOwner, token);
      log.info("Response received for the curl {}", result);
      GitPollingWebhookEventMetadata[] gitPollingWebhookEventMetadata =
          new ObjectMapper().readValue(result, GitPollingWebhookEventMetadata[].class);
      return List.of(gitPollingWebhookEventMetadata);
    } catch (Exception e) {
      log.error("Exception while executing the curl or processing the response for url {}", apiUrl, e);
    }
    return Collections.emptyList();
  }

  public Predicate<GitPollingWebhookEventMetadata> filterStatusPredicates(
      Predicate<GitPollingWebhookEventMetadata>... predicates) {
    return Arrays.stream(predicates).reduce(t -> true, Predicate::and);
  }

  private List<GitPollingWebhookData> getWebhookDeliveryFullEvents(
      List<GitPollingWebhookEventMetadata> webhookEventsMetadata, String apiUrl, String token, String repoOwner,
      String repoName, String webhookId) {
    List<GitPollingWebhookData> results = new ArrayList<>();
    webhookEventsMetadata.stream().forEach(webhookEvent -> {
      try {
        Response<GitHubPollingWebhookEventDelivery> fullWebhookResponse =
            getGithubClient(GithubAppConfig.builder().githubUrl(apiUrl).build())
                .getWebhookDeliveryId(
                    getBasicAuthHeader(repoOwner, token), repoOwner, repoName, webhookId, webhookEvent.getId())
                .execute();
        ObjectMapper mapper = new ObjectMapper();
        if (fullWebhookResponse.isSuccessful()) {
          String payload = mapper.writeValueAsString(fullWebhookResponse.body().getRequest().getPayload());
          JsonNode headers = mapper.valueToTree(fullWebhookResponse.body().getRequest().getHeaders());

          results.add(GitPollingWebhookData.builder()
                          .payload(payload)
                          .deliveryId(fullWebhookResponse.body().getId())
                          .headers(createHeaders(headers))
                          .build());
        } else {
          log.error("Failed to fetch full webhook event github response. "
                  + "Url {}, repo {}, hookId {}, deliveryId {}, error {} ",
              apiUrl, repoName, webhookId, webhookEvent.getId(), fullWebhookResponse.errorBody());
          getWebhookDeliveryFullEventsViaCurl(
              apiUrl, token, repoOwner, webhookId, repoName, webhookEvent.getId(), results);
        }
      } catch (Exception e) {
        log.error("Exception while fetching full webhook event from github. "
                + "Url {}, hookId {}, deliveryId {} and repo {} ",
            apiUrl, webhookId, webhookEvent.getId(), repoName, e);
      }
    });

    log.info("Total number of full webhook events fetched {}. "
            + "Url {}, repo {}, hookId {} ",
        results.size(), apiUrl, repoName, webhookId);
    return results;
  }

  private void getWebhookDeliveryFullEventsViaCurl(String url, String token, String repoOwner, String webhookId,
      String repoName, String webhookEventId, List<GitPollingWebhookData> results) {
    String apiUrl =
        url + "repos/" + repoOwner + "/" + repoName + "/hooks/" + webhookId + "/deliveries/" + webhookEventId;
    log.info("Trying to fetch to full webhook delivery event for url {} and webhookEventId {}", apiUrl, webhookEventId);
    try {
      String result = executeCurl(apiUrl, repoOwner, token);
      ObjectMapper mapper = new ObjectMapper();
      GitHubPollingWebhookEventDelivery gitHubPollingWebhookEventDelivery =
          mapper.readValue(result, GitHubPollingWebhookEventDelivery.class);
      String payload = mapper.writeValueAsString(gitHubPollingWebhookEventDelivery.getRequest().getPayload());
      JsonNode headers = mapper.valueToTree(gitHubPollingWebhookEventDelivery.getRequest().getHeaders());
      results.add(GitPollingWebhookData.builder()
                      .payload(payload)
                      .deliveryId(gitHubPollingWebhookEventDelivery.getId())
                      .headers(createHeaders(headers))
                      .build());
    } catch (Exception e) {
      log.error("Exception while executing the curl or processing the response for url {} and webhookEventId {}",
          apiUrl, webhookEventId, e);
    }
  }

  private String executeCurl(String apiUrl, String repoOwner, String token) throws IOException {
    String[] command = {"curl", "-u", repoOwner + ":" + token, apiUrl, "--insecure"};
    Process process = Runtime.getRuntime().exec(command);
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    StringBuilder builder = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      builder.append(line);
      builder.append(System.getProperty("line.separator"));
    }
    String result = builder.toString();
    log.info("Response received for the curl {}", result);
    return result;
  }

  private MultivaluedMap<String, String> createHeaders(JsonNode headers) {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, String> headersMap = mapper.convertValue(headers, new TypeReference<Map<String, String>>() {});

    return new MultivaluedHashMap<>(headersMap);
  }

  @VisibleForTesting
  public GithubRestClient getGithubClient(GithubAppConfig githubAppConfig) {
    try {
      String githubUrl = githubAppConfig.getGithubUrl();
      if (githubUrl == null) {
        throw new InvalidRequestException(format("Invalid Github Url Server URL %s ", githubAppConfig.getGithubUrl()));
      }
      if (!githubUrl.endsWith("/")) {
        githubUrl = githubUrl + "/";
      }
      Retrofit retrofit = new Retrofit.Builder()
                              .baseUrl(githubUrl)
                              .addConverterFactory(JacksonConverterFactory.create())
                              .client(Http.getUnsafeOkHttpClient(githubUrl))
                              .build();
      return retrofit.create(GithubRestClient.class);
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException("Could not reach Github Server at :" + githubAppConfig.getGithubUrl(), e);
    }
  }

  private <T> T executeRestCall(Call<T> restRequest) throws Exception {
    Response<T> restResponse = restRequest.execute();
    if (!restResponse.isSuccessful()) {
      throw new InvalidRequestException(restResponse.errorBody().string(), EnumSet.of(ReportTarget.UNIVERSAL));
    }
    return restResponse.body();
  }

  private static RSAPrivateKey getPrivateKeyFromString(String key) throws GeneralSecurityException {
    String privateKeyPEM = key;
    privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----", "");
    privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");
    // These are line breaks which are supported by different os
    privateKeyPEM = privateKeyPEM.replaceAll("\\r\\n|\\r|\\n", "");
    byte[] encoded = Base64.decodeBase64(privateKeyPEM);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
    return (RSAPrivateKey) kf.generatePrivate(keySpec);
  }

  private String generateTokenFromPrivateKey(GithubAppConfig githubAppConfig) throws Exception {
    try {
      Algorithm algorithm = Algorithm.RSA256(getPrivateKeyFromString(githubAppConfig.getPrivateKey()));
      return JWT.create()
          .withIssuer(githubAppConfig.getAppId())
          .withIssuedAt(new Date())
          .withExpiresAt(new Date(System.currentTimeMillis() + EXP_TIME))
          .sign(algorithm);
    } catch (Exception ex) {
      throw new InvalidRequestException("Invalid Github App key, Validate key is copied properly by trimming end line");
    }
  }

  private String getAuthToken(String authToken) {
    return format("Bearer %s", authToken);
  }

  private String getBasicAuthHeader(String username, String password) {
    return Credentials.basic(username, password);
  }
}

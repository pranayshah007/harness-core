/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service;

import static java.lang.String.format;

import io.harness.cistatus.GithubAppTokenCreationResponse;
import io.harness.cistatus.StatusCreationResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException.ReportTarget;
import io.harness.gitpolling.github.GitHubPollingWebhookEventDelivery;
import io.harness.gitpolling.github.GitPollingWebhookData;
import io.harness.gitpolling.github.GitPollingWebhookEventMetadata;
import io.harness.network.Http;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
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

      GithubAppTokenCreationResponse response = executeRestCall(responseCall);
      return response.getToken();
    } catch (Exception ex) {
      throw new InvalidRequestException(format("Failed to generate token for url %s, installation id %s",
                                            githubAppConfig.getGithubUrl(), githubAppConfig.getInstallationId()),
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
        log.error("Failed to send status for github url {} and sha {} error {}, message {}",
            githubAppConfig.getGithubUrl(), sha, githubStatusCreationResponseResponse.errorBody().string(),
            githubStatusCreationResponseResponse.message());
      }
      return githubStatusCreationResponseResponse.isSuccessful();

    } catch (Exception e) {
      log.error("Failed to send status for github url {} and sha {} ", githubAppConfig.getGithubUrl(), sha, e);
      return false;
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
      Response<Object> response = getGithubClient(GithubAppConfig.builder().githubUrl(apiUrl).build())
                                      .mergePR(getAuthToken(token), owner, repo, prNumber)
                                      .execute();
      if (response.isSuccessful()) {
        JSONObject json = new JSONObject();
        json.put("sha", ((LinkedHashMap) response.body()).get("sha"));
        json.put("merged", ((LinkedHashMap) response.body()).get("merged"));
        json.put("message", ((LinkedHashMap) response.body()).get("message"));
        return json;
      } else {
        log.error("Failed to merge pr error {}, message {}", response.errorBody().string(), response.message());
        log.warn("Merge Request for merging PR returned with response code {}", prNumber, response.code());
        return new JSONObject();
      }
    } catch (Exception e) {
      log.error("Failed to merge PR for github url {} and prNum {} ", apiUrl, prNumber, e);
      return new JSONObject();
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
              .getWebhookRecentDeliveryEventsIds(getAuthToken(token), repoOwner, repoName, webhookId)
              .execute();

      Predicate<GitPollingWebhookEventMetadata> filterHttpStatuses = filterStatusPredicates(delivery
          -> delivery.getStatusCode() != HttpStatus.SC_OK,
          delivery -> delivery.getStatusCode() != HttpStatus.SC_ACCEPTED);

      if (response.isSuccessful()) {
        List<GitPollingWebhookEventMetadata> filteredEvents =
            response.body().stream().filter(filterHttpStatuses).collect(Collectors.toList());

        return getWebhookDeliveryFullEvents(filteredEvents, apiUrl, token, repoOwner, repoName, webhookId);
      }

      log.error("Failed to fetch repo events for github url {}, repo {}, webhookId {}, error {} ", apiUrl, repoName,
          webhookId, response.errorBody());

    } catch (Exception e) {
      log.error(
          "Failed to fetch repo events for github url {}, webhookId {} and repo {} ", apiUrl, repoName, webhookId, e);
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
                .getWebhookDeliveryId(getAuthToken(token), repoOwner, repoName, webhookId, webhookEvent.getId())
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
          log.error("Failed to fetch full webhook response github response. Url {}, repo {}, hookId {} and error {} ",
              apiUrl, repoName, webhookId, fullWebhookResponse.errorBody());
        }

      } catch (Exception e) {
        log.error("Failed to fetch full webhook events for github url {}, hookId {} and repo {} ", apiUrl, webhookId,
            repoName, e);
      }
    });

    return results;
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
    privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----\n", "");
    privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");
    privateKeyPEM = privateKeyPEM.replaceAll("\n", "");
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
}

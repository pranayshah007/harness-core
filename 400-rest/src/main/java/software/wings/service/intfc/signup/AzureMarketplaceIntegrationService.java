/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.signup;

import static io.harness.annotations.dev.HarnessModule._940_MARKETPLACE_INTEGRATIONS;
import static io.harness.annotations.dev.HarnessTeam.GTM;

import static software.wings.sm.states.ApprovalState.JSON;

import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.SignupException;

import software.wings.app.MainConfiguration;
import software.wings.beans.MarketPlace;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.marketplace.MarketPlaceType;
import software.wings.dl.WingsPersistence;
import software.wings.security.authentication.AuthenticationUtils;
import software.wings.security.authentication.MarketPlaceConfig;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpStatus;
import org.json.JSONObject;

@OwnedBy(GTM)
@TargetModule(_940_MARKETPLACE_INTEGRATIONS)
@Slf4j
public class AzureMarketplaceIntegrationService {
  private OkHttpClient client;
  private WingsPersistence wingsPersistence;
  private String clientId;
  private String secretKey;

  private static final String PLAN_ID_KEY = "planId";
  private static final String SUBSCIPTION_ID_KEY = "id";
  private static final String QUANTITY_KEY = "quantity";
  private static final String GRANT_TYPE = "grant_type";
  private static final String CLIENT_ID_KEY = "client_id";
  private static final String CLIENT_SECRET_KEY = "client_secret";
  private static final String CLIENT_CREDENTIALS = "client_credentials";
  private static final String RESOURCE_KEY = "Resource";
  private static final String DEFAULT_RESOURCE = "62d94f6c-d599-489b-a797-3e10e42fbe22";
  private static final String QUANTITY_VALUE = "";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String X_MS_MARKETPLACE_TOKEN_KEY = "x-ms-marketplace-token";
  private static final String AZURE_MARKETPLACE_RESOLVE_API_URL =
      "https://marketplaceapi.microsoft.com/api/saas/subscriptions/resolve?api-version=2018-08-31";
  private static final String OFFER_ID_KEY = "offerId";
  private static final String HARNESS_OFFER_ID = "harness_pro";

  @Inject
  public AzureMarketplaceIntegrationService(
      AuthenticationUtils authenticationUtils, WingsPersistence wingsPersistence, MainConfiguration mainConfiguration) {
    this.wingsPersistence = wingsPersistence;
    client = new OkHttpClient();

    MarketPlaceConfig marketPlaceConfig = mainConfiguration.getMarketPlaceConfig();
    clientId = new String(
        Base64.decodeBase64(marketPlaceConfig.getAzureMarketplaceAccessKey().getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8);
    secretKey = new String(
        Base64.decodeBase64(marketPlaceConfig.getAzureMarketplaceSecretKey().getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8);
    // todo remove it once verified in dev
    log.info("Azure client id is {} and secret id is {}", clientId, secretKey);
  }

  private static final String AUTH_URL =
      "https://login.microsoftonline.com/b229b2bb-5f33-4d22-bce0-730f6474e906/oauth2/token";

  public boolean validate(String azureToken) {
    String authenticationToken = getAuthenticationToken();
    validateAzureToken(authenticationToken, azureToken);
    return true;
  }

  private void validateAzureToken(String authenticationToken, String azureToken) {
    JSONObject jsonObject = getResolveTokenResponse(authenticationToken, azureToken);
    if (!jsonObject.getString(OFFER_ID_KEY).equals(HARNESS_OFFER_ID)) {
      log.error("The offerId received in the Azure marketplace is not expected {}", jsonObject.toString());
      throw new io.harness.exception.SignupException("Failed to validate the azure marketplace token");
    }
  }

  private JSONObject getResolveTokenResponse(String authenticationToken, String azureToken) {
    try {
      RequestBody reqbody = RequestBody.create(null, new byte[0]);
      Request request = new Builder()
                            .url(AZURE_MARKETPLACE_RESOLVE_API_URL)
                            .post(reqbody)
                            .addHeader(AUTHORIZATION, "Bearer " + authenticationToken)
                            .addHeader(X_MS_MARKETPLACE_TOKEN_KEY, azureToken)
                            .build();

      okhttp3.Response response = client.newCall(request).execute();
      return new JSONObject(response.body().string());

    } catch (IOException e) {
      log.error("Failed to process Azure marketplace signup", e);
      throw new io.harness.exception.SignupException("Failed to process azure signup token");
    }
  }

  private String getAuthenticationToken() {
    RequestBody authenticationPayload = getAuthenticationPayload();

    Request request = new Builder()
                          .url(AUTH_URL)
                          .post(authenticationPayload)
                          .addHeader(CONTENT_TYPE, APPLICATION_FORM_URLENCODED)
                          .build();
    try (okhttp3.Response response = client.newCall(request).execute()) {
      JSONObject jsonObject = new JSONObject(response.body().string());
      return getAuthToken(jsonObject);
    } catch (Exception ex) {
      log.error("Getting azure authentication token failed", ex);
      throw new io.harness.exception.SignupException("Failed to signup for azure marketplace");
    }
  }

  private void activateSubscription(String authToken, RequestBody requestBody, String subscriptionId) {
    String subscriptionApi =
        "https://marketplaceapi.microsoft.com/api/saas/subscriptions/%s/activate?api-version=2018-08-31";
    String activateApi = String.format(subscriptionApi, subscriptionId);
    Request request = new Builder()
                          .url(activateApi)
                          .post(requestBody)
                          .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                          .addHeader(AUTHORIZATION, "Bearer " + authToken)
                          .build();
    try (okhttp3.Response response = client.newCall(request).execute()) {
      if (response.code() != HttpStatus.SC_OK) {
        log.info("Failed to activate azure subscription: {}", response.toString());
        throw new io.harness.exception.SignupException("Azure activate API failed");
      }
      log.info("Azure subscription successful for subscriptionId: {}", subscriptionId);
    } catch (IOException ex) {
      log.error("Getting azure authentication token failed", ex);
      throw new SignupException("Failed to signup for azure marketplace");
    }
  }

  private String getAuthToken(JSONObject jsonObject) {
    return jsonObject.getString(ACCESS_TOKEN);
  }

  private RequestBody getAuthenticationPayload() {
    return new MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(GRANT_TYPE, CLIENT_CREDENTIALS)
        .addFormDataPart(CLIENT_ID_KEY, clientId)
        .addFormDataPart(CLIENT_SECRET_KEY, secretKey)
        .addFormDataPart(RESOURCE_KEY, DEFAULT_RESOURCE)
        .build();
  }

  private RequestBody getActivateSubscriptionPayload(String planId) {
    JSONObject jsonPayload = new JSONObject();

    jsonPayload.put(PLAN_ID_KEY, planId);
    jsonPayload.put(QUANTITY_KEY, QUANTITY_VALUE);

    String payload = jsonPayload.toString();
    return RequestBody.create(JSON, payload);
  }

  public void activateSubscription(UserInvite userInvite, User user) {
    String authenticationToken = getAuthenticationToken();
    activateSubscription(authenticationToken, userInvite.getMarketPlaceToken(), user);
  }

  private void activateSubscription(String authenticationToken, String marketPlaceToken, User user) {
    JSONObject resolveTokenResponse = getResolveTokenResponse(authenticationToken, marketPlaceToken);
    String subscriptionId = resolveTokenResponse.getString(SUBSCIPTION_ID_KEY);
    String planId = resolveTokenResponse.getString(PLAN_ID_KEY);

    MarketPlace marketPlace = MarketPlace.builder()
                                  .type(MarketPlaceType.AZURE)
                                  .token(subscriptionId)
                                  .accountId(user.getDefaultAccountId())
                                  .customerIdentificationCode(user.getDefaultAccountId())
                                  .build();
    wingsPersistence.save(marketPlace);

    RequestBody reqBody = getActivateSubscriptionPayload(planId);
    activateSubscription(authenticationToken, reqBody, subscriptionId);
  }
}

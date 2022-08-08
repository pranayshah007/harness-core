/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.testing.TestUtils;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Added because original google lib {@link
 * com.google.api.client.googleapis.testing.auth.oauth2.MockTokenServerTransport} had a bug with expiration time
 */
public class MockTransport extends MockHttpTransport {
  static final String EXPECTED_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
  static final JsonFactory JSON_FACTORY = new JacksonFactory();
  final String tokenServerUrl;
  Map<String, String> serviceAccounts = new HashMap<>();
  Map<String, String> clients = new HashMap<>();
  Map<String, String> refreshTokens = new HashMap<>();

  public MockTransport() {
    this(GoogleOAuthConstants.TOKEN_SERVER_URL);
  }

  public MockTransport(String tokenServerUrl) {
    this.tokenServerUrl = tokenServerUrl;
  }

  public void addServiceAccount(String email, String accessToken) {
    serviceAccounts.put(email, accessToken);
  }

  public void addClient(String clientId, String clientSecret) {
    clients.put(clientId, clientSecret);
  }

  public void addRefreshToken(String refreshToken, String accessTokenToReturn) {
    refreshTokens.put(refreshToken, accessTokenToReturn);
  }

  @Override
  public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
    if (url.equals(tokenServerUrl)) {
      MockLowLevelHttpRequest request = new MockLowLevelHttpRequest(url) {
        @Override
        public LowLevelHttpResponse execute() throws IOException {
          String content = this.getContentAsString();
          Map<String, String> query = TestUtils.parseQuery(content);
          String accessToken = null;

          String foundId = query.get("client_id");
          if (foundId != null) {
            if (!clients.containsKey(foundId)) {
              throw new IOException("Client ID not found.");
            }
            String foundSecret = query.get("client_secret");
            String expectedSecret = clients.get(foundId);
            if (foundSecret == null || !foundSecret.equals(expectedSecret)) {
              throw new IOException("Client secret not found.");
            }
            String foundRefresh = query.get("refresh_token");
            if (!refreshTokens.containsKey(foundRefresh)) {
              throw new IOException("Refresh Token not found.");
            }
            accessToken = refreshTokens.get(foundRefresh);
          } else if (query.containsKey("grant_type")) {
            String grantType = query.get("grant_type");
            if (!EXPECTED_GRANT_TYPE.equals(grantType)) {
              throw new IOException("Unexpected Grant Type.");
            }
            String assertion = query.get("assertion");
            JsonWebSignature signature = JsonWebSignature.parse(JSON_FACTORY, assertion);
            String foundEmail = signature.getPayload().getIssuer();
            if (!serviceAccounts.containsKey(foundEmail)) {
              throw new IOException("Service Account Email not found as issuer.");
            }
            accessToken = serviceAccounts.get(foundEmail);
            String foundScopes = (String) signature.getPayload().get("scope");
            if (foundScopes == null || foundScopes.length() == 0) {
              throw new IOException("Scopes not found.");
            }
          } else {
            throw new IOException("Unknown token type.");
          }

          // Create the JSon response
          GenericJson refreshContents = new GenericJson();
          refreshContents.setFactory(JSON_FACTORY);
          refreshContents.put("access_token", accessToken);
          refreshContents.put("expires_in", 3600);
          refreshContents.put("token_type", "Bearer");
          String refreshText = refreshContents.toPrettyString();

          MockLowLevelHttpResponse response =
              new MockLowLevelHttpResponse().setContentType(Json.MEDIA_TYPE).setContent(refreshText);
          return response;
        }
      };
      return request;
    }
    return super.buildRequest(method, url);
  }
}

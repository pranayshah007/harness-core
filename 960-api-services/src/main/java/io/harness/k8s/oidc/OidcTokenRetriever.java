/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.oidc;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.utils.ExceptionUtils;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.OidcGrantType;
import io.harness.oidc.model.OidcTokenRequestData;

import com.github.scribejava.apis.openid.OpenIdOAuth2AccessToken;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Singleton
public class OidcTokenRetriever {
  private static final String SEPARATOR = "/";
  private static final String TOKEN = "token";
  private static final String AUTHORIZE = "authorize";
  public static final String SPACE = " ";

  String generateAccessTokenUrl(String providerUrl) {
    return providerUrl + SEPARATOR + TOKEN;
  }

  String generateAuthorizeUrl(String providerUrl) {
    return providerUrl + SEPARATOR + AUTHORIZE;
  }

  public OpenIdOAuth2AccessToken getAccessToken(OidcTokenRequestData oidcTokenRequestData)
      throws InterruptedException, ExecutionException, IOException {
    CustomIdentityProvider instance = CustomIdentityProvider.instance();
    instance.setAccessTokenEndpoint(generateAccessTokenUrl(oidcTokenRequestData.getProviderUrl()));
    instance.setAuthorizationBaseUrl(generateAuthorizeUrl(oidcTokenRequestData.getProviderUrl()));

    String scope = oidcTokenRequestData.getScope();
    scope = addNeededOidcScopesIfNotPresent(scope);
    OAuth20Service service = new ServiceBuilder(oidcTokenRequestData.getClientId())
                                 .apiSecret(oidcTokenRequestData.getClientSecret())
                                 .scope(scope)
                                 .build(instance);

    return (OpenIdOAuth2AccessToken) fetchAccessTokenUsingGrantType(service, oidcTokenRequestData);
  }

  @VisibleForTesting
  String addNeededOidcScopesIfNotPresent(String scope) {
    StringBuilder stringBuilder = new StringBuilder();
    // These 2 scopes are needed for access retrieval
    stringBuilder.append(K8sConstants.OPEN_ID);

    if (isBlank(scope)) {
      return stringBuilder.toString();
    }

    String[] scopes = scope.split(SPACE);

    List<String> scopeList = Arrays.stream(scopes)
                                 .filter(individualScopeStr -> isOtherThanDefaultNeededScope(individualScopeStr))
                                 .collect(toList());

    if (isNotEmpty(scopeList)) {
      scopeList.forEach(individualScopeStr -> stringBuilder.append(SPACE).append(individualScopeStr));
    }

    return stringBuilder.toString();
  }

  @VisibleForTesting
  boolean isOtherThanDefaultNeededScope(String individualScopeStr) {
    if (isBlank(individualScopeStr)) {
      return false;
    }

    return !K8sConstants.OPEN_ID.equals(individualScopeStr);
  }

  @VisibleForTesting
  OAuth2AccessToken fetchAccessTokenUsingGrantType(OAuth20Service service, OidcTokenRequestData oidcTokenRequestData)
      throws InterruptedException, ExecutionException, IOException {
    OAuth2AccessToken token = null;
    if (OidcGrantType.password.name().equals(oidcTokenRequestData.getGrantType())) {
      token =
          service.getAccessTokenPasswordGrant(oidcTokenRequestData.getUsername(), oidcTokenRequestData.getPassword());
    } else if (OidcGrantType.client_credentials.name().equals(oidcTokenRequestData.getGrantType())) {
      token = service.getAccessTokenClientCredentialsGrant();
    }

    return token;
  }

  public OidcTokenRequestData createOidcTokenRequestData(KubernetesConfig config) {
    return OidcTokenRequestData.builder()
        .providerUrl(config.getOidcIdentityProviderUrl())
        .clientId(config.getOidcClientId() == null ? null : new String(config.getOidcClientId()))
        .grantType(config.getOidcGrantType().name())
        .clientSecret(config.getOidcSecret() == null ? null : new String(config.getOidcSecret()))
        .username(config.getOidcUsername())
        .password(config.getOidcPassword() == null ? null : new String(config.getOidcPassword()))
        .scope(config.getOidcScopes())
        .build();
  }

  @VisibleForTesting
  public OpenIdOAuth2AccessToken retrieveOpenIdAccessToken(OidcTokenRequestData oidcTokenRequestData) {
    OpenIdOAuth2AccessToken accessToken = null;
    Exception ex = null;
    try {
      accessToken = getAccessToken(oidcTokenRequestData);
    } catch (InterruptedException intEx) {
      Thread.currentThread().interrupt();
      ex = intEx;
    } catch (Exception e) {
      ex = e;
    }

    if (ex != null) {
      throw new InvalidRequestException(
          "Failed to fetch OpenId Access Token. " + ExceptionUtils.getMessage(ex), ex, WingsException.USER);
    }
    return accessToken;
  }

  public String getOidcIdToken(KubernetesConfig config) {
    OidcTokenRequestData oidcTokenRequestData = createOidcTokenRequestData(config);

    OpenIdOAuth2AccessToken openIdOAuth2AccessToken = retrieveOpenIdAccessToken(oidcTokenRequestData);
    if (openIdOAuth2AccessToken != null) {
      return openIdOAuth2AccessToken.getOpenIdToken();
    }

    return null;
  }
}

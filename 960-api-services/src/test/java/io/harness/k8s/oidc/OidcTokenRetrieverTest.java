/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.oidc;

import static io.harness.k8s.K8sConstants.OPEN_ID;
import static io.harness.k8s.model.KubernetesClusterAuthType.OIDC;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANSHUL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.OidcGrantType;
import io.harness.oidc.model.OidcTokenRequestData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.github.scribejava.apis.openid.OpenIdOAuth2AccessToken;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class OidcTokenRetrieverTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks OidcTokenRetriever oidcTokenRetriever;

  @Spy OidcTokenRetriever spyOidcTokenRetriever;

  @Test
  @Owner(developers = OwnerRule.ADWAIT)
  @Category(UnitTests.class)
  public void testAddNeededOidcScopesIfNotPresent() {
    String scope = oidcTokenRetriever.addNeededOidcScopesIfNotPresent(null);
    assertThat(scope).isEqualTo(OPEN_ID);

    scope = oidcTokenRetriever.addNeededOidcScopesIfNotPresent("");
    assertThat(scope).isEqualTo(OPEN_ID);

    scope = oidcTokenRetriever.addNeededOidcScopesIfNotPresent("     ");
    assertThat(scope).isEqualTo(OPEN_ID);

    scope = oidcTokenRetriever.addNeededOidcScopesIfNotPresent(OPEN_ID);
    assertThat(scope).isEqualTo(OPEN_ID);

    scope = oidcTokenRetriever.addNeededOidcScopesIfNotPresent("A B");
    assertThat(scope).isEqualTo(OPEN_ID + " A B");

    scope = oidcTokenRetriever.addNeededOidcScopesIfNotPresent(OPEN_ID + "   "
        + "A      B");
    assertThat(scope).isEqualTo(OPEN_ID + " A B");
  }

  @Test
  @Owner(developers = OwnerRule.ADWAIT)
  @Category(UnitTests.class)
  public void testFetchAccessTokenUsingGrantType() throws Exception {
    OAuth20Service service = Mockito.mock(OAuth20Service.class);
    OAuth2AccessToken token = Mockito.mock(OAuth2AccessToken.class);

    Mockito.when(service.getAccessTokenPasswordGrant(any(), any())).thenReturn(token);

    oidcTokenRetriever.fetchAccessTokenUsingGrantType(
        service, OidcTokenRequestData.builder().grantType(OidcGrantType.password.name()).build());
    Mockito.verify(service, Mockito.times(1)).getAccessTokenPasswordGrant(any(), any());
    Mockito.verify(service, Mockito.never()).getAccessTokenClientCredentialsGrant();
  }

  @Test
  @Owner(developers = OwnerRule.ANSHUL)
  @Category(UnitTests.class)
  public void testGetAccessToken() throws Exception {
    Mockito.doReturn(null).when(spyOidcTokenRetriever).fetchAccessTokenUsingGrantType(any(), any());

    spyOidcTokenRetriever.getAccessToken(OidcTokenRequestData.builder()
                                             .clientId("clientId")
                                             .clientSecret("clientSecret")
                                             .scope("email")
                                             .grantType(OidcGrantType.password.name())
                                             .username("username")
                                             .password("password")
                                             .build());

    ArgumentCaptor<OidcTokenRequestData> captor = ArgumentCaptor.forClass(OidcTokenRequestData.class);
    Mockito.verify(spyOidcTokenRetriever, Mockito.times(1)).fetchAccessTokenUsingGrantType(any(), captor.capture());
    OidcTokenRequestData tokenRequestData = captor.getValue();

    assertThat(tokenRequestData.getClientId()).isEqualTo("clientId");
    assertThat(tokenRequestData.getUsername()).isEqualTo("username");
    assertThat(tokenRequestData.getPassword()).isEqualTo("password");
    assertThat(tokenRequestData.getClientSecret()).isEqualTo("clientSecret");
    assertThat(tokenRequestData.getScope()).isEqualTo("email");
  }

  @Test
  @Owner(developers = OwnerRule.ADWAIT)
  @Category(UnitTests.class)
  public void testFetchAccessTokenUsingGrantTypeForClientCredentials() throws Exception {
    OAuth20Service service = Mockito.mock(OAuth20Service.class);
    OAuth2AccessToken token = Mockito.mock(OAuth2AccessToken.class);

    Mockito.doReturn(token).when(service).getAccessTokenClientCredentialsGrant();

    oidcTokenRetriever.fetchAccessTokenUsingGrantType(
        service, OidcTokenRequestData.builder().grantType(OidcGrantType.client_credentials.name()).build());
    Mockito.verify(service, Mockito.times(1)).getAccessTokenClientCredentialsGrant();
    Mockito.verify(service, Mockito.never()).getAccessTokenPasswordGrant(any(), any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testRetrieveOpenIdAccessToken_ExceptionTest() throws Exception {
    doThrow(new InvalidRequestException("abc")).when(spyOidcTokenRetriever).getAccessToken(any());

    try {
      spyOidcTokenRetriever.retrieveOpenIdAccessToken(OidcTokenRequestData.builder().build());
    } catch (Exception e) {
      assertThat(e instanceof InvalidRequestException).isTrue();
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetOidcIdToken() throws Exception {
    OpenIdOAuth2AccessToken accessToken = mock(OpenIdOAuth2AccessToken.class);
    doReturn("oidcIdToken").when(accessToken).getOpenIdToken();
    doReturn(accessToken).when(spyOidcTokenRetriever).getAccessToken(any());

    KubernetesConfig kubeConfig = KubernetesConfig.builder()
                                      .authType(OIDC)
                                      .accountId("accId")
                                      .oidcClientId("clientId".toCharArray())
                                      .oidcGrantType(OidcGrantType.password)
                                      .oidcIdentityProviderUrl("url")
                                      .oidcUsername("user")
                                      .oidcPassword("pwd".toCharArray())
                                      .masterUrl("masterUrl")
                                      .oidcSecret("secret".toCharArray())
                                      .build();

    String oidcIdToken = spyOidcTokenRetriever.getOidcIdToken(kubeConfig);
    assertThat(oidcIdToken).isEqualTo("oidcIdToken");

    doReturn(null).when(spyOidcTokenRetriever).getAccessToken(any());
    oidcIdToken = spyOidcTokenRetriever.getOidcIdToken(kubeConfig);
    assertThat(oidcIdToken).isEqualTo(null);
  }
}

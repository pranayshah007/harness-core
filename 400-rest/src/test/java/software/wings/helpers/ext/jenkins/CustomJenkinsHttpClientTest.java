/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.jenkins;

import static io.harness.rule.OwnerRule.AGORODETKI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CustomJenkinsHttpClientTest extends CategoryTest {
  HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class);
  ArgumentCaptor<HttpHost> proxyHostCaptor = ArgumentCaptor.forClass(HttpHost.class);
  ArgumentCaptor<CredentialsProvider> credsProviderCaptor = ArgumentCaptor.forClass(CredentialsProvider.class);
  private static final String JENKINS_URL = "https://jenkins.test.com";
  private static final AuthScope PROXY_SCOPE = new AuthScope("proxyHost", 123);

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldAppendProxyConfig() throws URISyntaxException {
    System.setProperty("http.proxyHost", "proxyHost");
    System.setProperty("proxyScheme", "http");
    System.setProperty("http.proxyPort", "123");
    System.setProperty("http.proxyUser", "proxyUser");
    System.setProperty("http.proxyPassword", "proxyPassword");
    CustomJenkinsHttpClient.addAuthentication(httpClientBuilder, new URI(JENKINS_URL), null, null);
    Mockito.verify(httpClientBuilder).setProxy(proxyHostCaptor.capture());
    Mockito.verify(httpClientBuilder).setDefaultCredentialsProvider(credsProviderCaptor.capture());

    HttpHost capturedHost = proxyHostCaptor.getValue();
    CredentialsProvider capturedCredsProvider = credsProviderCaptor.getValue();

    assertThat(capturedHost.getHostName()).isEqualTo("proxyHost");
    assertThat(capturedHost.getPort()).isEqualTo(123);
    assertThat(capturedCredsProvider.getCredentials(PROXY_SCOPE).getUserPrincipal().getName()).isEqualTo("proxyUser");
    assertThat(capturedCredsProvider.getCredentials(PROXY_SCOPE).getPassword()).isEqualTo("proxyPassword");

    System.clearProperty("http.proxyHost");
    System.clearProperty("proxyScheme");
    System.clearProperty("http.proxyPort");
    System.clearProperty("http.proxyUser");
    System.clearProperty("http.proxyPassword");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotAppendProxyConfigWhenProxyIsNotEnabled() throws URISyntaxException {
    CustomJenkinsHttpClient.addAuthentication(httpClientBuilder, new URI(JENKINS_URL), null, null);
    Mockito.verify(httpClientBuilder, Mockito.times(1)).setProxy(null);
    Mockito.verify(httpClientBuilder, Mockito.times(1)).setProxy(any());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotAppendProxyConfigWhenJenkinsUrlIsInNonProxyList() throws URISyntaxException {
    System.setProperty("http.proxyHost", "proxyHost");
    System.setProperty("proxyScheme", "http");
    System.setProperty("http.proxyPort", "123");
    System.setProperty("http.proxyUser", "proxyUser");
    System.setProperty("http.proxyPassword", "proxyPassword");
    System.setProperty("http.nonProxyHosts", "jenkins.test.com");
    CustomJenkinsHttpClient.addAuthentication(httpClientBuilder, new URI(JENKINS_URL), null, null);

    Mockito.verify(httpClientBuilder, Mockito.times(1)).setProxy(null);
    Mockito.verify(httpClientBuilder, Mockito.times(1)).setProxy(any());

    System.clearProperty("http.proxyHost");
    System.clearProperty("proxyScheme");
    System.clearProperty("http.proxyPort");
    System.clearProperty("http.proxyUser");
    System.clearProperty("http.proxyPassword");
    System.clearProperty("http.nonProxyHosts");
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf.cfsdk;

import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VAIBHAV_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.network.Http;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;

import java.util.Optional;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.ProxyConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class CloudFoundryOperationsProviderTest extends CategoryTest {
  @Mock private ConnectionContextProvider connectionContextProvider;
  @Mock private CloudFoundryClientProvider cloudFoundryClientProvider;
  private MockedStatic<Http> httpMockedStatic;

  @InjectMocks private CloudFoundryOperationsProvider cloudFoundryOperationsProvider;

  @Before
  public void setup() {
    httpMockedStatic = mockStatic(Http.class);
  }

  @After
  public void cleanup() {
    httpMockedStatic.close();
  }

  @Test
  @Owner(developers = {ANSHUL, IVAN})
  @Category(UnitTests.class)
  public void testGetCloudFoundryOperationsWrapper() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName("username");
    cfRequestConfig.setPassword("password");
    cfRequestConfig.setEndpointUrl("api.run.pivotal.io");

    ConnectionContext connectionContextMock = mock(ConnectionContext.class);
    CloudFoundryClient cloudFoundryClientMock = mock(CloudFoundryClient.class);

    doReturn(connectionContextMock).when(connectionContextProvider).getConnectionContext(cfRequestConfig);
    doReturn(cloudFoundryClientMock)
        .when(cloudFoundryClientProvider)
        .getCloudFoundryClient(eq(cfRequestConfig), any(ConnectionContext.class));

    CloudFoundryOperationsWrapper cloudFoundryOperationsWrapper =
        cloudFoundryOperationsProvider.getCloudFoundryOperationsWrapper(cfRequestConfig);

    assertThat(cloudFoundryOperationsWrapper).isNotNull();
    assertThat(cloudFoundryOperationsWrapper.getCloudFoundryOperations()).isNotNull();
    assertThat(cloudFoundryOperationsWrapper.getCloudFoundryOperations())
        .isInstanceOf(DefaultCloudFoundryOperations.class);
  }

  @Test
  @Owner(developers = {ANSHUL, IVAN})
  @Category(UnitTests.class)
  public void testGetCFOperationsWrapperForConnectionContextException() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    doThrow(new PivotalClientApiException("Unable to get connection context"))
        .when(connectionContextProvider)
        .getConnectionContext(cfRequestConfig);

    try {
      cloudFoundryOperationsProvider.getCloudFoundryOperationsWrapper(cfRequestConfig);
      fail("Should not reach here.");
    } catch (PivotalClientApiException e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception while creating CloudFoundryOperations: Unable to get connection context");
    }
  }

  @Test
  @Owner(developers = {ANSHUL, IVAN})
  @Category(UnitTests.class)
  public void testGetCFOperationsWrapperForClientProviderException() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    doThrow(new PivotalClientApiException("Unable to get client provider"))
        .when(cloudFoundryClientProvider)
        .getCloudFoundryClient(any(), any());

    try {
      cloudFoundryOperationsProvider.getCloudFoundryOperationsWrapper(cfRequestConfig);
      fail("Should not reach here.");
    } catch (PivotalClientApiException e) {
      assertThat(e.getMessage())
          .isEqualTo("Exception while creating CloudFoundryOperations: Unable to get client provider");
    }
  }

  @Test
  @Owner(developers = VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testPcfProxyConfig() throws PivotalClientApiException {
    when(connectionContextProvider.getConnectionContext(any())).thenCallRealMethod();
    when(cloudFoundryClientProvider.getCloudFoundryClient(any(), any())).thenCallRealMethod();

    String hostname = "hostname";
    String port = "1502";
    String username = "username";
    String password = "password";
    String endpointUrl1 = "api.run.pivotal.io";
    String endpointUrl2 = "api.run.pivotal2.io";
    String endpointUrl3 = "api.run.pivotal3.io";
    String endpointUrl4 = "api.run.pivotal4.io";

    CfRequestConfig cfRequestConfig = getCfRequestConfig();
    cfRequestConfig.setUserName(username);
    cfRequestConfig.setPassword(password);
    cfRequestConfig.setEndpointUrl(endpointUrl1);

    // Case 1: Authenticated Proxy
    // Expected behaviour: hostname, port, username, password must be present inside ProxyConfiguration object
    httpMockedStatic.when(Http::getProxyHostName).thenAnswer((Answer<String>) invocation -> hostname);
    httpMockedStatic.when(Http::getProxyPort).thenAnswer((Answer<String>) invocation -> port);
    httpMockedStatic.when(Http::getProxyUserName).thenAnswer((Answer<String>) invocation -> username);
    httpMockedStatic.when(Http::getProxyPassword).thenAnswer((Answer<String>) invocation -> password);

    CloudFoundryOperationsWrapper cloudFoundryOperationsWrapper =
        cloudFoundryOperationsProvider.getCloudFoundryOperationsWrapper(cfRequestConfig);

    DefaultConnectionContext connectionContext =
        (DefaultConnectionContext) cloudFoundryOperationsWrapper.getConnectionContext();
    ProxyConfiguration proxyConfiguration = connectionContext.getProxyConfiguration().get();
    assertThat(proxyConfiguration.getHost()).isEqualTo(hostname);
    assertThat(proxyConfiguration.getPort().get().toString()).isEqualTo(port);
    assertThat(proxyConfiguration.getUsername().get()).isEqualTo(username);
    assertThat(proxyConfiguration.getPassword().get()).isEqualTo(password);

    // Case 1: Unauthenticated Proxy
    // Expected behaviour: hostname, port must be present inside ProxyConfiguration object
    // username and password must be empty
    httpMockedStatic.when(Http::getProxyHostName).thenAnswer((Answer<String>) invocation -> hostname);
    httpMockedStatic.when(Http::getProxyPort).thenAnswer((Answer<String>) invocation -> port);
    httpMockedStatic.when(Http::getProxyUserName).thenAnswer((Answer<Void>) invocation -> null);
    httpMockedStatic.when(Http::getProxyPassword).thenAnswer((Answer<Void>) invocation -> null);
    cfRequestConfig.setEndpointUrl(endpointUrl2);

    cloudFoundryOperationsWrapper = cloudFoundryOperationsProvider.getCloudFoundryOperationsWrapper(cfRequestConfig);

    connectionContext = (DefaultConnectionContext) cloudFoundryOperationsWrapper.getConnectionContext();
    proxyConfiguration = connectionContext.getProxyConfiguration().get();
    assertThat(proxyConfiguration.getHost()).isEqualTo(hostname);
    assertThat(proxyConfiguration.getPort().get().toString()).isEqualTo(port);
    assertThat(proxyConfiguration.getUsername()).isEqualTo(Optional.empty());
    assertThat(proxyConfiguration.getPassword()).isEqualTo(Optional.empty());

    // Case 3: No Proxy
    // Expected behaviour: The ProxyConfiguration object must be empty
    httpMockedStatic.when(Http::getProxyHostName).thenAnswer((Answer<Void>) invocation -> null);
    cfRequestConfig.setEndpointUrl(endpointUrl3);
    cloudFoundryOperationsWrapper = cloudFoundryOperationsProvider.getCloudFoundryOperationsWrapper(cfRequestConfig);

    connectionContext = (DefaultConnectionContext) cloudFoundryOperationsWrapper.getConnectionContext();
    assertThat(connectionContext.getProxyConfiguration()).isEqualTo(Optional.empty());

    // Case 4: Explicit No Proxy
    // Expected behaviour: The ProxyConfiguration object must be empty
    httpMockedStatic.when(() -> Http.shouldUseNonProxy(endpointUrl4)).thenAnswer((Answer<Boolean>) invocation -> true);
    httpMockedStatic.when(Http::getProxyHostName).thenAnswer((Answer<String>) invocation -> hostname);
    httpMockedStatic.when(Http::getProxyPort).thenAnswer((Answer<String>) invocation -> port);
    httpMockedStatic.when(Http::getProxyUserName).thenAnswer((Answer<String>) invocation -> username);
    httpMockedStatic.when(Http::getProxyPassword).thenAnswer((Answer<String>) invocation -> password);
    cfRequestConfig.setEndpointUrl(endpointUrl4);

    cloudFoundryOperationsWrapper = cloudFoundryOperationsProvider.getCloudFoundryOperationsWrapper(cfRequestConfig);

    connectionContext = (DefaultConnectionContext) cloudFoundryOperationsWrapper.getConnectionContext();
    assertThat(connectionContext.getProxyConfiguration()).isEqualTo(Optional.empty());
  }

  private CfRequestConfig getCfRequestConfig() {
    return CfRequestConfig.builder().timeOutIntervalInMins(1).orgName("org").applicationName("app").build();
  }
}

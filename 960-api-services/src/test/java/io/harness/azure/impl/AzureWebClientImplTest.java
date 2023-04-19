/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.appservice.fluent.WebAppsClient;
import com.azure.resourcemanager.appservice.fluent.WebSiteManagementClient;
import com.azure.resourcemanager.appservice.implementation.WebSiteManagementClientImpl;
import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import com.azure.resourcemanager.appservice.models.DeploymentSlots;
import com.azure.resourcemanager.appservice.models.WebApp;
import com.azure.resourcemanager.appservice.models.WebAppBasic;
import com.azure.resourcemanager.appservice.models.WebApps;
import com.azure.resourcemanager.appservice.models.WebDeploymentSlotBasic;
import com.azure.resourcemanager.resources.fluentcore.utils.PagedConverter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

public class AzureWebClientImplTest extends CategoryTest {
  private AzureResourceManager.Configurable configurable;
  private AzureResourceManager.Authenticated authenticated;
  private AzureResourceManager azure;

  @InjectMocks AzureWebClientImpl azureWebClientImpl;

  @Before
  public void before() throws Exception {
    MockitoAnnotations.initMocks(this);

    azure = mock(AzureResourceManager.class);
    configurable = mock(AzureResourceManager.Configurable.class);
    authenticated = mock(AzureResourceManager.Authenticated.class);

    MockedStatic<AzureResourceManager> azureMockStatic = mockStatic(AzureResourceManager.class);
    azureMockStatic.when(AzureResourceManager::configure).thenReturn(configurable);
    azureMockStatic.when(() -> AzureResourceManager.authenticate(any(HttpPipeline.class), any(AzureProfile.class)))
        .thenReturn(authenticated);
    when(configurable.withLogLevel(any(HttpLogDetailLevel.class))).thenReturn(configurable);
    when(configurable.withHttpClient(any(HttpClient.class))).thenReturn(configurable);
    when(configurable.withRetryPolicy(any())).thenReturn(configurable);
    when(configurable.authenticate(any(), any())).thenReturn(authenticated);
    when(authenticated.withSubscription(anyString())).thenReturn(azure);
    when(authenticated.withDefaultSubscription()).thenReturn(azure);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testListWebAppsByResourceGroupName() {
    AzureClientContext azureClientContext = buildAzureClientContext();

    WebAppBasic webAppBasicMock = mockWebAppBasic("webAppName", "resourceGroupName");
    WebApps webAppsMock = mockWebApps(null, Arrays.asList(webAppBasicMock));
    doReturn(webAppsMock).when(azure).webApps();

    List<WebAppBasic> response = azureWebClientImpl.listWebAppsByResourceGroupName(azureClientContext);

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(1);
    assertThat(response.get(0).name()).isEqualTo("webAppName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testListDeploymentSlotsByWebAppName() {
    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext();
    WebDeploymentSlotBasic webDeploymentSlotBasicMock1 = mockWebDeploymentSlotBasic();
    WebDeploymentSlotBasic webDeploymentSlotBasicMock2 = mockWebDeploymentSlotBasic();
    DeploymentSlots deploymentSlotsMock =
        mockDeploymentSlots(null, Arrays.asList(webDeploymentSlotBasicMock1, webDeploymentSlotBasicMock2));
    WebApp webAppMock = mockWebApp(null, deploymentSlotsMock);
    WebApps webAppsMock = mockWebApps(webAppMock, null);
    doReturn(webAppsMock).when(azure).webApps();

    List<WebDeploymentSlotBasic> response = azureWebClientImpl.listDeploymentSlotsByWebAppName(azureWebClientContext);

    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetWebAppByName() {
    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext();
    WebApp webAppMock = mockWebApp("webAppName", null);
    WebApps webAppsMock = mockWebApps(webAppMock, null);
    doReturn(webAppsMock).when(azure).webApps();

    Optional<WebApp> response = azureWebClientImpl.getWebAppByName(azureWebClientContext);

    assertThat(response.isPresent()).isTrue();
    assertThat(response.get()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentSlotByName() {
    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext();
    String slotName = "slotName";
    DeploymentSlot deploymentSlotMock = mockDeploymentSlot("deploymentSlot");
    DeploymentSlots deploymentSlotsMock = mockDeploymentSlots(deploymentSlotMock, null);
    WebApp webAppMock = mockWebApp(null, deploymentSlotsMock);
    WebApps webAppsMock = mockWebApps(webAppMock, null);
    doReturn(webAppsMock).when(azure).webApps();

    Optional<DeploymentSlot> response = azureWebClientImpl.getDeploymentSlotByName(azureWebClientContext, slotName);

    assertThat(response.isPresent()).isTrue();
    assertThat(response.get()).isNotNull();
    assertThat(response.get().name()).isEqualTo("deploymentSlot");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testStartDeploymentSlot() {
    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext();
    String slotName = "slotName";
    DeploymentSlot deploymentSlotMock = mockDeploymentSlot(slotName);
    DeploymentSlots deploymentSlotsMock = mockDeploymentSlots(deploymentSlotMock, null);
    WebApp webAppMock = mockWebApp(null, deploymentSlotsMock);
    WebApps webAppsMock = mockWebApps(webAppMock, null);
    doReturn(webAppsMock).when(azure).webApps();

    doNothing().when(deploymentSlotMock).start();

    azureWebClientImpl.startDeploymentSlot(azureWebClientContext, slotName);

    verify(deploymentSlotMock, times(1)).start();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testStartDeploymentSlotAsync() {
    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext();
    String slotName = "slotName";

    WebSiteManagementClientImpl webSiteManagementClient = mock(WebSiteManagementClientImpl.class);
    AzureWebClientImpl azureWebClient = mock(AzureWebClientImpl.class);
    WebAppsClient webAppsClientMock = mockWebAppsClient(webSiteManagementClient);
    doCallRealMethod().when(azureWebClient).startDeploymentSlotAsync(any(), any(), any());

    Mono<Response<Void>> responseMono = Mono.just(new Response<Void>() {
      @Override
      public int getStatusCode() {
        return 200;
      }

      @Override
      public HttpHeaders getHeaders() {
        return null;
      }

      @Override
      public HttpRequest getRequest() {
        return null;
      }

      @Override
      public Void getValue() {
        return null;
      }
    });

    doReturn(responseMono).when(webAppsClientMock).startSlotWithResponseAsync(anyString(), anyString(), anyString());

    Mono<Response<Void>> response =
        azureWebClientImpl.startDeploymentSlotAsync(azureWebClientContext, slotName, webAppsClientMock);

    verify(webAppsClientMock, times(1)).startSlotWithResponseAsync(anyString(), anyString(), anyString());
    assertThat(response).isNotNull();
    assertThat(response.block().getStatusCode()).isGreaterThanOrEqualTo(200).isLessThan(300);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testStartDeploymentSlotWithDeploymentSlot() {
    DeploymentSlot mockDeploymentSlot = mockDeploymentSlot("slotName");
    doNothing().when(mockDeploymentSlot).start();

    azureWebClientImpl.startDeploymentSlot(mockDeploymentSlot);

    verify(mockDeploymentSlot, times(1)).start();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testStartDeploymentSlotAsyncWithDeploymentSlot() throws InstantiationException, IllegalAccessException {
    DeploymentSlot mockDeploymentSlot = mockDeploymentSlot("slotName");
    Mono<Void> voidMono = Mono.empty();
    doReturn(voidMono).when(mockDeploymentSlot).startAsync();

    azureWebClientImpl.startDeploymentSlotAsync(mockDeploymentSlot);

    verify(mockDeploymentSlot, times(1)).startAsync();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testStopDeploymentSlot() {
    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext();
    String slotName = "slotName";

    DeploymentSlot deploymentSlotMock = mockDeploymentSlot(slotName);
    DeploymentSlots deploymentSlotsMock = mockDeploymentSlots(deploymentSlotMock, null);
    WebApp webAppMock = mockWebApp(null, deploymentSlotsMock);
    WebApps webAppsMock = mockWebApps(webAppMock, null);
    doReturn(webAppsMock).when(azure).webApps();

    doNothing().when(deploymentSlotMock).stop();

    azureWebClientImpl.stopDeploymentSlot(azureWebClientContext, slotName);

    verify(deploymentSlotMock, times(1)).stop();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testStopDeploymentSlotAsync() {
    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext();
    String slotName = "slotName";

    WebSiteManagementClientImpl webSiteManagementClient = mock(WebSiteManagementClientImpl.class);
    AzureWebClientImpl azureWebClient = mock(AzureWebClientImpl.class);
    WebAppsClient webAppsClientMock = mockWebAppsClient(webSiteManagementClient);
    doCallRealMethod().when(azureWebClient).stopDeploymentSlotAsync(any(), any(), any());

    Mono<Response<Void>> responseMono = Mono.just(new Response<Void>() {
      @Override
      public int getStatusCode() {
        return 200;
      }

      @Override
      public HttpHeaders getHeaders() {
        return null;
      }

      @Override
      public HttpRequest getRequest() {
        return null;
      }

      @Override
      public Void getValue() {
        return null;
      }
    });

    doReturn(responseMono).when(webAppsClientMock).stopSlotWithResponseAsync(anyString(), anyString(), anyString());

    Mono<Response<Void>> response =
        azureWebClientImpl.stopDeploymentSlotAsync(azureWebClientContext, slotName, webAppsClientMock);

    verify(webAppsClientMock, times(1)).stopSlotWithResponseAsync(anyString(), anyString(), anyString());
    assertThat(response).isNotNull();
    assertThat(response.block().getStatusCode()).isGreaterThanOrEqualTo(200).isLessThan(300);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testStopDeploymentSlotWithDeploymentSlot() {
    DeploymentSlot mockDeploymentSlot = mockDeploymentSlot("slotName");
    doNothing().when(mockDeploymentSlot).stop();

    azureWebClientImpl.stopDeploymentSlot(mockDeploymentSlot);

    verify(mockDeploymentSlot, times(1)).stop();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testStopDeploymentSlotAsyncWithDeploymentSlot() throws InstantiationException, IllegalAccessException {
    DeploymentSlot mockDeploymentSlot = mockDeploymentSlot("slotName");
    Mono<Void> voidMono = Mono.empty();
    doReturn(voidMono).when(mockDeploymentSlot).stopAsync();

    azureWebClientImpl.stopDeploymentSlotAsync(mockDeploymentSlot);

    verify(mockDeploymentSlot, times(1)).stopAsync();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetSlotState() {
    AzureWebClientContext azureWebClientContext = buildAzureWebClientContext();
    String slotName = "slotName";
    String runningSlotState = "Running";

    DeploymentSlot deploymentSlotMock = mockDeploymentSlot(slotName);
    DeploymentSlots deploymentSlotsMock = mockDeploymentSlots(deploymentSlotMock, null);
    WebApp webAppMock = mockWebApp(null, deploymentSlotsMock);
    WebApps webAppsMock = mockWebApps(webAppMock, null);
    doReturn(webAppsMock).when(azure).webApps();
    doReturn(runningSlotState).when(deploymentSlotMock).state();

    String slotState = azureWebClientImpl.getSlotState(azureWebClientContext, slotName);

    assertThat(slotState).isNotNull();
    assertThat(slotState).isEqualTo(runningSlotState);
  }

  public AzureWebClientContext buildAzureWebClientContext() {
    return AzureWebClientContext.builder()
        .azureConfig(buildAzureConfig())
        .resourceGroupName("resourceGroupName")
        .subscriptionId("subscriptionId")
        .appName("webAppName")
        .build();
  }

  public AzureClientContext buildAzureClientContext() {
    return new AzureClientContext(buildAzureConfig(), "subscriptionId", "resourceGroupName");
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder()
        .key("key".toCharArray())
        .clientId("clientId")
        .tenantId("tenantId")
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .build();
  }

  @NotNull
  public <T> PagedIterable<T> getPagedIterable(Response<List<T>> response) {
    return new PagedIterable<T>(PagedConverter.convertListToPagedFlux(Mono.just(response)));
  }

  private WebAppsClient mockWebAppsClient(WebSiteManagementClient webSiteManagementClientMock) {
    WebAppsClient webAppsClientMock = mock(WebAppsClient.class);
    when(webSiteManagementClientMock.getWebApps()).thenReturn(webAppsClientMock);
    return webAppsClientMock;
  }

  private WebApp mockWebApp(String name, DeploymentSlots deploymentSlots) {
    WebApp webAppMock = mock(WebApp.class);

    if (deploymentSlots != null) {
      doReturn(deploymentSlots).when(webAppMock).deploymentSlots();
    }

    if (name != null) {
      doReturn(name).when(webAppMock).name();
    }

    return webAppMock;
  }

  private WebDeploymentSlotBasic mockWebDeploymentSlotBasic() {
    WebDeploymentSlotBasic webDeploymentSlotBasicMock = mock(WebDeploymentSlotBasic.class);

    return webDeploymentSlotBasicMock;
  }

  private DeploymentSlot mockDeploymentSlot(String name) {
    DeploymentSlot deploymentSlotMock = mock(DeploymentSlot.class);

    if (name != null) {
      doReturn(name).when(deploymentSlotMock).name();
    }

    return deploymentSlotMock;
  }

  private DeploymentSlots mockDeploymentSlots(
      DeploymentSlot deploymentSlot, List<WebDeploymentSlotBasic> webDeploymentSlotBasics) {
    DeploymentSlots deploymentSlotsMock = mock(DeploymentSlots.class);

    if (deploymentSlot != null) {
      doReturn(deploymentSlot).when(deploymentSlotsMock).getByName(any());
    }

    if (webDeploymentSlotBasics != null) {
      Response list = new SimpleResponse(null, 200, null, webDeploymentSlotBasics);
      doReturn(getPagedIterable(list)).when(deploymentSlotsMock).list();
    }

    return deploymentSlotsMock;
  }

  private WebApps mockWebApps(WebApp webApp, List<WebAppBasic> webAppBasics) {
    WebApps webAppsMock = mock(WebApps.class);

    if (webApp != null) {
      doReturn(webApp).when(webAppsMock).getByResourceGroup(any(), any());
    }

    if (webAppBasics != null) {
      Response resp = new SimpleResponse(null, 200, null, webAppBasics);
      doReturn(getPagedIterable(resp)).when(webAppsMock).listByResourceGroup(any());
    }

    return webAppsMock;
  }

  private WebAppBasic mockWebAppBasic(String name, String resourceGroup) {
    WebAppBasic webAppBasicMock = mock(WebAppBasic.class);

    if (name != null) {
      doReturn(name).when(webAppBasicMock).name();
    }

    if (resourceGroup != null) {
      doReturn(resourceGroup).when(webAppBasicMock).resourceGroupName();
    }

    return webAppBasicMock;
  }
}

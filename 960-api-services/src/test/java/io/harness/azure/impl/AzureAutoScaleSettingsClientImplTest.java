/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.Response;
import com.azure.core.http.rest.SimpleResponse;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.monitor.fluent.AutoscaleSettingsClient;
import com.azure.resourcemanager.monitor.fluent.MonitorClient;
import com.azure.resourcemanager.monitor.fluent.models.AutoscaleSettingResourceInner;
import com.azure.resourcemanager.monitor.implementation.MonitorClientImpl;
import com.azure.resourcemanager.monitor.models.AutoscaleProfile;
import com.azure.resourcemanager.monitor.models.AutoscaleSetting;
import com.azure.resourcemanager.monitor.models.AutoscaleSettings;
import com.azure.resourcemanager.monitor.models.ScaleCapacity;
import com.azure.resourcemanager.resources.fluentcore.utils.PagedConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

public class AzureAutoScaleSettingsClientImplTest extends CategoryTest {
  private AzureResourceManager.Configurable configurable;
  private AzureResourceManager.Authenticated authenticated;
  private AzureResourceManager azure;

  @InjectMocks AzureAutoScaleSettingsClientImpl azureAutoScaleSettingsClient;

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
  public void testGetAutoScaleSettingByTargetResourceId() throws Exception {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String targetResourceId = "targetResourceId";
    String autoScaleSettingId = "autoScaleSettingId";
    AzureConfig azureConfig = buildAzureConfig();

    mockAutosScaleSettings(resourceGroupName, targetResourceId, autoScaleSettingId);

    Optional<AutoscaleSetting> response = azureAutoScaleSettingsClient.getAutoScaleSettingByTargetResourceId(
        azureConfig, subscriptionId, resourceGroupName, targetResourceId);

    response.ifPresent(as -> Assertions.assertThat(as).isNotNull());
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testAttachAutoScaleSettingToTargetResourceId() throws Exception {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String targetResourceId = "targetResourceId";
    String autoScaleSettingId = "autoScaleSettingId";
    String autoScaleSettingsJSON = "{\n"
        + "    \"location\": \"eastus\",\n"
        + "    \"tags\": {},\n"
        + "    \"properties\": {\n"
        + "        \"name\": \"testAutoScaleName\",\n"
        + "        \"enabled\": true,\n"
        + "        \"targetResourceUri\": \"targetResourceId\",\n"
        + "        \"profiles\": [\n"
        + "            {\n"
        + "                \"name\": \"Auto created scale condition\",\n"
        + "                \"capacity\": {\n"
        + "                    \"minimum\": \"2\",\n"
        + "                    \"maximum\": \"2\",\n"
        + "                    \"default\": \"2\"\n"
        + "                },\n"
        + "                \"rules\": [],\n"
        + "                \"fixedDate\": {\n"
        + "                    \"timeZone\": \"UTC\",\n"
        + "                    \"start\": \"2020-07-05T00:00:00.000Z\",\n"
        + "                    \"end\": \"2020-07-05T23:59:00.000Z\"\n"
        + "                }\n"
        + "            },\n"
        + "            {\n"
        + "                \"name\": \"Profile 2\",\n"
        + "                \"capacity\": {\n"
        + "                    \"minimum\": \"1\",\n"
        + "                    \"maximum\": \"1\",\n"
        + "                    \"default\": \"1\"\n"
        + "                },\n"
        + "                \"rules\": [],\n"
        + "                \"fixedDate\": {\n"
        + "                    \"timeZone\": \"UTC\",\n"
        + "                    \"start\": \"2020-08-18T00:00:00.000Z\",\n"
        + "                    \"end\": \"2020-08-18T23:59:00.000Z\"\n"
        + "                }\n"
        + "            },\n"
        + "            {\n"
        + "                \"name\": \"Profile 3\",\n"
        + "                \"capacity\": {\n"
        + "                    \"minimum\": \"1\",\n"
        + "                    \"maximum\": \"3\",\n"
        + "                    \"default\": \"2\"\n"
        + "                },\n"
        + "                \"rules\": [\n"
        + "                    {\n"
        + "                        \"scaleAction\": {\n"
        + "                            \"direction\": \"Increase\",\n"
        + "                            \"type\": \"ChangeCount\",\n"
        + "                            \"value\": \"1\",\n"
        + "                            \"cooldown\": \"PT5M\"\n"
        + "                        },\n"
        + "                        \"metricTrigger\": {\n"
        + "                            \"metricName\": \"Percentage CPU\",\n"
        + "                            \"metricNamespace\": \"\",\n"
        + "                            \"metricResourceUri\": \"metricResourceUri\",\n"
        + "                            \"operator\": \"GreaterThan\",\n"
        + "                            \"statistic\": \"Average\",\n"
        + "                            \"threshold\": 70,\n"
        + "                            \"timeAggregation\": \"Average\",\n"
        + "                            \"timeGrain\": \"PT1M\",\n"
        + "                            \"timeWindow\": \"PT10M\",\n"
        + "                            \"Dimensions\": [],\n"
        + "                            \"dividePerInstance\": false\n"
        + "                        }\n"
        + "                    }\n"
        + "                ]\n"
        + "            }\n"
        + "        ],\n"
        + "        \"notifications\": [],\n"
        + "        \"targetResourceLocation\": \"eastus\"\n"
        + "    },\n"
        + "    \"id\": \"AutoScaleSettingsId\",\n"
        + "    \"name\": \"AutoScaleSettingsName\",\n"
        + "    \"type\": \"Microsoft.Insights/autoscaleSettings\"\n"
        + "}";
    List<String> autoScaleSettingsJSONs = Collections.singletonList(autoScaleSettingsJSON);
    ScaleCapacity defaultProfileScalePolicy = new ScaleCapacity();
    defaultProfileScalePolicy.withMaximum("1");
    defaultProfileScalePolicy.withMinimum("0");
    defaultProfileScalePolicy.withDefaultProperty("1");
    AzureConfig azureConfig = buildAzureConfig();

    MonitorClientImpl monitorClientMock = mock(MonitorClientImpl.class);
    AutoscaleSettingsClient autoscaleSettingsClientMock = mockAutoscaleSettingsClient(monitorClientMock);
    Mono<AutoscaleSettingResourceInner> autoscaleSettingResourceInnerMono = mock(Mono.class);

    ArgumentCaptor<AutoscaleSettingResourceInner> autoScaleSettingResourceInnerCapture =
        ArgumentCaptor.forClass(AutoscaleSettingResourceInner.class);
    Mockito.doReturn(autoscaleSettingResourceInnerMono)
        .when(autoscaleSettingsClientMock)
        .createOrUpdateAsync(any(), anyString(), autoScaleSettingResourceInnerCapture.capture());

    azureAutoScaleSettingsClient.attachAutoScaleSettingToTargetResourceId(azureConfig, subscriptionId,
        resourceGroupName, targetResourceId, autoScaleSettingsJSONs.get(0), defaultProfileScalePolicy,
        autoscaleSettingsClientMock);

    AutoscaleSettingResourceInner autoScaleSettingResult = autoScaleSettingResourceInnerCapture.getValue();
    Assertions.assertThat(autoScaleSettingResult).isNotNull();
    Assertions.assertThat(autoScaleSettingResult.targetResourceUri()).isEqualTo(targetResourceId);
    Assertions.assertThat(autoScaleSettingResult.profiles().size()).isEqualTo(3);
    Assertions.assertThat(autoScaleSettingResult.profiles().get(0).capacity().minimum())
        .isEqualTo(defaultProfileScalePolicy.minimum());
    Assertions.assertThat(autoScaleSettingResult.profiles().get(0).capacity().defaultProperty())
        .isEqualTo(defaultProfileScalePolicy.defaultProperty());
    Assertions.assertThat(autoScaleSettingResult.profiles().get(0).capacity().maximum())
        .isEqualTo(defaultProfileScalePolicy.maximum());
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testClearAutoScaleSettingOnTargetResourceId() throws Exception {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String targetResourceId = "targetResourceId";
    String autoScaleSettingId = "autoScaleSettingId";
    AzureConfig azureConfig = buildAzureConfig();

    AutoscaleSettings mockAutoScaleSettings =
        mockAutosScaleSettings(resourceGroupName, targetResourceId, autoScaleSettingId);

    ArgumentCaptor<String> autoScaleSettingIdCapture = ArgumentCaptor.forClass(String.class);
    Mockito.doNothing().when(mockAutoScaleSettings).deleteById(autoScaleSettingIdCapture.capture());

    azureAutoScaleSettingsClient.clearAutoScaleSettingOnTargetResourceId(
        azureConfig, subscriptionId, resourceGroupName, targetResourceId);

    String autoScaleSettingIdResult = autoScaleSettingIdCapture.getValue();
    Assertions.assertThat(autoScaleSettingIdResult).isNotNull();
    Assertions.assertThat(autoScaleSettingIdResult).isEqualTo(autoScaleSettingId);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetDefaultAutoScaleProfile() throws Exception {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String targetResourceId = "targetResourceId";
    String autoScaleSettingId = "autoScaleSettingId";
    AzureConfig azureConfig = buildAzureConfig();

    mockAutosScaleSettings(resourceGroupName, targetResourceId, autoScaleSettingId);

    Optional<AutoscaleProfile> response = azureAutoScaleSettingsClient.getDefaultAutoScaleProfile(
        azureConfig, subscriptionId, resourceGroupName, targetResourceId);

    response.ifPresent(as -> Assertions.assertThat(as).isNotNull());
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testListAutoScaleProfilesByTargetResourceId() throws Exception {
    String subscriptionId = "subscriptionId";
    String resourceGroupName = "resourceGroupName";
    String targetResourceId = "targetResourceId";
    String autoScaleSettingId = "autoScaleSettingId";
    AzureConfig azureConfig = buildAzureConfig();

    mockAutosScaleSettings(resourceGroupName, targetResourceId, autoScaleSettingId);

    List<AutoscaleProfile> response = azureAutoScaleSettingsClient.listAutoScaleProfilesByTargetResourceId(
        azureConfig, subscriptionId, resourceGroupName, targetResourceId);

    Assertions.assertThat(response).isNotNull();
    Assertions.assertThat(response.size()).isEqualTo(1);
  }

  public AutoscaleSettings mockAutosScaleSettings(
      String resourceGroupName, String targetResourceId, String autoScaleSettingId) throws IOException {
    AutoscaleSettings mockAutoScaleSettings = Mockito.mock(AutoscaleSettings.class);
    AutoscaleSetting mockAutoScaleSetting = Mockito.mock(AutoscaleSetting.class);

    List<AutoscaleSetting> responseList = new ArrayList<>();
    responseList.add(mockAutoScaleSetting);
    Response response = new SimpleResponse(null, 200, null, responseList);

    Mockito.when(azure.autoscaleSettings()).thenReturn(mockAutoScaleSettings);
    Mockito.when(mockAutoScaleSettings.listByResourceGroup(resourceGroupName)).thenReturn(getPagedIterable(response));
    Mockito.when(mockAutoScaleSetting.autoscaleEnabled()).thenReturn(true);
    Mockito.when(mockAutoScaleSetting.targetResourceId()).thenReturn(targetResourceId);
    Mockito.when(mockAutoScaleSetting.id()).thenReturn(autoScaleSettingId);
    AutoscaleProfile autoscaleProfile = Mockito.mock(AutoscaleProfile.class);
    Mockito.when(mockAutoScaleSetting.profiles()).thenReturn(new HashMap<String, AutoscaleProfile>() {
      { put("profile1", autoscaleProfile); }
    });
    return mockAutoScaleSettings;
  }

  private AutoscaleSettingsClient mockAutoscaleSettingsClient(MonitorClient monitorClientMock) {
    AutoscaleSettingsClient autoscaleSettingsClientMock = mock(AutoscaleSettingsClient.class);
    when(monitorClientMock.getAutoscaleSettings()).thenReturn(autoscaleSettingsClientMock);
    return autoscaleSettingsClientMock;
  }

  @NotNull
  public <T> PagedIterable<T> getPagedIterable(Response<List<T>> response) {
    return new PagedIterable<T>(PagedConverter.convertListToPagedFlux(Mono.just(response)));
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder()
        .key("key".toCharArray())
        .clientId("clientId")
        .tenantId("tenantId")
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .build();
  }
}

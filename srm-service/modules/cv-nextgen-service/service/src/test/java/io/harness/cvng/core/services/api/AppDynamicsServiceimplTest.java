/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.AppdynamicsValidationResponse;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.ThirdPartyApiResponseStatus;
import io.harness.cvng.beans.appd.AppDynamicFetchFileStructureRequest;
import io.harness.cvng.beans.appd.AppDynamicSingleMetricDataRequest;
import io.harness.cvng.beans.appd.AppDynamicsApplication;
import io.harness.cvng.beans.appd.AppDynamicsFileDefinition;
import io.harness.cvng.beans.appd.AppDynamicsFileDefinition.FileType;
import io.harness.cvng.beans.appd.AppDynamicsTier;
import io.harness.cvng.beans.appd.AppdynamicsMetricDataResponse;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.CorrelationContext;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CV)
public class AppDynamicsServiceimplTest extends CvNextGenTestBase {
  @Inject AppDynamicsService appDynamicsService;
  @Inject private MetricPackService metricPackService;
  @Inject private OnboardingService onboardingService;
  @Mock NextGenService nextGenService;
  @Mock VerificationManagerService verificationManagerService;
  private String accountId;
  private String connectorIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private BuilderFactory builderFactory;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    accountId = generateUuid();
    connectorIdentifier = generateUuid();
    projectIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    FieldUtils.writeField(appDynamicsService, "onboardingService", onboardingService, true);
    FieldUtils.writeField(onboardingService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(onboardingService, "verificationManagerService", verificationManagerService, true);
    FieldUtils.writeField(appDynamicsService, "clock", builderFactory.getClock(), true);
    CorrelationContext.setCorrelationId(generateUuid());

    when(nextGenService.get(anyString(), anyString(), anyString(), anyString()))
        .then(invocation
            -> Optional.of(
                ConnectorInfoDTO.builder().connectorConfig(AppDynamicsConnectorDTO.builder().build()).build()));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetApplications() {
    List<AppDynamicsApplication> appDynamicsApplications = new ArrayList<>();
    int numOfApplications = 100;
    for (int i = 0; i < numOfApplications; i++) {
      appDynamicsApplications.add(AppDynamicsApplication.builder().name("app-" + i).id(i).build());
    }
    Collections.shuffle(appDynamicsApplications);
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(appDynamicsApplications));
    PageResponse<AppDynamicsApplication> applications = appDynamicsService.getApplications(
        accountId, connectorIdentifier, generateUuid(), generateUuid(), 0, 5, "ApP-2");
    assertThat(applications.getContent())
        .isEqualTo(Lists.newArrayList(AppDynamicsApplication.builder().name("app-2").id(2).build(),
            AppDynamicsApplication.builder().name("app-20").id(20).build(),
            AppDynamicsApplication.builder().name("app-21").id(21).build(),
            AppDynamicsApplication.builder().name("app-22").id(22).build(),
            AppDynamicsApplication.builder().name("app-23").id(23).build()));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetTiers() {
    Set<AppDynamicsTier> appDynamicsTiers = new HashSet<>();
    int numOfApplications = 100;
    for (int i = 0; i < numOfApplications; i++) {
      appDynamicsTiers.add(AppDynamicsTier.builder().name("tier-" + i).id(i).build());
    }
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(JsonUtils.asJson(appDynamicsTiers));
    PageResponse<AppDynamicsTier> applications = appDynamicsService.getTiers(
        accountId, connectorIdentifier, generateUuid(), generateUuid(), generateUuid(), 0, 5, "IeR-2");
    assertThat(applications.getContent())
        .isEqualTo(Lists.newArrayList(AppDynamicsTier.builder().name("tier-2").id(2).build(),
            AppDynamicsTier.builder().name("tier-20").id(20).build(),
            AppDynamicsTier.builder().name("tier-21").id(21).build(),
            AppDynamicsTier.builder().name("tier-22").id(22).build(),
            AppDynamicsTier.builder().name("tier-23").id(23).build()));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetMetricPackData() throws IOException, IllegalAccessException {
    final List<MetricPackDTO> metricPacks =
        metricPackService.getMetricPacks(DataSourceType.APP_DYNAMICS, accountId, orgIdentifier, projectIdentifier);
    assertThat(metricPacks).isNotEmpty();

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/appd_metric_data_validation.json"), Charsets.UTF_8);
    JsonUtils.asObject(textLoad, OnboardingResponseDTO.class);

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(appDynamicsService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(anyString(), any(OnboardingRequestDTO.class)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    Set<AppdynamicsValidationResponse> metricPackData = appDynamicsService.getMetricPackData(accountId, generateUuid(),
        orgIdentifier, projectIdentifier, generateUuid(), generateUuid(), generateUuid(), metricPacks);

    // verify errors pack
    AppdynamicsValidationResponse errorValidationResponse =
        metricPackData.stream()
            .filter(validationResponse -> validationResponse.getMetricPackName().equals("Errors"))
            .findFirst()
            .orElse(null);
    assertThat(errorValidationResponse).isNotNull();
    assertThat(errorValidationResponse.getOverallStatus()).isEqualTo(ThirdPartyApiResponseStatus.SUCCESS);
    List<AppdynamicsValidationResponse.AppdynamicsMetricValueValidationResponse> metricValueValidationResponses =
        errorValidationResponse.getValues();
    assertThat(metricValueValidationResponses.size()).isEqualTo(1);
    assertThat(metricValueValidationResponses.get(0))
        .isEqualTo(AppdynamicsValidationResponse.AppdynamicsMetricValueValidationResponse.builder()
                       .metricName("Number of Errors")
                       .apiResponseStatus(ThirdPartyApiResponseStatus.SUCCESS)
                       .value(233)
                       .build());

    // verify performance pack
    AppdynamicsValidationResponse performanceValidationResponse =
        metricPackData.stream()
            .filter(validationResponse -> validationResponse.getMetricPackName().equals("Performance"))
            .findFirst()
            .orElse(null);
    assertThat(performanceValidationResponse).isNotNull();
    assertThat(performanceValidationResponse.getOverallStatus()).isEqualTo(ThirdPartyApiResponseStatus.NO_DATA);
    metricValueValidationResponses = performanceValidationResponse.getValues();
    assertThat(metricValueValidationResponses.size()).isEqualTo(4);

    metricValueValidationResponses.forEach(metricValueValidationResponse -> {
      if (metricValueValidationResponse.getMetricName().equals("Stall Count")) {
        assertThat(metricValueValidationResponse.getApiResponseStatus()).isEqualTo(ThirdPartyApiResponseStatus.NO_DATA);
        assertThat(metricValueValidationResponse.getValue()).isEqualTo(0.0);
      } else {
        assertThat(metricValueValidationResponse.getApiResponseStatus()).isEqualTo(ThirdPartyApiResponseStatus.SUCCESS);
        assertThat(metricValueValidationResponse.getValue()).isGreaterThan(0.0);
      }
    });
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetBaseFolders() throws IOException, IllegalAccessException {
    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/appd/appd_file_structure_dsl_sample_output.json"),
        Charsets.UTF_8);
    JsonUtils.asObject(textLoad, OnboardingResponseDTO.class);

    DataCollectionRequest request = AppDynamicFetchFileStructureRequest.builder()
                                        .appName("appName")
                                        .path("path")
                                        .type(DataCollectionRequestType.APPDYNAMICS_FETCH_METRIC_STRUCTURE)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(connectorIdentifier)
            .accountId(builderFactory.getContext().getProjectParams().getAccountIdentifier())
            .tracingId("tracingId")
            .orgIdentifier(builderFactory.getContext().getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getContext().getProjectParams().getProjectIdentifier())
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(appDynamicsService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    List<String> baseFolders = appDynamicsService.getBaseFolders(
        builderFactory.getContext().getProjectParams(), connectorIdentifier, "appName", "path", "tracingId");

    assertThat(baseFolders.size()).isEqualTo(6);
    assertThat(baseFolders.get(0)).isEqualTo("Numbers");
    assertThat(baseFolders.get(2)).isEqualTo("External Calls");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetricStructure() throws IOException, IllegalAccessException {
    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/appd/appd_file_structure_dsl_sample_output.json"),
        Charsets.UTF_8);
    JsonUtils.asObject(textLoad, OnboardingResponseDTO.class);

    DataCollectionRequest request = AppDynamicFetchFileStructureRequest.builder()
                                        .appName("appName")
                                        .path("baseFolder|tier|metricPath")
                                        .type(DataCollectionRequestType.APPDYNAMICS_FETCH_METRIC_STRUCTURE)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(connectorIdentifier)
            .accountId(builderFactory.getContext().getProjectParams().getAccountIdentifier())
            .tracingId("tracingId")
            .orgIdentifier(builderFactory.getContext().getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getContext().getProjectParams().getProjectIdentifier())
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(appDynamicsService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    List<AppDynamicsFileDefinition> fileDefinitionList =
        appDynamicsService.getMetricStructure(builderFactory.getContext().getProjectParams(), connectorIdentifier,
            "appName", "baseFolder", "tier", "metricPath", "tracingId");

    assertThat(fileDefinitionList.size()).isEqualTo(16);
    assertThat(fileDefinitionList.get(0).getName()).isEqualTo("Average Async Processing Time (ms)");
    assertThat(fileDefinitionList.get(0).getType()).isEqualTo(FileType.LEAF);
    assertThat(fileDefinitionList.get(15).getName()).isEqualTo("Individual Nodes");
    assertThat(fileDefinitionList.get(15).getType()).isEqualTo(FileType.FOLDER);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetServiceInstanceMetricPath() throws IOException, IllegalAccessException {
    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/appd/appd_file_structure_dsl_sample_output.json"),
        Charsets.UTF_8);
    JsonUtils.asObject(textLoad, OnboardingResponseDTO.class);

    DataCollectionRequest request = AppDynamicFetchFileStructureRequest.builder()
                                        .appName("appName")
                                        .path("baseFolder|tier|metricPath")
                                        .type(DataCollectionRequestType.APPDYNAMICS_FETCH_METRIC_STRUCTURE)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(connectorIdentifier)
            .accountId(builderFactory.getContext().getProjectParams().getAccountIdentifier())
            .tracingId("tracingId")
            .orgIdentifier(builderFactory.getContext().getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getContext().getProjectParams().getProjectIdentifier())
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(appDynamicsService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    String serviceInstanceMetricPath =
        appDynamicsService.getServiceInstanceMetricPath(builderFactory.getContext().getProjectParams(),
            connectorIdentifier, "appName", "baseFolder", "tier", "metricPath", "tracingId");

    assertThat(serviceInstanceMetricPath).isEqualTo("Individual Nodes|*|metricPath");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetCompleteServiceInstanceMetricPath() throws IOException, IllegalAccessException {
    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/appd/appd_file_structure_dsl_sample_output.json"),
        Charsets.UTF_8);
    JsonUtils.asObject(textLoad, OnboardingResponseDTO.class);

    DataCollectionRequest request = AppDynamicFetchFileStructureRequest.builder()
                                        .appName("appName")
                                        .path("baseFolder|tier")
                                        .type(DataCollectionRequestType.APPDYNAMICS_FETCH_METRIC_STRUCTURE)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(connectorIdentifier)
            .accountId(builderFactory.getContext().getProjectParams().getAccountIdentifier())
            .tracingId("tracingId")
            .orgIdentifier(builderFactory.getContext().getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getContext().getProjectParams().getProjectIdentifier())
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(appDynamicsService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    String serviceInstanceMetricPath =
        appDynamicsService.getCompleteServiceInstanceMetricPath(builderFactory.getContext().getProjectParams(),
            connectorIdentifier, "appName", "baseFolder|tier|metricPath", "tracingId");

    assertThat(serviceInstanceMetricPath).isEqualTo("baseFolder|tier|Individual Nodes|*|metricPath");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetricData() throws IOException, IllegalAccessException {
    final List<MetricPackDTO> metricPacks =
        metricPackService.getMetricPacks(DataSourceType.APP_DYNAMICS, accountId, orgIdentifier, projectIdentifier);
    assertThat(metricPacks).isNotEmpty();

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/appd_metric_data_validation.json"), Charsets.UTF_8);
    JsonUtils.asObject(textLoad, OnboardingResponseDTO.class);

    DataCollectionRequest request = AppDynamicSingleMetricDataRequest.builder()
                                        .applicationName("appName")
                                        .endTime(builderFactory.getClock().instant())
                                        .startTime(builderFactory.getClock().instant().minus(Duration.ofHours(1)))
                                        .metricPath("baseFolder|tier|metricPath")
                                        .type(DataCollectionRequestType.APPDYNAMICS_GET_SINGLE_METRIC_DATA)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(connectorIdentifier)
            .accountId(builderFactory.getContext().getProjectParams().getAccountIdentifier())
            .tracingId("tracingId")
            .orgIdentifier(builderFactory.getContext().getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getContext().getProjectParams().getProjectIdentifier())
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(appDynamicsService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    AppdynamicsMetricDataResponse appdynamicsMetricDataResponse =
        appDynamicsService.getMetricData(builderFactory.getContext().getProjectParams(), connectorIdentifier, "appName",
            "baseFolder", "tier", "metricPath", "tracingId");

    assertThat(appdynamicsMetricDataResponse.getResponseStatus()).isEqualTo(ThirdPartyApiResponseStatus.SUCCESS);
    assertThat(appdynamicsMetricDataResponse.getStartTime())
        .isEqualTo(builderFactory.getClock().instant().minus(Duration.ofHours(1)).toEpochMilli());
    assertThat(appdynamicsMetricDataResponse.getEndTime())
        .isEqualTo(builderFactory.getClock().instant().toEpochMilli());
    assertThat(appdynamicsMetricDataResponse.getDataPoints().size()).isEqualTo(6);
    assertThat(appdynamicsMetricDataResponse.getDataPoints().get(0).getTimestamp()).isEqualTo(1595760660000L);
    assertThat(appdynamicsMetricDataResponse.getDataPoints().get(0).getValue()).isEqualTo(233.0);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetricDataV2() throws IOException, IllegalAccessException {
    final List<MetricPackDTO> metricPacks =
        metricPackService.getMetricPacks(DataSourceType.APP_DYNAMICS, accountId, orgIdentifier, projectIdentifier);
    assertThat(metricPacks).isNotEmpty();

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/appd_metric_data_validation.json"), Charsets.UTF_8);
    JsonUtils.asObject(textLoad, OnboardingResponseDTO.class);

    DataCollectionRequest request = AppDynamicSingleMetricDataRequest.builder()
                                        .applicationName("appName")
                                        .endTime(builderFactory.getClock().instant())
                                        .startTime(builderFactory.getClock().instant().minus(Duration.ofHours(1)))
                                        .metricPath("baseFolder|tier|metricPath")
                                        .type(DataCollectionRequestType.APPDYNAMICS_GET_SINGLE_METRIC_DATA)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(connectorIdentifier)
            .accountId(builderFactory.getContext().getProjectParams().getAccountIdentifier())
            .tracingId("tracingId")
            .orgIdentifier(builderFactory.getContext().getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getContext().getProjectParams().getProjectIdentifier())
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(appDynamicsService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    AppdynamicsMetricDataResponse appdynamicsMetricDataResponse =
        appDynamicsService.getMetricDataV2(builderFactory.getContext().getProjectParams(), connectorIdentifier,
            "appName", "baseFolder|tier|metricPath", "tracingId");

    assertThat(appdynamicsMetricDataResponse.getResponseStatus()).isEqualTo(ThirdPartyApiResponseStatus.SUCCESS);
    assertThat(appdynamicsMetricDataResponse.getStartTime())
        .isEqualTo(builderFactory.getClock().instant().minus(Duration.ofHours(1)).toEpochMilli());
    assertThat(appdynamicsMetricDataResponse.getEndTime())
        .isEqualTo(builderFactory.getClock().instant().toEpochMilli());
    assertThat(appdynamicsMetricDataResponse.getDataPoints().size()).isEqualTo(6);
    assertThat(appdynamicsMetricDataResponse.getDataPoints().get(0).getTimestamp()).isEqualTo(1595760660000L);
    assertThat(appdynamicsMetricDataResponse.getDataPoints().get(0).getValue()).isEqualTo(233.0);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetricData_empty() throws IOException, IllegalAccessException {
    final List<MetricPackDTO> metricPacks =
        metricPackService.getMetricPacks(DataSourceType.APP_DYNAMICS, accountId, orgIdentifier, projectIdentifier);
    assertThat(metricPacks).isNotEmpty();

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/empty_timeseries_dsl_response.json"), Charsets.UTF_8);
    JsonUtils.asObject(textLoad, OnboardingResponseDTO.class);

    DataCollectionRequest request = AppDynamicSingleMetricDataRequest.builder()
                                        .applicationName("appName")
                                        .endTime(builderFactory.getClock().instant())
                                        .startTime(builderFactory.getClock().instant().minus(Duration.ofHours(1)))
                                        .metricPath("baseFolder|tier|metricPath")
                                        .type(DataCollectionRequestType.APPDYNAMICS_GET_SINGLE_METRIC_DATA)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(connectorIdentifier)
            .accountId(builderFactory.getContext().getProjectParams().getAccountIdentifier())
            .tracingId("tracingId")
            .orgIdentifier(builderFactory.getContext().getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getContext().getProjectParams().getProjectIdentifier())
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(appDynamicsService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    AppdynamicsMetricDataResponse appdynamicsMetricDataResponse =
        appDynamicsService.getMetricData(builderFactory.getContext().getProjectParams(), connectorIdentifier, "appName",
            "baseFolder", "tier", "metricPath", "tracingId");

    assertThat(appdynamicsMetricDataResponse.getResponseStatus()).isEqualTo(ThirdPartyApiResponseStatus.NO_DATA);
    assertThat(appdynamicsMetricDataResponse.getStartTime())
        .isEqualTo(builderFactory.getClock().instant().minus(Duration.ofHours(1)).toEpochMilli());
    assertThat(appdynamicsMetricDataResponse.getEndTime())
        .isEqualTo(builderFactory.getClock().instant().toEpochMilli());
  }
}

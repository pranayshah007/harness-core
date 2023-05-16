/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.ThirdPartyApiResponseStatus;
import io.harness.cvng.beans.appd.AppDynamicFetchFileStructureRequest;
import io.harness.cvng.beans.appd.AppDynamicSingleMetricDataRequest;
import io.harness.cvng.beans.appd.AppDynamicsFileDefinition;
import io.harness.cvng.beans.appd.AppDynamicsFileDefinition.FileType;
import io.harness.cvng.beans.appd.AppdynamicsMetricDataResponse;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.services.api.AppDynamicsService;
import io.harness.cvng.core.services.api.AppDynamicsServiceimplTest;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.ng.core.CorrelationContext;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.time.Duration;
import java.util.List;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AppDynamicsResourceTest extends CvNextGenTestBase {
  private static AppDynamicsResource appDynamicsResource = new AppDynamicsResource();

  @Inject private Injector injector;
  @Inject private MetricPackService metricPackService;
  @Inject AppDynamicsService appDynamicsService;
  BuilderFactory builderFactory = BuilderFactory.getDefault();

  @ClassRule
  public static final ResourceTestRule RESOURCES = ResourceTestRule.builder().addResource(appDynamicsResource).build();

  private String connectorIdentifier;

  @SneakyThrows
  @Before
  public void setup() {
    injector.injectMembers(appDynamicsResource);
    connectorIdentifier = generateUuid();
    FieldUtils.writeField(appDynamicsService, "clock", builderFactory.getClock(), true);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetBaseFolders_queryParamValidationCheck() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/appdynamics/base-folders")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(400);
    String responseJson = response.readEntity(String.class);
    assertThat(responseJson).contains("{\"field\":\"appName\",\"message\":\"must not be null\"}");
    assertThat(responseJson).contains("{\"field\":\"connectorIdentifier\",\"message\":\"must not be null\"}");
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetricStructure_queryParamValidationCheck() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/appdynamics/metric-structure")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(400);
    String responseJson = response.readEntity(String.class);
    assertThat(responseJson).contains("{\"field\":\"appName\",\"message\":\"must not be null\"}");
    assertThat(responseJson).contains("{\"field\":\"connectorIdentifier\",\"message\":\"must not be null\"}");
    assertThat(responseJson).contains("{\"field\":\"tier\",\"message\":\"must not be null\"}");
    assertThat(responseJson).contains("{\"field\":\"baseFolder\",\"message\":\"must not be null\"}");
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetricData_queryParamValidationCheck() {
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/appdynamics/metric-data")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();
    assertThat(response.getStatus()).isEqualTo(400);
    String responseJson = response.readEntity(String.class);
    assertThat(responseJson).contains("{\"field\":\"appName\",\"message\":\"must not be null\"}");
    assertThat(responseJson).contains("{\"field\":\"connectorIdentifier\",\"message\":\"must not be null\"}");
    assertThat(responseJson).contains("{\"field\":\"tier\",\"message\":\"must not be null\"}");
    assertThat(responseJson).contains("{\"field\":\"baseFolder\",\"message\":\"must not be null\"}");
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetBaseFolders() {
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

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/appdynamics/base-folders")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("connectorIdentifier", connectorIdentifier)
                            .queryParam("appName", "appName")
                            .queryParam("path", "path")
                            .queryParam("routingId", "tracingId")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    List<String> baseFolders = response.readEntity(new GenericType<ResponseDTO<List<String>>>() {}).getData();

    assertThat(baseFolders.size()).isEqualTo(6);
    assertThat(baseFolders.get(0)).isEqualTo("Numbers");
    assertThat(baseFolders.get(2)).isEqualTo("External Calls");
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetricStructure() {
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

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/appdynamics/metric-structure")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("connectorIdentifier", connectorIdentifier)
                            .queryParam("appName", "appName")
                            .queryParam("baseFolder", "baseFolder")
                            .queryParam("metricPath", "metricPath")
                            .queryParam("tier", "tier")
                            .queryParam("routingId", "tracingId")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    List<AppDynamicsFileDefinition> fileDefinitionList =
        response.readEntity(new GenericType<ResponseDTO<List<AppDynamicsFileDefinition>>>() {}).getData();

    assertThat(fileDefinitionList.size()).isEqualTo(16);
    assertThat(fileDefinitionList.get(0).getName()).isEqualTo("Average Async Processing Time (ms)");
    assertThat(fileDefinitionList.get(0).getType()).isEqualTo(FileType.LEAF);
    assertThat(fileDefinitionList.get(15).getName()).isEqualTo("Individual Nodes");
    assertThat(fileDefinitionList.get(15).getType()).isEqualTo(FileType.FOLDER);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetricData() {
    final List<MetricPackDTO> metricPacks =
        metricPackService.getMetricPacks(DataSourceType.APP_DYNAMICS, builderFactory.getContext().getAccountId(),
            builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
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

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/appdynamics/metric-data")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("connectorIdentifier", connectorIdentifier)
                            .queryParam("appName", "appName")
                            .queryParam("baseFolder", "baseFolder")
                            .queryParam("metricPath", "metricPath")
                            .queryParam("tier", "tier")
                            .queryParam("routingId", "tracingId")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    AppdynamicsMetricDataResponse appdynamicsMetricDataResponse =
        response.readEntity(new GenericType<ResponseDTO<AppdynamicsMetricDataResponse>>() {}).getData();

    assertThat(appdynamicsMetricDataResponse.getResponseStatus()).isEqualTo(ThirdPartyApiResponseStatus.SUCCESS);
    assertThat(appdynamicsMetricDataResponse.getStartTime())
        .isEqualTo(builderFactory.getClock().instant().minus(Duration.ofHours(1)).toEpochMilli());
    assertThat(appdynamicsMetricDataResponse.getEndTime())
        .isEqualTo(builderFactory.getClock().instant().toEpochMilli());
    assertThat(appdynamicsMetricDataResponse.getDataPoints().size()).isEqualTo(6);
    assertThat(appdynamicsMetricDataResponse.getDataPoints().get(0).getTimestamp()).isEqualTo(1595760660000L);
    assertThat(appdynamicsMetricDataResponse.getDataPoints().get(0).getValue()).isEqualTo(233.0);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetMetricDataV2() {
    final List<MetricPackDTO> metricPacks =
        metricPackService.getMetricPacks(DataSourceType.APP_DYNAMICS, builderFactory.getContext().getAccountId(),
            builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
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

    CorrelationContext.setCorrelationId("tracingId");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/appdynamics/metric-data/v2")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("connectorIdentifier", connectorIdentifier)
                            .queryParam("appName", "appName")
                            .queryParam("completeMetricPath", "baseFolder|tier|metricPath")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    AppdynamicsMetricDataResponse appdynamicsMetricDataResponse =
        response.readEntity(new GenericType<ResponseDTO<AppdynamicsMetricDataResponse>>() {}).getData();

    assertThat(appdynamicsMetricDataResponse.getResponseStatus()).isEqualTo(ThirdPartyApiResponseStatus.SUCCESS);
    assertThat(appdynamicsMetricDataResponse.getStartTime())
        .isEqualTo(builderFactory.getClock().instant().minus(Duration.ofHours(1)).toEpochMilli());
    assertThat(appdynamicsMetricDataResponse.getEndTime())
        .isEqualTo(builderFactory.getClock().instant().toEpochMilli());
    assertThat(appdynamicsMetricDataResponse.getDataPoints().size()).isEqualTo(6);
    assertThat(appdynamicsMetricDataResponse.getDataPoints().get(0).getTimestamp()).isEqualTo(1595760660000L);
    assertThat(appdynamicsMetricDataResponse.getDataPoints().get(0).getValue()).isEqualTo(233.0);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetCompleteServiceInstanceMetricPath() {
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

    CorrelationContext.setCorrelationId("tracingId");
    Response response = RESOURCES.client()
                            .target("http://localhost:9998/appdynamics/complete-service-instance-metric-path")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("connectorIdentifier", connectorIdentifier)
                            .queryParam("appName", "appName")
                            .queryParam("completeMetricPath", "baseFolder|tier|path|metricPath")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    String serviceInstanceMetricPath = response.readEntity(new GenericType<ResponseDTO<String>>() {}).getData();
    assertThat(serviceInstanceMetricPath).isEqualTo("baseFolder|tier|Individual Nodes|*|path|metricPath");
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetServiceInstanceMetricPath() {
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

    Response response = RESOURCES.client()
                            .target("http://localhost:9998/appdynamics/service-instance-metric-path")
                            .queryParam("accountId", builderFactory.getContext().getAccountId())
                            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
                            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
                            .queryParam("connectorIdentifier", connectorIdentifier)
                            .queryParam("appName", "appName")
                            .queryParam("baseFolder", "baseFolder")
                            .queryParam("metricPath", "metricPath")
                            .queryParam("tier", "tier")
                            .queryParam("routingId", "tracingId")
                            .request(MediaType.APPLICATION_JSON_TYPE)
                            .get();

    assertThat(response.getStatus()).isEqualTo(200);
    String serviceInstanceMetricPath = response.readEntity(new GenericType<ResponseDTO<String>>() {}).getData();

    assertThat(serviceInstanceMetricPath).isEqualTo("Individual Nodes|*|metricPath");
  }
}

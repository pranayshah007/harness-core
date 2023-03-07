/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.SyncDataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.sli.MetricOnboardingGraph;
import io.harness.cvng.core.beans.sli.SLIOnboardingGraphs;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.services.api.AppDynamicsServiceimplTest;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorTransformer;
import io.harness.ng.core.CorrelationContext;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.ResourceTestRule;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ServiceLevelIndicatorResourceTest extends CvNextGenTestBase {
  private static ServiceLevelIndicatorResource serviceLevelIndicatorResource = new ServiceLevelIndicatorResource();

  @Inject private Injector injector;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private MetricPackService metricPackService;
  @Inject MonitoredServiceService monitoredServiceService;
  @Inject HPersistence hPersistence;
  @Inject private ServiceLevelIndicatorEntityAndDTOTransformer serviceLevelIndicatorEntityAndDTOTransformer;
  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;

  @Mock OnboardingService onboardingService;
  BuilderFactory builderFactory = BuilderFactory.getDefault();
  @Inject Clock clock;

  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder().addResource(serviceLevelIndicatorResource).build();

  private String connectorIdentifier;
  private String monitoredServiceIdentifier;
  private OnboardingRequestDTO onboardingRequestDTO;
  private String textLoad;
  private ServiceLevelIndicatorDTO serviceLevelIndicatorDTO;

  private String healthSourceRef;
  private CVConfig cvConfig;

  @SneakyThrows
  @Before
  public void setup() {
    injector.injectMembers(serviceLevelIndicatorResource);
    connectorIdentifier = generateUuid();
    monitoredServiceIdentifier = "monitoredServiceIdentifier";
    createMonitoredService();
    String tracingId = "tracingId";
    CorrelationContext.setCorrelationId(tracingId);
    serviceLevelIndicatorDTO = builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build();
    healthSourceRef = serviceLevelIndicatorDTO.getHealthSourceRef();
    String monitoredServiceIdentifier = "monitoredServiceIdentifier";
    cvConfig = builderFactory.appDynamicsCVConfigBuilder()
                   .identifier(HealthSourceService.getNameSpacedIdentifier(
                       monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef()))
                   .build();
    hPersistence.save(cvConfig);
    metricPackService.populateDataCollectionDsl(cvConfig.getType(), ((MetricCVConfig) cvConfig).getMetricPack());

    textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/appd_metric_data_validation.json"), Charsets.UTF_8);

    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorEntityAndDTOTransformer.getEntity(builderFactory.getProjectParams(),
            serviceLevelIndicatorDTO, monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef(), true);
    DataCollectionInfo dataCollectionInfo = dataSourceTypeDataCollectionInfoMapperMap.get(cvConfig.getType())
                                                .toDataCollectionInfo(Arrays.asList(cvConfig), serviceLevelIndicator);

    Instant endTime = clock.instant().truncatedTo(ChronoUnit.MINUTES);
    Instant startTime = endTime.minus(Duration.ofDays(1));

    DataCollectionRequest request = SyncDataCollectionRequest.builder()
                                        .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
                                        .dataCollectionInfo(dataCollectionInfo)
                                        .endTime(endTime)
                                        .startTime(startTime)
                                        .build();

    onboardingRequestDTO = OnboardingRequestDTO.builder()
                               .dataCollectionRequest(request)
                               .connectorIdentifier(cvConfig.getConnectorIdentifier())
                               .accountId(builderFactory.getProjectParams().getAccountIdentifier())
                               .orgIdentifier(builderFactory.getProjectParams().getOrgIdentifier())
                               .projectIdentifier(builderFactory.getProjectParams().getProjectIdentifier())
                               .tracingId(tracingId)
                               .build();
    onboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "onboardingService", onboardingService, true);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetOnboardingGraph() throws IOException, IllegalAccessException {
    when(onboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    ObjectMapper objectMapper = new ObjectMapper();
    Response response =
        RESOURCES.client()
            .target("http://localhost:9998/monitored-service/" + monitoredServiceIdentifier + "/sli/onboarding-graphs")
            .queryParam("accountId", builderFactory.getContext().getAccountId())
            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
            .queryParam("connectorIdentifier", connectorIdentifier)
            .queryParam("appName", "appName")
            .queryParam("path", "path")
            .queryParam("routingId", "tracingId")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .post(Entity.json(objectMapper.writeValueAsString(serviceLevelIndicatorDTO)));

    assertThat(response.getStatus()).isEqualTo(200);
    SLIOnboardingGraphs sliOnboardingGraphs =
        response.readEntity(new GenericType<RestResponse<SLIOnboardingGraphs>>() {}).getResource();

    assertThat(sliOnboardingGraphs.getSliGraph().getStartTime()).isEqualTo(1595760600000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getEndTime()).isEqualTo(1595847000000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getValue()).isEqualTo(50.0);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getTimeStamp()).isEqualTo(1595760660000L);
    assertThat(sliOnboardingGraphs.getMetricGraphs()).hasSize(1);
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getMetricName())
        .isEqualTo("Calls per Minute");
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getMetricIdentifier())
        .isEqualTo("Calls per Minute");
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getDataPoints().get(0).getValue())
        .isEqualTo(343.0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetMetricGraph() throws IOException {
    when(ServiceLevelIndicatorResourceTest.this.onboardingService.getOnboardingResponse(any(), any()))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    ObjectMapper objectMapper = new ObjectMapper();
    Response response =
        RESOURCES.client()
            .target("http://localhost:9998/monitored-service/" + monitoredServiceIdentifier
                + "/sli/onboarding-metric-graphs")
            .queryParam("accountId", builderFactory.getContext().getAccountId())
            .queryParam("orgIdentifier", builderFactory.getContext().getOrgIdentifier())
            .queryParam("projectIdentifier", builderFactory.getContext().getProjectIdentifier())
            .queryParam("healthSourceRef", healthSourceRef)
            .queryParam("routingId", "tracingId")
            .request(MediaType.APPLICATION_JSON_TYPE)
            .post(Entity.json(objectMapper.writeValueAsString(Collections.singletonList("identifier"))));

    assertThat(response.getStatus()).isEqualTo(200);
    MetricOnboardingGraph metricOnboardingGraph =
        response.readEntity(new GenericType<RestResponse<MetricOnboardingGraph>>() {}).getResource();

    assertThat(metricOnboardingGraph.getMetricGraphs().size()).isEqualTo(1);

    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getStartTime()).isEqualTo(1595760600000L);
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getEndTime()).isEqualTo(1595847000000L);
    assertThat(metricOnboardingGraph.getMetricGraphs()).hasSize(1);
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getMetricName()).isEqualTo("name");
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getMetricIdentifier()).isEqualTo("identifier");
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getDataPoints().get(0).getValue())
        .isEqualTo(343.0);
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }
}

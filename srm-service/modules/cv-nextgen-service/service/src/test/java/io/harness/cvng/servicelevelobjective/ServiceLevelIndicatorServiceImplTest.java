/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import io.harness.cvng.core.beans.params.ProjectParams;
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
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.RequestBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorEntityAndDTOTransformer;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class ServiceLevelIndicatorServiceImplTest extends CvNextGenTestBase {
  BuilderFactory builderFactory;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private ServiceLevelIndicatorEntityAndDTOTransformer serviceLevelIndicatorEntityAndDTOTransformer;
  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;
  @Inject private MetricPackService metricPackService;
  @Inject HPersistence hPersistence;
  @Inject Clock clock;
  @Inject private MonitoredServiceService monitoredServiceService;

  @Mock OrchestrationService orchestrationService;
  private String monitoredServiceIdentifier;

  private Instant startTime;

  private Instant endTime;

  @Before
  @SneakyThrows
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceIdentifier = "monitoredServiceIdentifier";
    createMonitoredService();
    clock = Clock.fixed(TIME_FOR_TESTS, ZoneOffset.UTC);
    startTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS;
    FieldUtils.writeField(serviceLevelIndicatorService, "orchestrationService", orchestrationService, true);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetOnboardingGraph() throws IOException, IllegalAccessException {
    String tracingId = "tracingId";
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build();
    CVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder()
                            .identifier(HealthSourceService.getNameSpacedIdentifier(
                                monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef()))
                            .build();
    hPersistence.save(cvConfig);
    metricPackService.populateDataCollectionDsl(cvConfig.getType(), ((MetricCVConfig) cvConfig).getMetricPack());

    String textLoad = Resources.toString(
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

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(cvConfig.getConnectorIdentifier())
            .accountId(builderFactory.getProjectParams().getAccountIdentifier())
            .orgIdentifier(builderFactory.getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getProjectParams().getProjectIdentifier())
            .tracingId(tracingId)
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    SLIOnboardingGraphs sliOnboardingGraphs =
        serviceLevelIndicatorService.getOnboardingGraphs(builderFactory.getContext().getProjectParams(),
            monitoredServiceIdentifier, serviceLevelIndicatorDTO, tracingId);

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
  public void testGetMetricGraph() throws IOException, IllegalAccessException {
    String tracingId = "tracingId";
    String healthSourceRef = "healthSourceIdentifier";
    CVConfig cvConfig =
        builderFactory.appDynamicsCVConfigBuilder()
            .identifier(HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceRef))
            .build();
    hPersistence.save(cvConfig);
    metricPackService.populateDataCollectionDsl(cvConfig.getType(), ((MetricCVConfig) cvConfig).getMetricPack());

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/appd_metric_data_validation.json"), Charsets.UTF_8);

    DataCollectionInfo dataCollectionInfo =
        dataSourceTypeDataCollectionInfoMapperMap.get(cvConfig.getType())
            .toDataCollectionInfo(Arrays.asList(cvConfig), Collections.singletonList("identifier"));

    Instant endTime = clock.instant().truncatedTo(ChronoUnit.MINUTES);
    Instant startTime = endTime.minus(Duration.ofDays(1));

    DataCollectionRequest request = SyncDataCollectionRequest.builder()
                                        .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
                                        .dataCollectionInfo(dataCollectionInfo)
                                        .endTime(endTime)
                                        .startTime(startTime)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(cvConfig.getConnectorIdentifier())
            .accountId(builderFactory.getProjectParams().getAccountIdentifier())
            .orgIdentifier(builderFactory.getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getProjectParams().getProjectIdentifier())
            .tracingId(tracingId)
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    MetricOnboardingGraph metricOnboardingGraph =
        serviceLevelIndicatorService.getMetricGraphs(builderFactory.getContext().getProjectParams(),
            monitoredServiceIdentifier, healthSourceRef, null, Collections.singletonList("identifier"), tracingId);

    assertThat(metricOnboardingGraph.getMetricGraphs().size()).isEqualTo(1);

    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getStartTime()).isEqualTo(1595760600000L);
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getEndTime()).isEqualTo(1595847000000L);
    assertThat(metricOnboardingGraph.getMetricGraphs()).hasSize(1);
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getMetricName()).isEqualTo("name");
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getMetricIdentifier()).isEqualTo("identifier");
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getDataPoints().get(0).getValue())
        .isEqualTo(343.0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetMetricGraphForRatio() throws IOException, IllegalAccessException {
    String tracingId = "tracingId";
    String healthSourceRef = "healthSourceIdentifier";
    CVConfig cvConfig =
        builderFactory.appDynamicsCVConfigBuilder()
            .identifier(HealthSourceService.getNameSpacedIdentifier(monitoredServiceIdentifier, healthSourceRef))
            .build();
    hPersistence.save(cvConfig);

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/appd_metric_data_validation.json"), Charsets.UTF_8);

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(any(), any()))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    MetricOnboardingGraph metricOnboardingGraph = serviceLevelIndicatorService.getMetricGraphs(
        builderFactory.getContext().getProjectParams(), monitoredServiceIdentifier, healthSourceRef,
        RatioSLIMetricEventType.GOOD, List.of("identifier", "zero metric identifier"), tracingId);

    assertThat(metricOnboardingGraph.getMetricGraphs().size()).isEqualTo(2);

    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getStartTime()).isEqualTo(1595760600000L);
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getEndTime()).isEqualTo(1595847000000L);
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getMetricName()).isEqualTo("name");
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getMetricIdentifier()).isEqualTo("identifier");
    assertThat(metricOnboardingGraph.getMetricGraphs().get("identifier").getDataPoints().get(0).getValue())
        .isEqualTo(343.0);

    assertThat(metricOnboardingGraph.getMetricGraphs().get("zero metric identifier").getStartTime())
        .isEqualTo(1595760600000L);
    assertThat(metricOnboardingGraph.getMetricGraphs().get("zero metric identifier").getEndTime())
        .isEqualTo(1595847000000L);
    assertThat(metricOnboardingGraph.getMetricGraphs().get("zero metric identifier").getMetricName())
        .isEqualTo("zero metric");
    assertThat(metricOnboardingGraph.getMetricGraphs().get("zero metric identifier").getMetricIdentifier())
        .isEqualTo("zero metric identifier");
    assertThat(metricOnboardingGraph.getMetricGraphs().get("zero metric identifier").getDataPoints().get(0).getValue())
        .isEqualTo(0.0);

    assertThat(metricOnboardingGraph.getMetricPercentageGraph().getMetricIdentifier1()).isEqualTo("identifier");
    assertThat(metricOnboardingGraph.getMetricPercentageGraph().getMetricIdentifier2())
        .isEqualTo("zero metric identifier");
    assertThat(metricOnboardingGraph.getMetricPercentageGraph().getDataPoints().size()).isEqualTo(0);
    assertThat(metricOnboardingGraph.getMetricPercentageGraph().getStartTime()).isEqualTo(1595760600000L);
    assertThat(metricOnboardingGraph.getMetricPercentageGraph().getEndTime()).isEqualTo(1595847000000L);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetOnboardingGraph_NoDataPresent() throws IOException, IllegalAccessException {
    String tracingId = "tracingId";
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build();
    CVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder()
                            .identifier(HealthSourceService.getNameSpacedIdentifier(
                                monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef()))
                            .build();
    hPersistence.save(cvConfig);

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/empty_timeseries_dsl_response.json"), Charsets.UTF_8);

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(eq(builderFactory.getContext().getAccountId()), any()))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    SLIOnboardingGraphs sliOnboardingGraphs =
        serviceLevelIndicatorService.getOnboardingGraphs(builderFactory.getContext().getProjectParams(),
            monitoredServiceIdentifier, serviceLevelIndicatorDTO, tracingId);

    assertThat(sliOnboardingGraphs.getSliGraph().getStartTime()).isEqualTo(1595760600000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getEndTime()).isEqualTo(1595847000000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getValue()).isEqualTo(100.0);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getTimeStamp()).isEqualTo(1595760660000L);
    assertThat(sliOnboardingGraphs.getMetricGraphs()).hasSize(0);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetOnboardingGraph_ratio() throws IOException, IllegalAccessException {
    String tracingId = "tracingId";
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getRatioServiceLevelIndicatorDTOBuilder().build();
    CVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder()
                            .identifier(HealthSourceService.getNameSpacedIdentifier(
                                monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef()))
                            .build();
    hPersistence.save(cvConfig);

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/appd_metric_data_validation.json"), Charsets.UTF_8);

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(eq(builderFactory.getContext().getAccountId()), any()))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    SLIOnboardingGraphs sliOnboardingGraphs =
        serviceLevelIndicatorService.getOnboardingGraphs(builderFactory.getContext().getProjectParams(),
            monitoredServiceIdentifier, serviceLevelIndicatorDTO, tracingId);

    assertThat(sliOnboardingGraphs.getSliGraph().getStartTime()).isEqualTo(1595760600000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getEndTime()).isEqualTo(1595847000000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getValue()).isEqualTo(50.0);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getTimeStamp()).isEqualTo(1595760660000L);
    assertThat(sliOnboardingGraphs.getMetricGraphs()).hasSize(2);
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getMetricName())
        .isEqualTo("Calls per Minute");
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getMetricIdentifier())
        .isEqualTo("Calls per Minute");
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getDataPoints().get(0).getValue())
        .isEqualTo(343.0);
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Errors per Minute").getDataPoints().get(0).getValue())
        .isEqualTo(233.0);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreateThreshold_success() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createServiceLevelIndicator(SLIMetricType.THRESHOLD);
    ProjectParams projectParams = builderFactory.getProjectParams();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
            generateUuid(), monitoredServiceIdentifier, generateUuid());
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList =
        serviceLevelIndicatorService.get(projectParams, serviceLevelIndicatorIdentifiers);
    assertThat(Collections.singletonList(serviceLevelIndicatorDTO)).isEqualTo(serviceLevelIndicatorDTOList);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateThreshold_backwardCompatible_sliMissingType() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createThresholdServiceLevelIndicator_oldDTO();
    ProjectParams projectParams = builderFactory.getProjectParams();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
            generateUuid(), monitoredServiceIdentifier, generateUuid());
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList =
        serviceLevelIndicatorService.get(projectParams, serviceLevelIndicatorIdentifiers);
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());
    assertThat(serviceLevelIndicator.getSliMissingDataType()).isEqualTo(SLIMissingDataType.GOOD);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateRatio_backwardCompatible_sliMissingType() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createRatioServiceLevelIndicator_oldDTO();
    ProjectParams projectParams = builderFactory.getProjectParams();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
            generateUuid(), monitoredServiceIdentifier, generateUuid());
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList =
        serviceLevelIndicatorService.get(projectParams, serviceLevelIndicatorIdentifiers);
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.getServiceLevelIndicator(projectParams, serviceLevelIndicatorDTO.getIdentifier());
    assertThat(serviceLevelIndicator.getSliMissingDataType()).isEqualTo(SLIMissingDataType.GOOD);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreateRatio_success() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createServiceLevelIndicator(SLIMetricType.RATIO);
    ProjectParams projectParams = builderFactory.getProjectParams();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
            generateUuid(), monitoredServiceIdentifier, generateUuid());
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList =
        serviceLevelIndicatorService.get(projectParams, serviceLevelIndicatorIdentifiers);
    assertThat(Collections.singletonList(serviceLevelIndicatorDTO)).isEqualTo(serviceLevelIndicatorDTOList);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreateRequest_success() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getRequestServiceLevelIndicatorDTOBuilder().build();
    ProjectParams projectParams = builderFactory.getProjectParams();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
            generateUuid(), monitoredServiceIdentifier, generateUuid());
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList =
        serviceLevelIndicatorService.get(projectParams, serviceLevelIndicatorIdentifiers);
    assertThat(Collections.singletonList(serviceLevelIndicatorDTO)).isEqualTo(serviceLevelIndicatorDTOList);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdateWindowWithConsecutiveMinutes_success() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build();
    ProjectParams projectParams = builderFactory.getProjectParams();
    List<String> serviceLevelIndicatorIdentifiers = serviceLevelIndicatorService.create(projectParams,
        Collections.singletonList(serviceLevelIndicatorDTO), "sloId", monitoredServiceIdentifier, "healthSourceId");
    ServiceLevelIndicatorSpec serviceLevelIndicatorSpec =
        WindowBasedServiceLevelIndicatorSpec.builder()
            .sliMissingDataType(SLIMissingDataType.GOOD)
            .type(SLIMetricType.THRESHOLD)
            .spec(ThresholdSLIMetricSpec.builder()
                      .metric1("Calls per Minute")
                      .thresholdValue(500.0)
                      .thresholdType(ThresholdType.GREATER_THAN_EQUAL_TO)
                      .considerConsecutiveMinutes(5)
                      .considerAllConsecutiveMinutesFromStartAsBad(false)
                      .build())
            .build();
    serviceLevelIndicatorDTO.setSpec(serviceLevelIndicatorSpec);
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList = Collections.singletonList(serviceLevelIndicatorDTO);
    LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    List<SLIState> sliStateList =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);
    String sliId =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), serviceLevelIndicatorIdentifiers.get(0))
            .getUuid();
    createSLIRecords(sliId, sliStateList, startTime);
    serviceLevelIndicatorService.update(projectParams, serviceLevelIndicatorDTOList, "sloId",
        Collections.singletonList(serviceLevelIndicatorDTO.getIdentifier()), monitoredServiceIdentifier,
        "healthSourceId",
        TimePeriod.builder()
            .startDate(currentLocalDate.toLocalDate().minus(1, ChronoUnit.DAYS))
            .endDate(currentLocalDate.toLocalDate())
            .build(),
        TimePeriod.builder()
            .startDate(currentLocalDate.toLocalDate().minus(1, ChronoUnit.DAYS))
            .endDate(currentLocalDate.toLocalDate())
            .build());
    serviceLevelIndicatorDTOList = serviceLevelIndicatorService.get(projectParams, serviceLevelIndicatorIdentifiers);
    assertThat(
        ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTOList.get(0).getSpec()).getSliMissingDataType())
        .isEqualTo(SLIMissingDataType.GOOD);
    assertThat(
        ((ThresholdSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTOList.get(0).getSpec())
                .getSpec())
            .getConsiderConsecutiveMinutes())
        .isEqualTo(5);
    assertThat(
        ((ThresholdSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTOList.get(0).getSpec())
                .getSpec())
            .getConsiderAllConsecutiveMinutesFromStartAsBad())
        .isEqualTo(false);
    verify(orchestrationService, times(1)).queueAnalysis(any());
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdateRequest_success() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getRequestServiceLevelIndicatorDTOBuilder().build();
    ProjectParams projectParams = builderFactory.getProjectParams();
    List<String> serviceLevelIndicatorIdentifiers = serviceLevelIndicatorService.create(projectParams,
        Collections.singletonList(serviceLevelIndicatorDTO), "sloId", monitoredServiceIdentifier, "healthSourceId");
    ServiceLevelIndicatorSpec serviceLevelIndicatorSpec = RequestBasedServiceLevelIndicatorSpec.builder()
                                                              .metric1("new_metric1")
                                                              .metric2("new_metric2")
                                                              .eventType(RatioSLIMetricEventType.BAD)
                                                              .build();
    serviceLevelIndicatorDTO.setSpec(serviceLevelIndicatorSpec);
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList = Collections.singletonList(serviceLevelIndicatorDTO);
    LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    serviceLevelIndicatorService.update(projectParams, serviceLevelIndicatorDTOList, "sloId",
        Collections.singletonList(serviceLevelIndicatorDTO.getIdentifier()), monitoredServiceIdentifier,
        "healthSourceId",
        TimePeriod.builder().startDate(currentLocalDate.toLocalDate()).endDate(currentLocalDate.toLocalDate()).build(),
        TimePeriod.builder().startDate(currentLocalDate.toLocalDate()).endDate(currentLocalDate.toLocalDate()).build());
    serviceLevelIndicatorDTOList = serviceLevelIndicatorService.get(projectParams, serviceLevelIndicatorIdentifiers);
    assertThat(Collections.singletonList(serviceLevelIndicatorDTO)).isEqualTo(serviceLevelIndicatorDTOList);
    assertThat(((RequestBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTOList.get(0).getSpec()).getMetric1())
        .isEqualTo("new_metric1");
    assertThat(((RequestBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTOList.get(0).getSpec()).getMetric2())
        .isEqualTo("new_metric2");
    assertThat(((RequestBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTOList.get(0).getSpec()).getEventType())
        .isEqualTo(RatioSLIMetricEventType.BAD);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_queueAnalysis_success() throws IllegalAccessException {
    OrchestrationService orchestrationService = mock(OrchestrationService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "orchestrationService", orchestrationService, true);
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createServiceLevelIndicator(SLIMetricType.RATIO);
    ProjectParams projectParams = builderFactory.getProjectParams();
    String serviceLevelObjectiveIdentifier = generateUuid();
    String healthSourceIdentifier = generateUuid();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
            serviceLevelObjectiveIdentifier, monitoredServiceIdentifier, healthSourceIdentifier);
    List<SLIState> sliStateList =
        Arrays.asList(SLIState.GOOD, SLIState.GOOD, SLIState.BAD, SLIState.NO_DATA, SLIState.BAD);
    String sliId =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), serviceLevelIndicatorIdentifiers.get(0))
            .getUuid();
    createSLIRecords(sliId, sliStateList, startTime.minus(2, ChronoUnit.DAYS));
    updateSLI(projectParams, serviceLevelIndicatorDTO, serviceLevelObjectiveIdentifier,
        serviceLevelIndicatorIdentifiers, healthSourceIdentifier);
    verify(orchestrationService, times(1))
        .queueAnalysis(AnalysisInput.builder()
                           .verificationTaskId(sliId)
                           .startTime(startTime.minus(2, ChronoUnit.DAYS))
                           .endTime(startTime.minus(36, ChronoUnit.HOURS))
                           .build());
    verify(orchestrationService, times(1)).queueAnalysis(any());
    verify(orchestrationService, times(4)).queueAnalysisWithoutEventPublish(any(), any());
    ((RatioSLIMetricSpec) ((WindowBasedServiceLevelIndicatorSpec) serviceLevelIndicatorDTO.getSpec()).getSpec())
        .setEventType(RatioSLIMetricEventType.BAD);
    updateSLI(projectParams, serviceLevelIndicatorDTO, serviceLevelObjectiveIdentifier,
        serviceLevelIndicatorIdentifiers, healthSourceIdentifier);
    verify(orchestrationService, times(2))
        .queueAnalysis(AnalysisInput.builder()
                           .verificationTaskId(sliId)
                           .startTime(startTime.minus(2, ChronoUnit.DAYS))
                           .endTime(startTime.minus(36, ChronoUnit.HOURS))
                           .build());
    verify(orchestrationService, times(2)).queueAnalysis(any());
    verify(orchestrationService, times(8)).queueAnalysisWithoutEventPublish(any(), any());
  }

  private void updateSLI(ProjectParams projectParams, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO,
      String serviceLevelObjectiveIdentifier, List<String> serviceLevelIndicatorIdentifiers,
      String healthSourceIdentifier) {
    serviceLevelIndicatorService.update(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
        serviceLevelObjectiveIdentifier, serviceLevelIndicatorIdentifiers, monitoredServiceIdentifier,
        healthSourceIdentifier,
        TimePeriod.builder()
            .startDate(LocalDate.ofInstant(clock.instant().minus(21, ChronoUnit.DAYS), ZoneOffset.UTC))
            .endDate(LocalDate.ofInstant(clock.instant().plus(9, ChronoUnit.DAYS), ZoneOffset.UTC))
            .build(),
        TimePeriod.builder()
            .startDate(LocalDate.ofInstant(clock.instant().minus(7, ChronoUnit.DAYS), ZoneOffset.UTC))
            .endDate(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))
            .build());
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  private ServiceLevelIndicatorDTO createServiceLevelIndicator(SLIMetricType sliMetricType) {
    if (SLIMetricType.RATIO.equals(sliMetricType)) {
      return createRatioServiceLevelIndicator();
    } else {
      return createThresholdServiceLevelIndicator();
    }
  }

  private ServiceLevelIndicatorDTO createRatioServiceLevelIndicator() {
    return ServiceLevelIndicatorDTO.builder()
        .identifier("sliIndicator")
        .name("sliName")
        .type(SLIEvaluationType.WINDOW)
        .spec(WindowBasedServiceLevelIndicatorSpec.builder()
                  .sliMissingDataType(SLIMissingDataType.GOOD)
                  .type(SLIMetricType.RATIO)
                  .spec(RatioSLIMetricSpec.builder()
                            .eventType(RatioSLIMetricEventType.GOOD)
                            .thresholdValue(50.0)
                            .thresholdType(ThresholdType.GREATER_THAN)
                            .metric1("metric1")
                            .metric2("metric2")
                            .build())
                  .build())
        .build();
  }

  private ServiceLevelIndicatorDTO createThresholdServiceLevelIndicator() {
    return ServiceLevelIndicatorDTO.builder()
        .identifier("sliIndicator")
        .name("sliName")
        .type(SLIEvaluationType.WINDOW)
        .spec(WindowBasedServiceLevelIndicatorSpec.builder()
                  .sliMissingDataType(SLIMissingDataType.GOOD)
                  .type(SLIMetricType.THRESHOLD)
                  .spec(ThresholdSLIMetricSpec.builder()
                            .metric1("metric1")
                            .thresholdValue(50.0)
                            .thresholdType(ThresholdType.GREATER_THAN)
                            .build())
                  .build())
        .build();
  }

  private ServiceLevelIndicatorDTO createRatioServiceLevelIndicator_oldDTO() {
    return ServiceLevelIndicatorDTO.builder()
        .identifier("sliIndicator")
        .name("sliName")
        .type(SLIEvaluationType.WINDOW)
        .sliMissingDataType(SLIMissingDataType.GOOD)
        .spec(WindowBasedServiceLevelIndicatorSpec.builder()
                  .type(SLIMetricType.RATIO)
                  .spec(RatioSLIMetricSpec.builder()
                            .eventType(RatioSLIMetricEventType.GOOD)
                            .thresholdValue(50.0)
                            .thresholdType(ThresholdType.GREATER_THAN)
                            .metric1("metric1")
                            .metric2("metric2")
                            .build())
                  .build())
        .build();
  }

  private ServiceLevelIndicatorDTO createThresholdServiceLevelIndicator_oldDTO() {
    return ServiceLevelIndicatorDTO.builder()
        .identifier("sliIndicator")
        .name("sliName")
        .type(SLIEvaluationType.WINDOW)
        .sliMissingDataType(SLIMissingDataType.GOOD)
        .spec(WindowBasedServiceLevelIndicatorSpec.builder()
                  .type(SLIMetricType.THRESHOLD)
                  .spec(ThresholdSLIMetricSpec.builder()
                            .metric1("metric1")
                            .thresholdValue(50.0)
                            .thresholdType(ThresholdType.GREATER_THAN)
                            .build())
                  .build())
        .build();
  }

  private List<SLIRecord> createSLIRecords(String sliId, List<SLIState> states, Instant startTime) {
    List<SLIRecord> sliRecords = new ArrayList<>();
    for (int index = 0; index < states.size(); index += 1) {
      Instant instant = startTime.plus(index, ChronoUnit.MINUTES);
      SLIRecord sliRecord = SLIRecord.builder()
                                .verificationTaskId(sliId)
                                .sliId(sliId)
                                .version(0)
                                .sliState(states.get(index))
                                .runningBadCount(0)
                                .runningGoodCount(1)
                                .sliVersion(0)
                                .timestamp(instant)
                                .build();
      sliRecords.add(sliRecord);
      index++;
    }
    hPersistence.save(sliRecords);
    return sliRecords;
  }
}

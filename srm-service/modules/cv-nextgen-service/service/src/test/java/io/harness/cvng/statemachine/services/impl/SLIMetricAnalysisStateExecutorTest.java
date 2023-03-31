/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.Sources;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.downtime.beans.AllEntitiesRule;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.DowntimeType;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.beans.OnetimeDowntimeSpec;
import io.harness.cvng.downtime.services.api.DowntimeService;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.downtime.transformer.DowntimeSpecDetailsTransformer;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slotargetspec.WindowBasedServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLIConsecutiveMinutesProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.services.impl.SLIDataUnavailabilityInstancesHandlerServiceImpl;
import io.harness.cvng.servicelevelobjective.services.impl.SLOHealthIndicatorServiceImpl;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState.StateType;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.SLIMetricAnalysisState;
import io.harness.cvng.statemachine.services.api.AnalysisStateExecutor;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SLIMetricAnalysisStateExecutorTest extends CvNextGenTestBase {
  @Inject Map<StateType, AnalysisStateExecutor> stateTypeAnalysisStateExecutorMap;
  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private SLOHealthIndicatorServiceImpl sloHealthIndicatorService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Mock TimeSeriesRecordService timeSeriesRecordService;
  @Mock VerificationTaskService verificationTaskService;

  @Inject EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;

  @Inject SLIConsecutiveMinutesProcessorService sliConsecutiveMinutesProcessorService;

  @Inject DowntimeService downtimeService;
  @Mock SLIDataUnavailabilityInstancesHandlerServiceImpl sliDataUnavailabilityInstancesHandlerService;
  BuilderFactory builderFactory;
  private Instant startTime;
  private Instant endTime;
  private String verificationTaskId;
  private ServiceLevelIndicator serviceLevelIndicator;
  AnalysisStateExecutor sliMetricAnalysisStateExecutor;
  private SLIMetricAnalysisState sliMetricAnalysisState;
  ServiceLevelObjectiveV2DTO serviceLevelObjective;

  @Inject private Map<DowntimeType, DowntimeSpecDetailsTransformer> downtimeTransformerMap;

  @Inject Clock clock;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);

    builderFactory = BuilderFactory.getDefault();
    sliMetricAnalysisStateExecutor = stateTypeAnalysisStateExecutorMap.get(StateType.SLI_METRIC_ANALYSIS);
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().sources(Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    serviceLevelObjective = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    spec.setMonitoredServiceRef(monitoredServiceDTO.getIdentifier());
    serviceLevelObjective.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);
    serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec())
            .getServiceLevelIndicators()
            .get(0)
            .getIdentifier());
    verificationTaskId = serviceLevelIndicator.getUuid();
    startTime = clock.instant().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    endTime = startTime.plus(5, ChronoUnit.MINUTES);
    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();

    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "timeSeriesRecordService", timeSeriesRecordService, true);
    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "verificationTaskService", verificationTaskService, true);
    when(verificationTaskService.getSliId(any())).thenReturn(verificationTaskId);
    when(timeSeriesRecordService.getTimeSeriesRecordDTOs(any(), any(), any())).thenReturn(generateTimeSeriesRecord());
    sliMetricAnalysisState = SLIMetricAnalysisState.builder().build();
    sliMetricAnalysisState.setInputs(input);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testExecute() {
    sliMetricAnalysisState = (SLIMetricAnalysisState) sliMetricAnalysisStateExecutor.execute(sliMetricAnalysisState);
    List<SLIRecord> sliRecordList = hPersistence.createQuery(SLIRecord.class)
                                        .filter(SLIRecordKeys.sliId, serviceLevelIndicator.getUuid())
                                        .field(SLIRecordKeys.timestamp)
                                        .greaterThanOrEq(startTime)
                                        .field(SLIRecordKeys.timestamp)
                                        .lessThan(endTime)
                                        .asList();
    assertThat(sliMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sliRecordList.size()).isEqualTo(5);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(
        builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(99.94212962962963);
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteWithConsecutiveMinutes() {
    serviceLevelObjective.setIdentifier("identifier2");
    SimpleServiceLevelObjectiveSpec spec = (SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec();
    WindowBasedServiceLevelIndicatorSpec windowBasedServiceLevelIndicatorSpec =
        WindowBasedServiceLevelIndicatorSpec.builder()
            .sliMissingDataType(SLIMissingDataType.GOOD)
            .type(SLIMetricType.RATIO)
            .spec(RatioSLIMetricSpec.builder()
                      .thresholdType(ThresholdType.GREATER_THAN)
                      .thresholdValue(20.0)
                      .eventType(RatioSLIMetricEventType.GOOD)
                      .metric1("metric1")
                      .metric2("metric2")
                      .considerConsecutiveMinutes(6)
                      .considerAllConsecutiveMinutesFromStartAsBad(true)
                      .build())
            .build();
    spec.getServiceLevelIndicators().get(0).setSpec(windowBasedServiceLevelIndicatorSpec);
    spec.getServiceLevelIndicators().get(0).setIdentifier("sli_identifier");

    serviceLevelObjective.setSpec(spec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjective);
    serviceLevelIndicator = serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(),
        ((SimpleServiceLevelObjectiveSpec) serviceLevelObjective.getSpec())
            .getServiceLevelIndicators()
            .get(0)
            .getIdentifier());
    verificationTaskId = serviceLevelIndicator.getUuid();
    AnalysisInput input =
        AnalysisInput.builder().verificationTaskId(verificationTaskId).startTime(startTime).endTime(endTime).build();

    when(verificationTaskService.getSliId(any())).thenReturn(verificationTaskId);
    sliMetricAnalysisState = SLIMetricAnalysisState.builder().build();
    sliMetricAnalysisState.setInputs(input);

    sliMetricAnalysisState = (SLIMetricAnalysisState) sliMetricAnalysisStateExecutor.execute(sliMetricAnalysisState);
    List<SLIRecord> sliRecordList = hPersistence.createQuery(SLIRecord.class)
                                        .filter(SLIRecordKeys.sliId, serviceLevelIndicator.getUuid())
                                        .field(SLIRecordKeys.timestamp)
                                        .greaterThanOrEq(startTime)
                                        .field(SLIRecordKeys.timestamp)
                                        .lessThan(endTime)
                                        .order(SLIRecordKeys.timestamp)
                                        .asList();
    assertThat(sliMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sliRecordList.size()).isEqualTo(5);
    assertThat(sliRecordList.get(0).getSliState()).isEqualTo(SLIRecord.SLIState.BAD);
    assertThat(sliRecordList.get(4).getRunningGoodCount()).isEqualTo(5);
    assertThat(sliRecordList.get(4).getRunningBadCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteWithNoData() {
    when(timeSeriesRecordService.getTimeSeriesRecordDTOs(any(), any(), any())).thenReturn(new ArrayList<>());
    sliMetricAnalysisState = (SLIMetricAnalysisState) sliMetricAnalysisStateExecutor.execute(sliMetricAnalysisState);
    List<SLIRecord> sliRecordList = hPersistence.createQuery(SLIRecord.class)
                                        .filter(SLIRecordKeys.sliId, serviceLevelIndicator.getUuid())
                                        .field(SLIRecordKeys.timestamp)
                                        .greaterThanOrEq(startTime)
                                        .field(SLIRecordKeys.timestamp)
                                        .lessThan(endTime)
                                        .asList();
    assertThat(sliMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sliRecordList.size()).isEqualTo(5);
    assertThat(sliRecordList.get(0).getSliState()).isEqualTo(SLIRecord.SLIState.NO_DATA);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(
        builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(100);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteWithSkipDataBecauseOfDCFailure() throws IllegalAccessException {
    entityUnavailabilityStatusesService.create(builderFactory.getProjectParams(),
        Collections.singletonList(EntityUnavailabilityStatusesDTO.builder()
                                      .entityId(serviceLevelIndicator.getUuid())
                                      .entityType(EntityType.SLO)
                                      .startTime(startTime.getEpochSecond())
                                      .endTime(endTime.getEpochSecond())
                                      .status(EntityUnavailabilityStatus.DATA_COLLECTION_FAILED)
                                      .orgIdentifier(builderFactory.getContext().getOrgIdentifier())
                                      .projectIdentifier(builderFactory.getContext().getProjectIdentifier())
                                      .build()));
    FieldUtils.writeField(sliDataUnavailabilityInstancesHandlerService, "downtimeService", downtimeService, true);
    FieldUtils.writeField(sliDataUnavailabilityInstancesHandlerService, "entityUnavailabilityStatusesService",
        entityUnavailabilityStatusesService, true);
    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "sliDataUnavailabilityInstancesHandlerService",
        sliDataUnavailabilityInstancesHandlerService, true);
    when(timeSeriesRecordService.getTimeSeriesRecordDTOs(any(), any(), any())).thenReturn(new ArrayList<>());
    doCallRealMethod()
        .when(sliDataUnavailabilityInstancesHandlerService)
        .filterSLIRecordsToSkip(any(), any(), any(), any(), any(), any());
    sliMetricAnalysisState = (SLIMetricAnalysisState) sliMetricAnalysisStateExecutor.execute(sliMetricAnalysisState);
    List<SLIRecord> sliRecordList = hPersistence.createQuery(SLIRecord.class)
                                        .filter(SLIRecordKeys.sliId, serviceLevelIndicator.getUuid())
                                        .field(SLIRecordKeys.timestamp)
                                        .greaterThanOrEq(startTime)
                                        .field(SLIRecordKeys.timestamp)
                                        .lessThan(endTime)
                                        .asList();
    assertThat(sliMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sliRecordList.size()).isEqualTo(5);
    assertThat(sliRecordList.get(0).getSliState()).isEqualTo(SLIRecord.SLIState.SKIP_DATA);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(
        builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(100);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteWithSkipDataBecauseOfDowntime() throws IllegalAccessException {
    clock = Clock.fixed(clock.instant().minus(30, ChronoUnit.MINUTES), clock.getZone());
    FieldUtils.writeField(downtimeService, "clock", clock, true);
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.ONE_TIME), "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.RECURRING), "clock", clock, true);
    DowntimeDTO downtimeDTO = builderFactory.getOnetimeEndTimeBasedDowntimeDTO();
    downtimeDTO.getSpec().getSpec().setStartTime(startTime.minus(10, ChronoUnit.MINUTES).getEpochSecond());
    ((OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec) ((OnetimeDowntimeSpec) downtimeDTO.getSpec().getSpec()).getSpec())
        .setEndTime(endTime.plus(10, ChronoUnit.MINUTES).getEpochSecond());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);
    clock = CVNGTestConstants.FIXED_TIME_FOR_TESTS;
    FieldUtils.writeField(sliDataUnavailabilityInstancesHandlerService, "downtimeService", downtimeService, true);
    FieldUtils.writeField(sliDataUnavailabilityInstancesHandlerService, "entityUnavailabilityStatusesService",
        entityUnavailabilityStatusesService, true);
    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "sliDataUnavailabilityInstancesHandlerService",
        sliDataUnavailabilityInstancesHandlerService, true);
    when(timeSeriesRecordService.getTimeSeriesRecordDTOs(any(), any(), any())).thenReturn(new ArrayList<>());
    doCallRealMethod()
        .when(sliDataUnavailabilityInstancesHandlerService)
        .filterSLIRecordsToSkip(any(), any(), any(), any(), any(), any());
    sliMetricAnalysisState = (SLIMetricAnalysisState) sliMetricAnalysisStateExecutor.execute(sliMetricAnalysisState);
    List<SLIRecord> sliRecordList = hPersistence.createQuery(SLIRecord.class)
                                        .filter(SLIRecordKeys.sliId, serviceLevelIndicator.getUuid())
                                        .field(SLIRecordKeys.timestamp)
                                        .greaterThanOrEq(startTime)
                                        .field(SLIRecordKeys.timestamp)
                                        .lessThan(endTime)
                                        .asList();
    assertThat(sliMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sliRecordList.size()).isEqualTo(5);
    assertThat(sliRecordList.get(0).getSliState()).isEqualTo(SLIRecord.SLIState.SKIP_DATA);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(
        builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(100);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteWithNoImpactBecauseOfDowntimeDisabled() throws IllegalAccessException {
    DowntimeDTO downtimeDTO = builderFactory.getOnetimeEndTimeBasedDowntimeDTO();
    downtimeDTO.setEnabled(false);
    downtimeDTO.getSpec().getSpec().setStartTime(startTime.minus(10, ChronoUnit.MINUTES).getEpochSecond());
    ((OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec) ((OnetimeDowntimeSpec) downtimeDTO.getSpec().getSpec()).getSpec())
        .setEndTime(endTime.plus(10, ChronoUnit.MINUTES).getEpochSecond());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);
    FieldUtils.writeField(sliDataUnavailabilityInstancesHandlerService, "downtimeService", downtimeService, true);
    FieldUtils.writeField(sliDataUnavailabilityInstancesHandlerService, "entityUnavailabilityStatusesService",
        entityUnavailabilityStatusesService, true);
    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "sliDataUnavailabilityInstancesHandlerService",
        sliDataUnavailabilityInstancesHandlerService, true);
    when(timeSeriesRecordService.getTimeSeriesRecordDTOs(any(), any(), any())).thenReturn(generateTimeSeriesRecord());
    doCallRealMethod()
        .when(sliDataUnavailabilityInstancesHandlerService)
        .filterSLIRecordsToSkip(any(), any(), any(), any(), any(), any());
    sliMetricAnalysisState = (SLIMetricAnalysisState) sliMetricAnalysisStateExecutor.execute(sliMetricAnalysisState);
    List<SLIRecord> sliRecordList = hPersistence.createQuery(SLIRecord.class)
                                        .filter(SLIRecordKeys.sliId, serviceLevelIndicator.getUuid())
                                        .field(SLIRecordKeys.timestamp)
                                        .greaterThanOrEq(startTime)
                                        .field(SLIRecordKeys.timestamp)
                                        .lessThan(endTime)
                                        .asList();
    assertThat(sliMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sliRecordList.get(0).getSliState()).isEqualTo(SLIRecord.SLIState.BAD);
    assertThat(sliRecordList.size()).isEqualTo(5);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(
        builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(99.94212962962963);
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testExecuteWithSkipDataBecauseOfDowntimeWithALLMonitoredServices() throws IllegalAccessException {
    clock = Clock.fixed(clock.instant().minus(30, ChronoUnit.MINUTES), clock.getZone());
    FieldUtils.writeField(downtimeService, "clock", clock, true);
    FieldUtils.writeField(entityUnavailabilityStatusesService, "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.ONE_TIME), "clock", clock, true);
    FieldUtils.writeField(downtimeTransformerMap.get(DowntimeType.RECURRING), "clock", clock, true);
    DowntimeDTO downtimeDTO = builderFactory.getOnetimeEndTimeBasedDowntimeDTO();
    downtimeDTO.setEntitiesRule(AllEntitiesRule.builder().build());
    downtimeDTO.getSpec().getSpec().setStartTime(startTime.minus(10, ChronoUnit.MINUTES).getEpochSecond());
    ((OnetimeDowntimeSpec.OnetimeEndTimeBasedSpec) ((OnetimeDowntimeSpec) downtimeDTO.getSpec().getSpec()).getSpec())
        .setEndTime(endTime.plus(10, ChronoUnit.MINUTES).getEpochSecond());
    downtimeService.create(builderFactory.getProjectParams(), downtimeDTO);
    clock = CVNGTestConstants.FIXED_TIME_FOR_TESTS;
    FieldUtils.writeField(sliDataUnavailabilityInstancesHandlerService, "downtimeService", downtimeService, true);
    FieldUtils.writeField(sliDataUnavailabilityInstancesHandlerService, "entityUnavailabilityStatusesService",
        entityUnavailabilityStatusesService, true);
    FieldUtils.writeField(sliMetricAnalysisStateExecutor, "sliDataUnavailabilityInstancesHandlerService",
        sliDataUnavailabilityInstancesHandlerService, true);
    when(timeSeriesRecordService.getTimeSeriesRecordDTOs(any(), any(), any())).thenReturn(new ArrayList<>());
    doCallRealMethod()
        .when(sliDataUnavailabilityInstancesHandlerService)
        .filterSLIRecordsToSkip(any(), any(), any(), any(), any(), any());
    sliMetricAnalysisState = (SLIMetricAnalysisState) sliMetricAnalysisStateExecutor.execute(sliMetricAnalysisState);
    List<SLIRecord> sliRecordList = hPersistence.createQuery(SLIRecord.class)
                                        .filter(SLIRecordKeys.sliId, serviceLevelIndicator.getUuid())
                                        .field(SLIRecordKeys.timestamp)
                                        .greaterThanOrEq(startTime)
                                        .field(SLIRecordKeys.timestamp)
                                        .lessThan(endTime)
                                        .asList();
    assertThat(sliMetricAnalysisState.getStatus().name()).isEqualTo(AnalysisStatus.SUCCESS.name());
    assertThat(sliRecordList.size()).isEqualTo(5);
    assertThat(sliRecordList.get(0).getSliState()).isEqualTo(SLIRecord.SLIState.SKIP_DATA);
    SLOHealthIndicator sloHealthIndicator = sloHealthIndicatorService.getBySLOIdentifier(
        builderFactory.getProjectParams(), serviceLevelObjective.getIdentifier());
    assertThat(sloHealthIndicator.getErrorBudgetRemainingPercentage()).isEqualTo(100);
  }
  private List<TimeSeriesRecordDTO> generateTimeSeriesRecord() {
    List<TimeSeriesRecordDTO> timeSeriesDataCollectionRecordList = new ArrayList<>();
    String host = generateUuid();
    String metric1 = "metric1";
    String metric2 = "metric2";
    String metricName1 = "metricName1";
    String metricName2 = "metricName2";
    String group1 = "group1";
    String group2 = "group2";
    Double value1 = 20.0;
    Double value2 = 110.0;
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      TimeSeriesRecordDTO timeSeriesDataCollectionRecord1 =
          TimeSeriesRecordDTO.builder()
              .verificationTaskId(verificationTaskId)
              .host(host)
              .groupName(group1)
              .metricIdentifier(metric1)
              .metricName(metricName1)
              .metricValue(value1)
              .epochMinute(TimeUnit.MILLISECONDS.toMinutes(instant.toEpochMilli()))
              .build();
      timeSeriesDataCollectionRecordList.add(timeSeriesDataCollectionRecord1);
      TimeSeriesRecordDTO timeSeriesDataCollectionRecord2 =
          TimeSeriesRecordDTO.builder()
              .verificationTaskId(verificationTaskId)
              .host(host)
              .groupName(group2)
              .metricIdentifier(metric2)
              .metricName(metricName2)
              .metricValue(value2)
              .epochMinute(TimeUnit.MILLISECONDS.toMinutes(instant.toEpochMilli()))
              .build();
      timeSeriesDataCollectionRecordList.add(timeSeriesDataCollectionRecord2);
    }
    return timeSeriesDataCollectionRecordList;
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.cvng.core.services.CVNextGenConstants.MAX_NUMBER_OF_POINTS;
import static io.harness.cvng.core.services.CVNextGenConstants.SLI_RECORD_BUCKET_SIZE;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.NO_DATA;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.SKIP_DATA;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.entities.EntityDisableTime;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.EntityDisabledTimeService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordBucketService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GraphDataServiceV2ImplTest extends CvNextGenTestBase {
  @Inject GraphDataServiceV2Impl graphDataServiceV2;
  @Inject private Clock clock;
  @Inject private EntityDisabledTimeService entityDisabledTimeService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private SLIRecordBucketService sliRecordBucketService;
  private MonitoredService monitoredService;
  private String sliId;

  private String sliId2;
  private SimpleServiceLevelObjective simpleServiceLevelObjective1;

  private SimpleServiceLevelObjective simpleRequestServiceLevelObjective;
  @Inject private HPersistence hPersistence;

  private BuilderFactory builderFactory;
  private String verificationTaskId;
  private CompositeServiceLevelObjective compositeServiceLevelObjective;
  private ServiceLevelIndicator serviceLevelIndicator;

  private ServiceLevelIndicator requestServiceLevelIndicator;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    builderFactory.getContext().setOrgIdentifier("orgIdentifier");
    builderFactory.getContext().setProjectIdentifier("project");
    MonitoredServiceDTO monitoredServiceDTO1 =
        builderFactory.monitoredServiceDTOBuilder().sources(MonitoredServiceDTO.Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    ServiceLevelObjectiveV2DTO simpleRequestServiceLevelObjectiveDTO =
        builderFactory.getSimpleRequestServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleRequestServiceLevelObjectiveSpec =
        (SimpleServiceLevelObjectiveSpec) simpleRequestServiceLevelObjectiveDTO.getSpec();
    simpleRequestServiceLevelObjectiveSpec.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleRequestServiceLevelObjectiveSpec.setHealthSourceRef(generateUuid());
    simpleRequestServiceLevelObjectiveDTO.setSpec(simpleRequestServiceLevelObjectiveSpec);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleRequestServiceLevelObjectiveDTO);
    simpleRequestServiceLevelObjective = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleRequestServiceLevelObjectiveDTO.getIdentifier());

    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1.getIdentifier());

    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(MonitoredServiceDTO.Sources.builder().build())
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    monitoredService = monitoredServiceService.getMonitoredService(
        builderFactory.getProjectParams(), monitoredServiceDTO2.getIdentifier());
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2);
    SimpleServiceLevelObjective simpleServiceLevelObjective2 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2.getIdentifier());

    ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                  .weightagePercentage(25.0)
                                  .accountId(simpleServiceLevelObjective2.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    verificationTaskId = compositeServiceLevelObjective.getUuid();
    sliId = createServiceLevelIndicator();
    sliId2 = createRequestServiceLevelIndicator();
    serviceLevelIndicator = getServiceLevelIndicator(sliId);
    requestServiceLevelIndicator = getServiceLevelIndicator(sliId2);
  }

  private String createServiceLevelIndicator() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build();
    List<String> sliId = serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
        Collections.singletonList(serviceLevelIndicatorDTO), "sloIdentifier2", monitoredService.getIdentifier(), null);
    return sliId.get(0);
  }

  private String createRequestServiceLevelIndicator() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getRequestServiceLevelIndicatorDTOBuilder().build();
    List<String> sliId = serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
        Collections.singletonList(serviceLevelIndicatorDTO), "sloIdentifier2", monitoredService.getIdentifier(), null);
    return sliId.get(0);
  }

  private ServiceLevelIndicator getServiceLevelIndicator(String sliId) {
    return serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(), sliId);
  }

  private void testGraphCalculation(List<SLIState> sliStates, SLIMissingDataType sliMissingDataType,
      List<Double> expectedSLITrend, List<Double> expectedBurndown, int expectedErrorBudgetRemaining) {
    testGraphCalculation(
        sliStates, sliMissingDataType, expectedSLITrend, expectedBurndown, expectedErrorBudgetRemaining, 0, 0);
  }

  private void testGraphCalculation(List<SLIState> sliStates, List<Double> expectedSLITrend,
      List<Double> expectedBurndown, int expectedErrorBudgetRemaining) {
    testGraphCalculation(
        sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, expectedErrorBudgetRemaining);
  }

  private void createData(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordBucketService.create(sliRecordParams, serviceLevelIndicator.getUuid(), 0);
  }

  private void createData(Instant startTime, List<SLIState> sliStates, List<Long> goodCounts, List<Long> badCounts) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates, goodCounts, badCounts);
    sliRecordBucketService.create(sliRecordParams, requestServiceLevelIndicator.getUuid(), 0);
  }

  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      long goodCount = 0;
      long badCount = 0;
      long skipDataCount = 0;
      if (sliState == GOOD) {
        goodCount++;
      } else if (sliState == BAD) {
        badCount++;
      } else if (sliState == SKIP_DATA) {
        skipDataCount++;
      }
      sliRecordParams.add(SLIRecordParam.builder()
                              .sliState(sliState)
                              .timeStamp(startTime.plus(Duration.ofMinutes(i)))
                              .goodEventCount(goodCount)
                              .badEventCount(badCount)
                              .skipEventCount(skipDataCount)
                              .build());
    }
    return sliRecordParams;
  }

  private List<SLIRecordParam> getSLIRecordParam(
      Instant startTime, List<SLIState> sliStates, List<Long> goodCounts, List<Long> badCounts) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      long goodCount = goodCounts.get(i);
      long badCount = badCounts.get(i);
      sliRecordParams.add(SLIRecordParam.builder()
                              .sliState(sliState)
                              .timeStamp(startTime.plus(Duration.ofMinutes(i)))
                              .goodEventCount(goodCount)
                              .badEventCount(badCount)
                              .skipEventCount(0l)
                              .build());
    }
    return sliRecordParams;
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testNoDataCollectionAfterSLOCreation() {
    LocalDateTime currentLocalDate =
        LocalDateTime.ofInstant(clock.instant(), simpleServiceLevelObjective1.getZoneOffset());
    TimePeriod timePeriod = simpleServiceLevelObjective1.getCurrentTimeRange(currentLocalDate);
    Instant currentTimeMinute = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    SLODashboardWidget.SLOGraphData sloGraphData = graphDataServiceV2.getGraphData(simpleServiceLevelObjective1,
        timePeriod.getStartTime(simpleServiceLevelObjective1.getZoneOffset()), currentTimeMinute, 14400, null);
    assertThat(sloGraphData.isCalculatingSLI()).isFalse();
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testNoDataCollectionAfterSLOCreation_AfterTime() {
    LocalDateTime currentLocalDate =
        LocalDateTime.ofInstant(clock.instant(), simpleServiceLevelObjective1.getZoneOffset());
    Long createdTime = TIME_FOR_TESTS.minus(15, ChronoUnit.MINUTES).toEpochMilli();
    hPersistence.update(hPersistence.createQuery(ServiceLevelIndicator.class),
        hPersistence.createUpdateOperations(ServiceLevelIndicator.class)
            .set(ServiceLevelIndicatorKeys.createdAt, createdTime)
            .set(ServiceLevelIndicatorKeys.lastUpdatedAt, createdTime));

    TimePeriod timePeriod = simpleServiceLevelObjective1.getCurrentTimeRange(currentLocalDate);
    Instant currentTimeMinute = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    SLODashboardWidget.SLOGraphData sloGraphData = graphDataServiceV2.getGraphData(simpleServiceLevelObjective1,
        timePeriod.getStartTime(simpleServiceLevelObjective1.getZoneOffset()), currentTimeMinute, 14400, null);
    assertThat(sloGraphData.isCalculatingSLI()).isTrue();
    assertThat(sloGraphData.isRecalculatingSLI()).isFalse();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetGraphData_withSkipData() {
    List<SLIState> sliStates = Arrays.asList(SKIP_DATA, GOOD, BAD, SKIP_DATA, SKIP_DATA, SKIP_DATA, BAD, SKIP_DATA,
        SKIP_DATA, GOOD, BAD, SKIP_DATA, GOOD, GOOD, BAD);
    List<Double> expectedSLITrend = Lists.newArrayList(
        100.0, 100.0, 66.66, 75.0, 80.0, 83.33, 71.42, 75.0, 77.77, 80.0, 72.72, 75.0, 76.92, 78.57, 73.33);
    List<Double> expectedBurndown =
        Lists.newArrayList(100.0, 100.0, 99.0, 99.0, 99.0, 99.0, 98.0, 98.0, 98.0, 98.0, 97.0, 97.0, 97.0, 97.0, 96.0);
    testGraphCalculation(sliStates, SLIMissingDataType.BAD, expectedSLITrend, expectedBurndown, 96, 0, 0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetGraphData_customTimePerMinute() {
    List<SLIState> sliStates =
        Arrays.asList(GOOD, NO_DATA, BAD, GOOD, GOOD, NO_DATA, BAD, GOOD, GOOD, NO_DATA, BAD, GOOD, BAD, GOOD, GOOD);
    List<Double> expectedSLITrend = Lists.newArrayList(
        100.0, 100.0, 66.66, 75.0, 80.0, 83.33, 71.42, 75.0, 77.77, 80.0, 72.72, 75.0, 69.2, 71.42, 73.33);
    List<Double> expectedBurndown = Lists.newArrayList(
        100.0, 100.0, 99.0, 99.0, 99.0, 99.0, 98.0, 98.0, 98.0, 98.0, 97.0, 97.0, 96.0, 96.0, 96.0, 96.0);
    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 96, 0, 0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  @Ignore("Enable the test case, once behaviour is fully defined.")
  public void testGetGraphData_withDisabledTimes() {
    List<SLIState> sliStates = Arrays.asList(GOOD, NO_DATA, BAD, GOOD, GOOD, NO_DATA, NO_DATA, NO_DATA, NO_DATA,
        NO_DATA, NO_DATA, GOOD, NO_DATA, NO_DATA, NO_DATA);
    List<Double> expectedSLITrend = Lists.newArrayList(
        100.0, 100.0, 66.66, 75.0, 80.0, 83.33, 83.33, 83.33, 83.33, 83.33, 83.33, 85.71, 87.5, 88.88, 90.0);
    List<Double> expectedBurndown = Lists.newArrayList(100.0, 100.0, 99.0, 99.0, 99.0, 99.0, 99.0, 99.0, 99.0, 99.0,
        99.0, 99.0, 99.0, 99.0, 99.0); // Why is it not changing after 80.0, looks like our calc is going wrong
    entityDisabledTimeService.save(EntityDisableTime.builder()
                                       .entityUUID(monitoredService.getUuid())
                                       .accountId(monitoredService.getAccountId())
                                       .startTime(clock.instant().minus(Duration.ofMinutes(9)).toEpochMilli())
                                       .endTime(clock.instant().minus(Duration.ofMinutes(5)).toEpochMilli())
                                       .build());

    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 99, 0, 0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetGraphData_AllGood() {
    List<SLIState> sliStates = new ArrayList<>();
    List<Double> expectedSLITrend = new ArrayList<>();
    List<Double> expectedBurndown = new ArrayList<>();
    for (int i = 0; i < 65; i++) {
      sliStates.add(GOOD);
      expectedSLITrend.add(100.0);
      expectedBurndown.add(100.0);
    }
    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 100, 0, 0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetGraphData_customStartTimeAllGood() {
    List<SLIState> sliStates = new ArrayList<>();
    List<Double> expectedSLITrend = new ArrayList<>();
    List<Double> expectedBurndown = new ArrayList<>();
    for (int i = 0; i < 65; i++) {
      sliStates.add(GOOD);
      if (i < 61) {
        expectedSLITrend.add(100.0);
        expectedBurndown.add(100.0);
      }
    }
    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 100, 4, 0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetGraphData_customBothTimeAllGood() {
    List<SLIState> sliStates = new ArrayList<>();
    List<Double> expectedSLITrend = new ArrayList<>();
    List<Double> expectedBurndown = new ArrayList<>();
    for (int i = 0; i < 65; i++) {
      sliStates.add(GOOD);
      if (i < 57) {
        expectedSLITrend.add(100.0);
        expectedBurndown.add(100.0);
      }
    }
    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 100, 4, 9);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetGraphData_customStartAllBad() {
    List<SLIState> sliStates = new ArrayList<>();
    List<Double> expectedSLITrend = new ArrayList<>();
    List<Double> expectedBurndown = new ArrayList<>();
    for (int i = 0; i < 65; i++) {
      sliStates.add(BAD);
      if (i < 61) {
        expectedSLITrend.add(0.0);
        expectedBurndown.add(95.0 - i);
      }
    }
    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 35, 4, 0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetGraphData_noDataConsideredBad() {
    List<SLIState> sliStates = Arrays.asList(GOOD, NO_DATA, BAD, GOOD, GOOD);
    List<Double> expectedSLITrend = Lists.newArrayList(100.0, 50.0, 33.33, 50.0, 60.0);
    List<Double> expectedBurndown = Lists.newArrayList(100.0, 99.0, 98.0, 98.0, 98.0);
    testGraphCalculation(sliStates, SLIMissingDataType.BAD, expectedSLITrend, expectedBurndown, 98);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetGraphData_request() {
    List<SLIState> sliStates = Arrays.asList(GOOD, GOOD, GOOD, GOOD, GOOD);
    List<Long> goodCounts = Arrays.asList(100L, 95L, 80L, 100L, 100L);
    List<Long> badCounts = Arrays.asList(0L, 5L, 20L, 100L, 20L);
    List<Double> expectedSLITrend = Lists.newArrayList(100.0, 97.5, 91.66, 75.0, 76.61);
    List<Double> expectedBurndown = Lists.newArrayList(100.0, 87.5, 58.33, -25.0, -16.93);
    testGraphCalculation_Request(
        sliStates, goodCounts, badCounts, SLIMissingDataType.BAD, expectedSLITrend, expectedBurndown, -21, 0, 0, 124);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetGraphData_request_noDataSkipData() {
    List<SLIState> sliStates = Arrays.asList(GOOD, NO_DATA, SKIP_DATA, GOOD, GOOD);
    List<Long> goodCounts = Arrays.asList(100L, 0L, 0L, 100L, 100L);
    List<Long> badCounts = Arrays.asList(0L, 0L, 0L, 100L, 100L);
    List<Double> expectedSLITrend = Lists.newArrayList(100.0, 100.0, 100.0, 66.66, 60.0);
    List<Double> expectedBurndown = Lists.newArrayList(100.0, 100.0, 100.0, -66.66, -100.0);
    testGraphCalculation_Request(
        sliStates, goodCounts, badCounts, SLIMissingDataType.BAD, expectedSLITrend, expectedBurndown, -100, 0, 0, 100);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetGraphData_request_noCalls() {
    List<SLIState> sliStates = Arrays.asList(GOOD, GOOD, GOOD, GOOD, GOOD);
    List<Long> goodCounts = Arrays.asList(0L, 0L, 0L, 0L, 0L);
    List<Long> badCounts = Arrays.asList(0L, 0L, 0L, 0L, 0L);
    List<Double> expectedSLITrend = Lists.newArrayList(100.0, 100.0, 100.0, 100.0, 100.0);
    List<Double> expectedBurndown = Lists.newArrayList(100.0, 100.0, 100.0, 100.0, 100.0);
    testGraphCalculation_Request(
        sliStates, goodCounts, badCounts, SLIMissingDataType.BAD, expectedSLITrend, expectedBurndown, 0, 0, 0, 0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetGraphData_perMinute() {
    List<SLIState> sliStates = Arrays.asList(GOOD, NO_DATA, BAD, GOOD, GOOD, GOOD, NO_DATA, BAD, GOOD, GOOD);
    List<Double> expectedSLITrend = Lists.newArrayList(100.0, 100.0, 66.66, 75.0, 80.0, 83.3, 85.7, 0.75, 77.77, 80.0);
    List<Double> expectedBurndown = Lists.newArrayList(100.0, 100.0, 99.0, 99.0, 99.0, 99.0, 99.0, 98.0, 98.0, 98.0);
    testGraphCalculation(sliStates, expectedSLITrend, expectedBurndown, 98);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetGraphData_allBad() {
    List<SLIState> sliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD);
    List<Double> expectedSLITrend = Lists.newArrayList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    List<Double> expectedBurndown = Lists.newArrayList(99.0, 98.0, 97.0, 96.0, 95.0, 94.0, 93.0, 92.0, 91.0, 90.0);
    testGraphCalculation(sliStates, expectedSLITrend, expectedBurndown, 90);
  }

  private void testGraphCalculation(List<SLIState> sliStates, SLIMissingDataType sliMissingDataType,
      List<Double> expectedSLITrend, List<Double> expectedBurndown, int expectedErrorBudgetRemaining,
      long customMinutesStart, long customMinutesEnd) {
    Instant startTime =
        DateTimeUtils.roundDownTo1MinBoundary(clock.instant().minus(Duration.ofMinutes(sliStates.size())));
    createData(startTime.minus(Duration.ofMinutes(5)), Arrays.asList(SKIP_DATA, NO_DATA, BAD, GOOD, GOOD));
    createData(startTime, sliStates);

    Instant customStartTime = startTime.plus(Duration.ofMinutes(customMinutesStart));
    Instant customEndTime = startTime.plus(Duration.ofMinutes(sliStates.size() - customMinutesEnd + 1));
    serviceLevelIndicator.setSliMissingDataType(sliMissingDataType);
    SLODashboardWidget.SLOGraphData sloGraphData = graphDataServiceV2.getGraphDataForSimpleSLO(serviceLevelIndicator,
        startTime, startTime.plus(Duration.ofMinutes(sliStates.size() + 1)), 100,
        TimeRangeParams.builder().startTime(customStartTime).endTime(customEndTime).build(), null,
        MAX_NUMBER_OF_POINTS);
    Duration duration = Duration.between(customStartTime, customEndTime);
    if (customMinutesEnd == 0) {
      assertThat(sloGraphData.getSloPerformanceTrend()).hasSize((int) (duration.toMinutes() - 1) / 5);
      // the sliders work on the actual SLI value that means 40th min SLI represents the 39th min data,
      //  the timestamp of data means when its captured that means it's the end time of window the data is represented
    } else {
      assertThat(sloGraphData.getSloPerformanceTrend()).hasSize((int) duration.toMinutes() / 5);
    }
    List<SLODashboardWidget.Point> sloPerformanceTrend = sloGraphData.getSloPerformanceTrend();
    List<SLODashboardWidget.Point> errorBudgetBurndown = sloGraphData.getErrorBudgetBurndown();
    // the first bucket which has all points and is after selected timestamp
    long shiftedMins = 0;
    Instant adjustedActualStartTime = customStartTime;
    if (customMinutesStart != 0) {
      long adjustedFinalMins =
          (long) (Math.ceil((double) (customMinutesStart + 1) / SLI_RECORD_BUCKET_SIZE) * SLI_RECORD_BUCKET_SIZE);
      shiftedMins = adjustedFinalMins - customMinutesStart;
      adjustedActualStartTime = startTime.plus(Duration.ofMinutes(adjustedFinalMins));
    }
    for (int idx = 0; idx < sloPerformanceTrend.size(); idx++) {
      int expectedIdx = (int) ((idx + 1) * 5L - 1 + shiftedMins);
      assertThat(Instant.ofEpochMilli(sloPerformanceTrend.get(idx).getTimestamp()))
          .isEqualTo(adjustedActualStartTime.plus(Duration.ofMinutes((idx + 1) * 5L - 1)));
      assertThat(sloPerformanceTrend.get(idx).getValue()).isCloseTo(expectedSLITrend.get(expectedIdx), offset(0.01));
      assertThat(Instant.ofEpochMilli(errorBudgetBurndown.get(idx).getTimestamp()))
          .isEqualTo(adjustedActualStartTime.plus(Duration.ofMinutes((idx + 1) * 5L - 1)));
      assertThat(errorBudgetBurndown.get(idx).getValue()).isCloseTo(expectedBurndown.get(expectedIdx), offset(0.01));
    }
    assertThat(sloGraphData.getErrorBudgetRemainingPercentage())
        .isCloseTo(expectedBurndown.get((int) ((errorBudgetBurndown.size() * 5) + shiftedMins - 1)), offset(0.01));
    assertThat(sloGraphData.getErrorBudgetRemaining()).isEqualTo(expectedErrorBudgetRemaining);
    assertThat(sloGraphData.isRecalculatingSLI()).isFalse();
  }

  private void testGraphCalculation_Request(List<SLIState> sliStates, List<Long> goodCounts, List<Long> badCounts,
      SLIMissingDataType sliMissingDataType, List<Double> expectedSLITrend, List<Double> expectedBurndown,
      int expectedErrorBudgetRemaining, long customMinutesStart, long customMinutesEnd, long expectedErrorBudget) {
    Instant startTime =
        DateTimeUtils.roundDownTo1MinBoundary(clock.instant().minus(Duration.ofMinutes(sliStates.size())));
    createData(startTime.minus(Duration.ofMinutes(5)), Arrays.asList(SKIP_DATA, NO_DATA, GOOD, GOOD, GOOD),
        Arrays.asList(95L, 0L, 180L, 400L, 700L), Arrays.asList(5L, 0L, 20L, 100L, 150L));
    createData(startTime, sliStates, goodCounts, badCounts);
    serviceLevelIndicator.setSliMissingDataType(sliMissingDataType);
    Instant customStartTime = startTime.plus(Duration.ofMinutes(customMinutesStart));
    Instant customEndTime = startTime.plus(Duration.ofMinutes(sliStates.size() - customMinutesEnd + 1));
    SLODashboardWidget.SLOGraphData sloGraphData = graphDataServiceV2.getGraphDataForSimpleSLO(
        requestServiceLevelIndicator, startTime, startTime.plus(Duration.ofMinutes(sliStates.size() + 1)), 100,
        TimeRangeParams.builder().startTime(customStartTime).endTime(customEndTime).build(),
        simpleRequestServiceLevelObjective, MAX_NUMBER_OF_POINTS);
    Duration duration = Duration.between(customStartTime, customEndTime);
    if (customMinutesEnd == 0) {
      assertThat(sloGraphData.getSloPerformanceTrend()).hasSize((int) (duration.toMinutes() - 1) / 5);
    } else {
      assertThat(sloGraphData.getSloPerformanceTrend()).hasSize((int) duration.toMinutes() / 5);
    }
    List<SLODashboardWidget.Point> sloPerformanceTrend = sloGraphData.getSloPerformanceTrend();
    List<SLODashboardWidget.Point> errorBudgetBurndown = sloGraphData.getErrorBudgetBurndown();
    // the first bucket which has all points and is after selected timestamp
    long shiftedMins = 0;
    Instant adjustedActualStartTime = customStartTime;
    if (customMinutesStart != 0) {
      long adjustedFinalMins =
          (long) (Math.ceil((double) (customMinutesStart + 1) / SLI_RECORD_BUCKET_SIZE) * SLI_RECORD_BUCKET_SIZE);
      shiftedMins = adjustedFinalMins - customMinutesStart;
      adjustedActualStartTime = startTime.plus(Duration.ofMinutes(adjustedFinalMins));
    }
    for (int idx = 0; idx < sloPerformanceTrend.size(); idx++) {
      int expectedIdx = (int) ((idx + 1) * 5L - 1 + shiftedMins);
      assertThat(Instant.ofEpochMilli(sloPerformanceTrend.get(idx).getTimestamp()))
          .isEqualTo(adjustedActualStartTime.plus(Duration.ofMinutes((idx + 1) * 5L - 1)));
      assertThat(sloPerformanceTrend.get(idx).getValue()).isCloseTo(expectedSLITrend.get(expectedIdx), offset(0.01));
      assertThat(Instant.ofEpochMilli(errorBudgetBurndown.get(idx).getTimestamp()))
          .isEqualTo(adjustedActualStartTime.plus(Duration.ofMinutes((idx + 1) * 5L - 1)));
      assertThat(errorBudgetBurndown.get(idx).getValue()).isCloseTo(expectedBurndown.get(expectedIdx), offset(0.01));
    }

    assertThat(sloGraphData.getErrorBudgetRemainingPercentage())
        .isCloseTo(expectedBurndown.get((int) ((errorBudgetBurndown.size() * 5) + shiftedMins - 1)), offset(0.01));
    assertThat(sloGraphData.getErrorBudgetRemaining()).isEqualTo(expectedErrorBudgetRemaining);
    assertThat(sloGraphData.isRecalculatingSLI()).isFalse();
    assertThat(sloGraphData.getTotalErrorBudgetFromGraph()).isEqualTo(expectedErrorBudget);
  }
}

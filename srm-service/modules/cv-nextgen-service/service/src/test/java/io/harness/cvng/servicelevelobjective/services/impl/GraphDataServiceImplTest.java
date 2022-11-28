/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.NO_DATA;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
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
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GraphDataServiceImplTest extends CvNextGenTestBase {
  @Inject GraphDataServiceImpl graphDataService;
  @Inject private Clock clock;
  @Inject private EntityDisabledTimeService entityDisabledTimeService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private SLIRecordService sliRecordService;
  @Inject private HPersistence hPersistence;
  private MonitoredService monitoredService;
  private String sliId;
  private Instant startTime;
  private Instant endTime;

  private BuilderFactory builderFactory;
  private String verificationTaskId;
  private CompositeServiceLevelObjective compositeServiceLevelObjective;
  private ServiceLevelIndicator serviceLevelIndicator;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    MonitoredServiceDTO monitoredServiceDTO1 =
        builderFactory.monitoredServiceDTOBuilder().sources(MonitoredServiceDTO.Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1);
    SimpleServiceLevelObjective simpleServiceLevelObjective1 =
        (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
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
    //        monitoredService = createMonitoredService();
    sliId = createServiceLevelIndicator();
    serviceLevelIndicator = getServiceLevelIndicator();
    startTime = TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
  }

  private MonitoredService createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier("monitoredServiceIdentifier").build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    return monitoredServiceService.getMonitoredService(
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getProjectParams())
            .monitoredServiceIdentifier("monitoredServiceIdentifier")
            .build());
  }

  private String createServiceLevelIndicator() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build();
    List<String> sliId = serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
        Collections.singletonList(serviceLevelIndicatorDTO), "sloIdentifier2", monitoredService.getIdentifier(), null);
    return sliId.get(0);
  }

  private ServiceLevelIndicator getServiceLevelIndicator() {
    return serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(), sliId);
  }

  //    @Test
  //    @Owner(developers = KAMAL)
  //    @Category(UnitTests.class)
  //    public void testGetGraphData_noData() {
  //        assertThat(
  //                graphDataService
  //                        .getGraphData(serviceLevelIndicator, clock.instant(), clock.instant(), 10,
  //                        SLIMissingDataType.GOOD, 0) .getSloPerformanceTrend())
  //                .isEmpty();
  //    }
  //
  //    @Test
  //    @Owner(developers = KAMAL)
  //    @Category(UnitTests.class)
  //    public void testGetGraphData_withAllStates() {
  //        Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
  //        List<SLIRecord.SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD,
  //        BAD); createData(startTime, sliStates);
  //
  //        assertThat(graphDataService
  //                .getGraphData(serviceLevelIndicator, startTime.minus(Duration.ofHours(1)),
  //                        startTime.plus(Duration.ofMinutes(11)), 10, SLIMissingDataType.GOOD, 0)
  //                .getSloPerformanceTrend())
  //                .hasSize(6);
  //    }
  //
  //    @Test
  //    @Owner(developers = KARAN_SARASWAT)
  //    @Category(UnitTests.class)
  //    public void testGetGraphData_withAllStates_WithRangedErrorBudgetBurned() {
  //        Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(15));
  //        List<SLIRecord.SLIState> sliStates =
  //                Arrays.asList(BAD, GOOD, GOOD, BAD, NO_DATA, NO_DATA, BAD, BAD, BAD, BAD, GOOD, NO_DATA, BAD, BAD,
  //                GOOD);
  //        createData(startTime, sliStates);
  //
  //        SLODashboardWidget.SLOGraphData sloGraphData = graphDataService.getGraphData(serviceLevelIndicator,
  //                startTime.minus(Duration.ofHours(1)), startTime.plus(Duration.ofMinutes(16)), 15,
  //                SLIMissingDataType.GOOD, 0, TimeRangeParams.builder()
  //                        .startTime(startTime.plus(Duration.ofMinutes(2)))
  //                        .endTime(startTime.plus(Duration.ofMinutes(12)))
  //                        .build());
  //        assertThat(sloGraphData.getSloPerformanceTrend()).hasSize(6);
  //        assertThat(sloGraphData.getErrorBudgetBurned())
  //                .isEqualTo(5); // as sliRecord last in range timestamp is less than the filter end time
  //
  //        sloGraphData = graphDataService.getGraphData(serviceLevelIndicator, startTime.minus(Duration.ofHours(1)),
  //                startTime.plus(Duration.ofMinutes(11)), 10, SLIMissingDataType.GOOD, 0,
  //                TimeRangeParams.builder()
  //                        .startTime(startTime.plus(Duration.ofSeconds(121)))
  //                        .endTime(startTime.plus(Duration.ofSeconds(750)))
  //                        .build());
  //        assertThat(sloGraphData.getErrorBudgetBurned()).isEqualTo(5);
  //    }
  //
  //    @Test
  //    @Owner(developers = KAMAL)
  //    @Category(UnitTests.class)
  //    public void testGetGraphData_9TotalPointsWith5AsMaxValue() {
  //        Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
  //        List<SLIRecord.SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD,
  //        BAD); createData(startTime, sliStates);
  //
  //        assertThat(graphDataService
  //                .getGraphData(serviceLevelIndicator, startTime.minus(Duration.ofHours(10)),
  //                        startTime.plus(Duration.ofMinutes(1000)), 10, SLIMissingDataType.GOOD, 0)
  //                .getSloPerformanceTrend())
  //                .hasSize(6);
  //    }
  //
  //    @Test
  //    @Owner(developers = KAMAL)
  //    @Category(UnitTests.class)
  //    public void testGetGraphData_recalculation() {
  //        Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
  //        List<SLIRecord.SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD,
  //        BAD); createData(startTime, sliStates);
  //
  //        assertThat(graphDataService
  //                .getGraphData(serviceLevelIndicator, startTime, startTime.plus(Duration.ofMinutes(20)), 10,
  //                        SLIMissingDataType.GOOD, 1)
  //                .isRecalculatingSLI())
  //                .isTrue();
  //    }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_minDataWithLargeRange() {
    long startTime = 1659345900000L;
    long endTime = 1659345960000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659345980000L).build());
    Pair<Long, Long> result = graphDataService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(1);
    assertThat(result.getRight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_minDataWithSmallRange() {
    long startTime = 1659345900000L;
    long endTime = 1659345960000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659345950000L).build());
    Pair<Long, Long> result = graphDataService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(0);
    assertThat(result.getRight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_minDataWithExactRange() {
    long startTime = 1659345900000L;
    long endTime = 1659345960000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659345960000L).build());
    Pair<Long, Long> result = graphDataService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(1);
    assertThat(result.getRight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_minDataWithExtraRanges() {
    long startTime = 1659345900000L;
    long endTime = 1659345960000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659344900000L).endTime(1659345680000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659345700000L).endTime(1659345780000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659345800000L).endTime(1659345880000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659345940000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659345950000L).endTime(1659345980000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659346500000L).endTime(1659346580000L).build());
    Pair<Long, Long> result = graphDataService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(1);
    assertThat(result.getRight()).isEqualTo(5);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_withLargeRange() {
    long startTime = 1659345900000L;
    long endTime = 1659346500000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659346600000L).build());
    Pair<Long, Long> result = graphDataService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(10);
    assertThat(result.getRight()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_withSmallRange() {
    long startTime = 1659345900000L;
    long endTime = 1659346500000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659346300000L).build());
    Pair<Long, Long> result = graphDataService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(6);
    assertThat(result.getRight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_withExactRange() {
    long startTime = 1659345900000L;
    long endTime = 1659346500000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659346500000L).build());
    Pair<Long, Long> result = graphDataService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(10);
    assertThat(result.getRight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetGraphData_customTimePerMinute() {
    SLIRecordServiceImpl.MAX_NUMBER_OF_POINTS = 15;
    List<SLIRecord.SLIState> sliStates =
        Arrays.asList(GOOD, NO_DATA, BAD, GOOD, GOOD, NO_DATA, BAD, GOOD, GOOD, NO_DATA, BAD, GOOD);
    List<Double> expectedSLITrend =
        Lists.newArrayList(100.0, 100.0, 66.66, 75.0, 80.0, 83.33, 71.42, 75.0, 77.77, 80.0, 72.72, 75.0);
    List<Double> expectedBurndown =
        Lists.newArrayList(100.0, 100.0, 99.0, 99.0, 99.0, 99.0, 98.0, 98.0, 98.0, 98.0, 97.0, 97.0);
    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 97, 0, 0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetGraphData_withDisabledTimes() {
    SLIRecordServiceImpl.MAX_NUMBER_OF_POINTS = 15;
    List<SLIRecord.SLIState> sliStates =
        Arrays.asList(GOOD, NO_DATA, BAD, GOOD, GOOD, NO_DATA, NO_DATA, NO_DATA, NO_DATA, NO_DATA, NO_DATA, GOOD);
    List<Double> expectedSLITrend =
        Lists.newArrayList(100.0, 100.0, 66.66, 75.0, 80.0, 83.33, 83.33, 83.33, 83.33, 83.33, 83.33, 85.71);
    List<Double> expectedBurndown =
        Lists.newArrayList(100.0, 100.0, 99.0, 99.0, 99.0, 99.0, 99.0, 99.0, 99.0, 99.0, 99.0, 99.0);
    entityDisabledTimeService.save(EntityDisableTime.builder()
                                       .entityUUID(monitoredService.getUuid())
                                       .accountId(monitoredService.getAccountId())
                                       .startTime(clock.instant().minus(Duration.ofMinutes(6)).toEpochMilli())
                                       .endTime(clock.instant().minus(Duration.ofMinutes(2)).toEpochMilli())
                                       .build());

    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 99, 0, 0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetGraphData_AllGood() {
    SLIRecordServiceImpl.MAX_NUMBER_OF_POINTS = 100;
    List<SLIRecord.SLIState> sliStates = new ArrayList<>();
    List<Double> expectedSLITrend = new ArrayList<>();
    List<Double> expectedBurndown = new ArrayList<>();
    for (int i = 0; i < 65; i++) {
      sliStates.add(GOOD);
      if (i < 65) {
        expectedSLITrend.add(100.0);
        expectedBurndown.add(100.0);
      }
    }
    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 100, 0, 0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetGraphData_customStartTimeAllGood() {
    SLIRecordServiceImpl.MAX_NUMBER_OF_POINTS = 100;
    List<SLIRecord.SLIState> sliStates = new ArrayList<>();
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
    SLIRecordServiceImpl.MAX_NUMBER_OF_POINTS = 100;
    List<SLIRecord.SLIState> sliStates = new ArrayList<>();
    List<Double> expectedSLITrend = new ArrayList<>();
    List<Double> expectedBurndown = new ArrayList<>();
    for (int i = 0; i < 65; i++) {
      sliStates.add(GOOD);
      if (i < 57) {
        expectedSLITrend.add(100.0);
        expectedBurndown.add(100.0);
      }
    }
    testGraphCalculation(sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, 100, 4, 5);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetGraphData_customStartAllBad() {
    SLIRecordServiceImpl.MAX_NUMBER_OF_POINTS = 100;
    List<SLIRecord.SLIState> sliStates = new ArrayList<>();
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
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetGraphData_noDataConsideredBad() {
    List<SLIRecord.SLIState> sliStates = Arrays.asList(GOOD, NO_DATA, BAD, GOOD);
    List<Double> expectedSLITrend = Lists.newArrayList(100.0, 50.0, 33.33, 50.0);
    List<Double> expectedBurndown = Lists.newArrayList(100.0, 99.0, 98.0, 98.0);
    testGraphCalculation(sliStates, SLIMissingDataType.BAD, expectedSLITrend, expectedBurndown, 98);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetGraphData_perMinute() {
    List<SLIRecord.SLIState> sliStates = Arrays.asList(GOOD, NO_DATA, BAD, GOOD);
    List<Double> expectedSLITrend = Lists.newArrayList(100.0, 100.0, 66.66, 75.0);
    List<Double> expectedBurndown = Lists.newArrayList(100.0, 100.0, 99.0, 99.0);
    testGraphCalculation(sliStates, expectedSLITrend, expectedBurndown, 99);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetGraphData_allBad() {
    List<SLIRecord.SLIState> sliStates = Arrays.asList(BAD, BAD, BAD, BAD);
    List<Double> expectedSLITrend = Lists.newArrayList(0.0, 0.0, 0.0, 0.0);
    List<Double> expectedBurndown = Lists.newArrayList(99.0, 98.0, 97.0, 96.0);
    testGraphCalculation(sliStates, expectedSLITrend, expectedBurndown, 96);
  }

  private void testGraphCalculation(List<SLIRecord.SLIState> sliStates, SLIMissingDataType sliMissingDataType,
      List<Double> expectedSLITrend, List<Double> expectedBurndown, int expectedErrorBudgetRemaining,
      long customMinutesStart, long customMinutesEnd) {
    Instant startTime =
        DateTimeUtils.roundDownTo1MinBoundary(clock.instant().minus(Duration.ofMinutes(sliStates.size())));
    createData(startTime.minus(Duration.ofMinutes(4)), Arrays.asList(GOOD, NO_DATA, BAD, GOOD));
    createData(startTime, sliStates);

    Instant customStartTime = startTime.plus(Duration.ofMinutes(customMinutesStart));
    Instant customEndTime = startTime.plus(Duration.ofMinutes(sliStates.size() - customMinutesEnd + 1));

    SLODashboardWidget.SLOGraphData sloGraphData = graphDataService.getGraphData(serviceLevelIndicator, startTime,
        startTime.plus(Duration.ofMinutes(sliStates.size() + 1)), 100, sliMissingDataType, 0,
        TimeRangeParams.builder().startTime(customStartTime).endTime(customEndTime).build());
    Duration duration = Duration.between(customStartTime, customEndTime);
    if (customMinutesEnd == 0) {
      assertThat(sloGraphData.getSloPerformanceTrend()).hasSize((int) duration.toMinutes() - 1);
    } else {
      assertThat(sloGraphData.getSloPerformanceTrend()).hasSize((int) duration.toMinutes());
    }
    List<SLODashboardWidget.Point> sloPerformanceTrend = sloGraphData.getSloPerformanceTrend();
    List<SLODashboardWidget.Point> errorBudgetBurndown = sloGraphData.getErrorBudgetBurndown();

    for (int i = 1; i < expectedSLITrend.size(); i++) {
      assertThat(sloPerformanceTrend.get(i).getTimestamp())
          .isEqualTo(customStartTime.plus(Duration.ofMinutes(i)).toEpochMilli());
      assertThat(sloPerformanceTrend.get(i).getValue()).isCloseTo(expectedSLITrend.get(i), offset(0.01));
      assertThat(errorBudgetBurndown.get(i).getTimestamp())
          .isEqualTo(customStartTime.plus(Duration.ofMinutes(i)).toEpochMilli());
      assertThat(errorBudgetBurndown.get(i).getValue()).isCloseTo(expectedBurndown.get(i), offset(0.01));
    }

    assertThat(sloGraphData.getErrorBudgetRemainingPercentage())
        .isCloseTo(expectedBurndown.get(errorBudgetBurndown.size() - 1), offset(0.01));
    assertThat(sloGraphData.getErrorBudgetRemaining()).isEqualTo(expectedErrorBudgetRemaining);
    assertThat(sloGraphData.isRecalculatingSLI()).isFalse();
  }

  private void testGraphCalculation(List<SLIRecord.SLIState> sliStates, SLIMissingDataType sliMissingDataType,
      List<Double> expectedSLITrend, List<Double> expectedBurndown, int expectedErrorBudgetRemaining) {
    testGraphCalculation(
        sliStates, sliMissingDataType, expectedSLITrend, expectedBurndown, expectedErrorBudgetRemaining, 0, 0);
  }

  private void testGraphCalculation(List<SLIRecord.SLIState> sliStates, List<Double> expectedSLITrend,
      List<Double> expectedBurndown, int expectedErrorBudgetRemaining) {
    testGraphCalculation(
        sliStates, SLIMissingDataType.GOOD, expectedSLITrend, expectedBurndown, expectedErrorBudgetRemaining);
  }

  private List<CompositeSLORecord> createSLORecords(
      Instant start, Instant end, List<Double> runningGoodCount, List<Double> runningBadCount) {
    int index = 0;
    List<CompositeSLORecord> sloRecords = new ArrayList<>();
    for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                         .verificationTaskId(verificationTaskId)
                                         .sloId(compositeServiceLevelObjective.getUuid())
                                         .version(0)
                                         .runningBadCount(runningBadCount.get(index))
                                         .runningGoodCount(runningGoodCount.get(index))
                                         .sloVersion(0)
                                         .timestamp(instant)
                                         .build();
      sloRecords.add(sloRecord);
      index++;
    }
    hPersistence.save(sloRecords);
    return sloRecords;
  }

  private void createData(Instant startTime, List<SLIRecord.SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, serviceLevelIndicator.getUuid(), verificationTaskId, 0);
  }

  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIRecord.SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIRecord.SLIState sliState = sliStates.get(i);
      sliRecordParams.add(
          SLIRecordParam.builder().sliState(sliState).timeStamp(startTime.plus(Duration.ofMinutes(i))).build());
    }
    return sliRecordParams;
  }
}

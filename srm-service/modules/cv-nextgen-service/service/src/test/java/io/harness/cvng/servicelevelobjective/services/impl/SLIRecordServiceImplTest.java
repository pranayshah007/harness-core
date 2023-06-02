/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.servicelevelobjective.entities.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.NO_DATA;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.SKIP_DATA;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class SLIRecordServiceImplTest extends CvNextGenTestBase {
  @Spy @Inject private SLIRecordServiceImpl sliRecordService;
  @Inject private HPersistence hPersistence;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private MonitoredServiceService monitoredServiceService;

  private String verificationTaskId;
  private String sliId;

  private BuilderFactory builderFactory;

  private ServiceLevelIndicator serviceLevelIndicator;

  private MonitoredService monitoredService;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);
    verificationTaskId = generateUuid();
    /*sliId = generateUuid();*/
    monitoredService = createMonitoredService();
    sliId = createServiceLevelIndicator();
    serviceLevelIndicator = getServiceLevelIndicator();
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    createData(startTime, sliStates);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    createData(startTime.plus(Duration.ofMinutes(10)), sliStates);
    lastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(10);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(8);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves_requestSLI() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<Long> goodCounts = Arrays.asList(50l, 60l, 70l, 20l, 40l, 50l, 25l, 75l);
    List<Long> badCounts = Arrays.asList(10l, 10l, 20l, 10l, 20l, 10l, 20l, 10l);
    createDataForRequestSLI(startTime, goodCounts, badCounts);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(110);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(390);
    createDataForRequestSLI(startTime.plus(Duration.ofMinutes(10)), goodCounts, badCounts);
    lastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(220);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(780);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_SkipData() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, SKIP_DATA, GOOD, NO_DATA, GOOD, GOOD, BAD, SKIP_DATA, BAD, BAD);
    createData(startTime, sliStates);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(4);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(3);
    createData(startTime.plus(Duration.ofMinutes(10)), sliStates);
    lastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(8);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(6);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetSLIRecordsOfMinutes() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, SKIP_DATA, GOOD, NO_DATA, GOOD, GOOD, BAD, SKIP_DATA, BAD, BAD);
    createData(startTime, sliStates);
    List<SLIRecord> sliRecords = sliRecordService.getSLIRecordsOfMinutes(serviceLevelIndicator.getUuid(),
        List.of(startTime, startTime.plus(2, ChronoUnit.MINUTES), startTime.plus(5, ChronoUnit.MINUTES)));
    assertThat(sliRecords.size()).isEqualTo(3);
    assertThat(sliRecords.get(0).getRunningBadCount()).isEqualTo(1);
    assertThat(sliRecords.get(0).getRunningGoodCount()).isZero();
    assertThat(sliRecords.get(1).getRunningBadCount()).isEqualTo(1);
    assertThat(sliRecords.get(1).getRunningGoodCount()).isEqualTo(1);
    assertThat(sliRecords.get(2).getRunningBadCount()).isEqualTo(1);
    assertThat(sliRecords.get(2).getRunningGoodCount()).isEqualTo(3);
  }
  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap_requestSLI() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<Long> goodCounts = Arrays.asList(50l, 60l, 70l, 20l, 40l, 50l, 25l, 75l);
    List<Long> badCounts = Arrays.asList(10l, 10l, 20l, 10l, 20l, 10l, 20l, 10l);
    createDataForRequestSLI(startTime, goodCounts, badCounts);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(110);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(390);
    assertThat(lastRecord.getSliVersion()).isZero();
    goodCounts = Arrays.asList(10l, 60l, 70l, 20l, 40l, 50l, 25l, 75l);
    badCounts = Arrays.asList(10l, 20l, 20l, 10l, 20l, 10l, 20l, 10l);
    createDataForRequestSLI(startTime, goodCounts, badCounts);
    SLIRecord updatedLastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(120l);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(350l);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap_requestSLI() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<Long> goodCounts = Arrays.asList(50l, 60l, 70l, 20l, 40l, 50l, 25l, 75l);
    List<Long> badCounts = Arrays.asList(10l, 10l, 20l, 10l, 20l, 10l, 20l, 10l);
    createDataForRequestSLI(startTime, goodCounts, badCounts);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(110);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(390);
    assertThat(lastRecord.getSliVersion()).isZero();
    goodCounts = Arrays.asList(0l, 0l, 70l, 20l, 40l);
    badCounts = Arrays.asList(0l, 0l, 20l, 10l, 20l);
    createDataForRequestSLI(startTime.plus(Duration.ofMinutes(5)), goodCounts, badCounts);
    SLIRecord updatedLastRecord = sliRecordService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(120l);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(370l);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    List<SLIState> updatedSliStates = Arrays.asList(GOOD, BAD, BAD, NO_DATA, GOOD, BAD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(startTime, updatedSliStates);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(7);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(2);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    Instant updatedStartTime = Instant.parse("2020-07-27T10:55:00Z");
    List<SLIState> updatedSliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(3);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_duplicateRecords() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, NO_DATA, NO_DATA, NO_DATA, NO_DATA);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    List<SLIRecord> sliRecords = sliRecordService.getLatestCountSLIRecords(sliId, 4);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(1);
    assertThat(sliRecords.size()).isEqualTo(4);
    for (SLIRecord sliRecord : sliRecords) {
      sliRecord.setUuid(generateUuid());
      sliRecord.setSliState(BAD);
    }
    hPersistence.saveBatch(sliRecords);
    sliRecords = sliRecordService.getLatestCountSLIRecords(sliId, 50);
    assertThat(sliRecords.size()).isEqualTo(14);
    Instant updatedStartTime = Instant.parse("2020-07-27T10:55:00Z");
    List<SLIState> updatedSliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    sliRecords = sliRecordService.getSLIRecords(
        sliId, updatedStartTime.plus(4, ChronoUnit.MINUTES), updatedStartTime.plus(5, ChronoUnit.MINUTES));
    sliRecords = sliRecords.stream()
                     .sorted(Comparator.comparingLong(SLIRecord::getLastUpdatedAt).reversed())
                     .collect(Collectors.toList());
    SLIRecord updatedLastRecord = sliRecords.get(0);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(3);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_MissingRecords() {
    Instant startTime = Instant.parse("2020-07-27T10:00:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    Instant updatedStartTime = Instant.parse("2020-07-27T10:05:00Z");
    List<SLIState> updatedSliStates =
        Arrays.asList(BAD, BAD, BAD, BAD, BAD, NO_DATA, NO_DATA, NO_DATA, NO_DATA, NO_DATA);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(3);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(updatedLastRecord.getTimestamp()).isEqualTo(Instant.parse("2020-07-27T10:14:00Z"));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_NotSyncedRecords() {
    Instant startTime = Instant.parse("2020-07-27T10:00:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    Instant updatedStartTime = Instant.parse("2020-07-27T10:05:00Z");
    List<SLIState> updatedSliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD, GOOD, BAD, GOOD, BAD, GOOD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(8);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(updatedLastRecord.getTimestamp()).isEqualTo(Instant.parse("2020-07-27T10:14:00Z"));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_retryConcurrency() {
    Instant startTime = Instant.parse("2020-07-27T10:00:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    Instant endTime = sliRecordParams.get(sliRecordParams.size() - 1).getTimeStamp();
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    List<SLIRecord> firstTimeResponse = sliRecordService.getSLIRecords(lastRecord.getSliId(), startTime, endTime);
    for (SLIRecord sliRecord : firstTimeResponse) {
      sliRecord.setVersion(-1);
    }
    List<SLIRecord> secondTimeResponse = sliRecordService.getSLIRecords(lastRecord.getSliId(), startTime, endTime);
    Instant updatedStartTime = Instant.parse("2020-07-27T10:05:00Z");
    List<SLIState> updatedSliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD, GOOD, BAD, GOOD, BAD, GOOD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    when(sliRecordService.getSLIRecords(sliId, updatedStartTime,
             updatedSliRecordParams.get(updatedSliRecordParams.size() - 1).getTimeStamp().plus(1, ChronoUnit.MINUTES)))
        .thenReturn(firstTimeResponse, secondTimeResponse);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = sliRecordService.getLatestSLIRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(8);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(updatedLastRecord.getTimestamp()).isEqualTo(Instant.parse("2020-07-27T10:14:00Z"));
  }

  private void createData(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, serviceLevelIndicator.getUuid(), verificationTaskId, 0);
  }

  private void createDataForRequestSLI(Instant startTime, List<Long> goodCounts, List<Long> badCounts) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < goodCounts.size(); i++) {
      sliRecordParams.add(SLIRecordParam.builder()
                              .sliState(GOOD)
                              .timeStamp(startTime.plus(Duration.ofMinutes(i)))
                              .badEventCount(badCounts.get(i))
                              .goodEventCount(goodCounts.get(i))
                              .build());
    }
    sliRecordService.create(sliRecordParams, serviceLevelIndicator.getUuid(), verificationTaskId, 0);
  }

  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      long goodCountValue = 0;
      long badCountValue = 0;

      if (sliState == SLIState.GOOD) {
        goodCountValue = 1;
      } else if (sliState == SLIState.BAD) {
        badCountValue = 1;
      }

      sliRecordParams.add(SLIRecordParam.builder()
                              .sliState(sliState)
                              .timeStamp(startTime.plus(Duration.ofMinutes(i)))
                              .badEventCount(badCountValue)
                              .goodEventCount(goodCountValue)
                              .build());
    }
    return sliRecordParams;
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
        Collections.singletonList(serviceLevelIndicatorDTO), "slo", "monitoredServiceIdentifier", null);
    return sliId.get(0);
  }

  private ServiceLevelIndicator getServiceLevelIndicator() {
    return serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(), sliId);
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.servicelevelobjective.entities.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.NO_DATA;
import static io.harness.cvng.servicelevelobjective.entities.SLIState.SKIP_DATA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordBucketService;
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
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class SLIRecordBucketServiceImplTest extends CvNextGenTestBase {
  @Inject SLIRecordBucketService sliRecordBucketService;

  @Inject private HPersistence hPersistence;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private MonitoredServiceService monitoredServiceService;

  private String sliId;

  private BuilderFactory builderFactory;

  private ServiceLevelIndicator serviceLevelIndicator;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);
    createMonitoredService();
    sliId = createServiceLevelIndicator();
    serviceLevelIndicator = getServiceLevelIndicator();
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    createData(startTime, sliStates);
    SLIRecordBucket lastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
    createData(startTime.plus(Duration.ofMinutes(10)), sliStates);
    lastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(10);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(8);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(15, ChronoUnit.MINUTES));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves_requestSLI() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<Long> goodCounts = Arrays.asList(50l, 60l, 70l, 20l, 40l, 50l, 25l, 75l, 60l, 50l);
    List<Long> badCounts = Arrays.asList(10l, 10l, 20l, 10l, 20l, 10l, 20l, 10l, 10l, 20l);
    createDataForRequestSLI(startTime, goodCounts, badCounts);
    SLIRecordBucket lastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(140);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(500);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
    createDataForRequestSLI(startTime.plus(Duration.ofMinutes(10)), goodCounts, badCounts);
    lastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(280);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(1000);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(15, ChronoUnit.MINUTES));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_SkipData() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, SKIP_DATA, GOOD, NO_DATA, GOOD, GOOD, BAD, SKIP_DATA, BAD, BAD);
    createData(startTime, sliStates);
    SLIRecordBucket lastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(4);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(3);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
    createData(startTime.plus(Duration.ofMinutes(10)), sliStates);
    lastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(8);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(6);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(15, ChronoUnit.MINUTES));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap_requestSLI() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<Long> goodCounts = Arrays.asList(50l, 60l, 70l, 20l, 40l, 50l, 25l, 75l, 60l, 50l);
    List<Long> badCounts = Arrays.asList(10l, 10l, 20l, 10l, 20l, 10l, 20l, 10l, 10l, 20l);
    createDataForRequestSLI(startTime, goodCounts, badCounts);
    SLIRecordBucket lastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(140);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(500);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
    assertThat(lastRecord.getSliVersion()).isZero();
    goodCounts = Arrays.asList(0l, 0l, 70l, 20l, 40l, 50l, 60l, 70l, 20l, 40l);
    badCounts = Arrays.asList(0l, 0l, 20l, 10l, 20l, 10l, 10l, 20l, 10l, 20l);
    createDataForRequestSLI(startTime, goodCounts, badCounts);
    SLIRecordBucket updatedLastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(120l);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(370l);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap_requestSLI() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<Long> goodCounts = Arrays.asList(50l, 60l, 70l, 20l, 40l, 50l, 25l, 75l, 60l, 50l);
    List<Long> badCounts = Arrays.asList(10l, 10l, 20l, 10l, 20l, 10l, 20l, 10l, 10l, 20l);
    createDataForRequestSLI(startTime, goodCounts, badCounts);
    SLIRecordBucket lastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(140);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(500);
    assertThat(lastRecord.getSliVersion()).isZero();
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
    goodCounts = Arrays.asList(0l, 0l, 70l, 20l, 40l, 50l, 60l, 70l, 20l, 40l);
    badCounts = Arrays.asList(0l, 0l, 20l, 10l, 20l, 10l, 10l, 20l, 10l, 20l);
    createDataForRequestSLI(startTime.plus(Duration.ofMinutes(5)), goodCounts, badCounts);
    SLIRecordBucket updatedLastRecord = sliRecordBucketService.getLatestSLIRecord(serviceLevelIndicator.getUuid());
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(190l);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(610l);
    assertThat(updatedLastRecord.getBucketStartTime()).isEqualTo(startTime.plus(10, ChronoUnit.MINUTES));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordBucketService.create(sliRecordParams, sliId, 0);
    SLIRecordBucket lastRecord = sliRecordBucketService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
    List<SLIState> updatedSliStates = Arrays.asList(GOOD, BAD, BAD, NO_DATA, GOOD, BAD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(startTime, updatedSliStates);
    sliRecordBucketService.create(updatedSliRecordParams, sliId, 1);
    SLIRecordBucket updatedLastRecord = sliRecordBucketService.getLatestSLIRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(7);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(2);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordBucketService.create(sliRecordParams, sliId, 0);
    SLIRecordBucket lastRecord = sliRecordBucketService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
    Instant updatedStartTime = Instant.parse("2020-07-27T10:55:00Z");
    List<SLIState> updatedSliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordBucketService.create(updatedSliRecordParams, sliId, 1);
    SLIRecordBucket updatedLastRecord = sliRecordBucketService.getLatestSLIRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(3);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(updatedStartTime);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_MissingRecords() {
    Instant startTime = Instant.parse("2020-07-27T10:00:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordBucketService.create(sliRecordParams, sliId, 0);
    SLIRecordBucket lastRecord = sliRecordBucketService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(Instant.parse("2020-07-27T10:05:00Z"));
    Instant updatedStartTime = Instant.parse("2020-07-27T10:05:00Z");
    List<SLIState> updatedSliStates =
        Arrays.asList(BAD, BAD, BAD, BAD, BAD, NO_DATA, NO_DATA, NO_DATA, NO_DATA, NO_DATA);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordBucketService.create(updatedSliRecordParams, sliId, 1);
    SLIRecordBucket updatedLastRecord = sliRecordBucketService.getLatestSLIRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(3);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(updatedLastRecord.getBucketStartTime()).isEqualTo(Instant.parse("2020-07-27T10:10:00Z"));
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_NotSyncedRecords() {
    Instant startTime = Instant.parse("2020-07-27T10:00:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordBucketService.create(sliRecordParams, sliId, 0);
    SLIRecordBucket lastRecord = sliRecordBucketService.getLatestSLIRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    assertThat(lastRecord.getBucketStartTime()).isEqualTo(Instant.parse("2020-07-27T10:05:00Z"));
    Instant updatedStartTime = Instant.parse("2020-07-27T10:05:00Z");
    List<SLIState> updatedSliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD, GOOD, BAD, GOOD, BAD, GOOD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordBucketService.create(updatedSliRecordParams, sliId, 1);
    SLIRecordBucket updatedLastRecord = sliRecordBucketService.getLatestSLIRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(8);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(updatedLastRecord.getBucketStartTime()).isEqualTo(Instant.parse("2020-07-27T10:10:00Z"));
  }

  private void createData(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordBucketService.create(sliRecordParams, serviceLevelIndicator.getUuid(), 0);
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
    sliRecordBucketService.create(sliRecordParams, serviceLevelIndicator.getUuid(), 0);
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

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier("monitoredServiceIdentifier").build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
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

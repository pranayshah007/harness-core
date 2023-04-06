/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.NO_DATA;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.SKIP_DATA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLIConsecutiveMinutesProcessorService;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class SLIConsecutiveMinutesProcessorServiceImplTest extends CvNextGenTestBase {
  @Inject SLIConsecutiveMinutesProcessorService sliConsecutiveMinutesProcessorService;

  @Spy @Inject SLIRecordService sliRecordService;
  Clock clock;

  BuilderFactory builderFactory;

  ServiceLevelIndicator serviceLevelIndicator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    clock = CVNGTestConstants.FIXED_TIME_FOR_TESTS;
    serviceLevelIndicator = builderFactory.ratioServiceLevelIndicatorBuilder()
                                .considerConsecutiveMinutes(5)
                                .considerAllConsecutiveMinutesFromStartAsBad(true)
                                .build();
    FieldUtils.writeField(sliConsecutiveMinutesProcessorService, "sliRecordService", sliRecordService, true);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testProcess() {
    Instant startTime = clock.instant();
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, SKIP_DATA, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, SKIP_DATA);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    List<SLIState> prevSLIStates = Arrays.asList(GOOD, BAD, BAD, BAD, BAD);
    doReturn(getPrevSLIRecords(startTime.minus(5, ChronoUnit.MINUTES), startTime.minus(1, ChronoUnit.MINUTES),
                 prevSLIStates, serviceLevelIndicator.getUuid()))
        .when(sliRecordService)
        .getSLIRecordsWithSLIVersion(serviceLevelIndicator.getUuid(), startTime.minus(5, ChronoUnit.MINUTES),
            startTime.minus(1, ChronoUnit.MINUTES), 0);
    List<SLIRecordParam> updatedSliRecordParams =
        sliConsecutiveMinutesProcessorService.process(sliRecordParams, serviceLevelIndicator);
    assertThat(updatedSliRecordParams.size()).isEqualTo(14);
    List<SLIState> expectedSliStates =
        Arrays.asList(BAD, BAD, BAD, BAD, BAD, GOOD, SKIP_DATA, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, SKIP_DATA);
    List<Long> goodEventCount = Arrays.asList(0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 1L, 1L, 1L, 1L, 1L, 0L);
    List<Long> badEventCount = Arrays.asList(1L, 1L, 1L, 1L, 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getSliState).collect(Collectors.toList()))
        .isEqualTo(expectedSliStates);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getGoodEventCount).collect(Collectors.toList()))
        .isEqualTo(goodEventCount);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getBadEventCount).collect(Collectors.toList()))
        .isEqualTo(badEventCount);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testProcessRequestBasedSLO() {
    serviceLevelIndicator = builderFactory.requestServiceLevelIndicatorBuilder().build();
    Instant startTime = clock.instant();
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, SKIP_DATA, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, SKIP_DATA);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    List<SLIRecordParam> updatedSliRecordParams =
        sliConsecutiveMinutesProcessorService.process(sliRecordParams, serviceLevelIndicator);
    assertThat(updatedSliRecordParams.size()).isEqualTo(10);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getSliState).collect(Collectors.toList()))
        .isEqualTo(sliStates);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testProcessAllBad() {
    Instant startTime = clock.instant();
    List<SLIState> sliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    List<SLIState> prevSLIStates = Arrays.asList(GOOD, BAD, BAD, BAD, BAD);
    doReturn(getPrevSLIRecords(startTime.minus(5, ChronoUnit.MINUTES), startTime.minus(1, ChronoUnit.MINUTES),
                 prevSLIStates, serviceLevelIndicator.getUuid()))
        .when(sliRecordService)
        .getSLIRecordsWithSLIVersion(serviceLevelIndicator.getUuid(), startTime.minus(5, ChronoUnit.MINUTES),
            startTime.minus(1, ChronoUnit.MINUTES), 0);
    List<SLIRecordParam> updatedSliRecordParams =
        sliConsecutiveMinutesProcessorService.process(sliRecordParams, serviceLevelIndicator);
    assertThat(updatedSliRecordParams.size()).isEqualTo(14);
    List<SLIState> expectedSliStates =
        Arrays.asList(BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD, BAD);
    List<Long> goodEventCount = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    List<Long> badEventCount = Arrays.asList(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getSliState).collect(Collectors.toList()))
        .isEqualTo(expectedSliStates);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getGoodEventCount).collect(Collectors.toList()))
        .isEqualTo(goodEventCount);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getBadEventCount).collect(Collectors.toList()))
        .isEqualTo(badEventCount);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testProcessAllGood() {
    Instant startTime = clock.instant();
    List<SLIState> sliStates = Arrays.asList(GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    List<SLIState> prevSLIStates = Arrays.asList(GOOD, GOOD, GOOD, GOOD, GOOD);
    doReturn(getPrevSLIRecords(startTime.minus(5, ChronoUnit.MINUTES), startTime.minus(1, ChronoUnit.MINUTES),
                 prevSLIStates, serviceLevelIndicator.getUuid()))
        .when(sliRecordService)
        .getSLIRecordsWithSLIVersion(serviceLevelIndicator.getUuid(), startTime.minus(5, ChronoUnit.MINUTES),
            startTime.minus(1, ChronoUnit.MINUTES), 0);
    List<SLIRecordParam> updatedSliRecordParams =
        sliConsecutiveMinutesProcessorService.process(sliRecordParams, serviceLevelIndicator);
    assertThat(updatedSliRecordParams.size()).isEqualTo(14);
    List<SLIState> expectedSliStates =
        Arrays.asList(GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD);
    List<Long> goodEventCount = Arrays.asList(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L);
    List<Long> badEventCount = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getSliState).collect(Collectors.toList()))
        .isEqualTo(expectedSliStates);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getGoodEventCount).collect(Collectors.toList()))
        .isEqualTo(goodEventCount);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getBadEventCount).collect(Collectors.toList()))
        .isEqualTo(badEventCount);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testProcessNoPreviousRecords() {
    Instant startTime = clock.instant();
    List<SLIState> sliStates = Arrays.asList(GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    doReturn(new ArrayList<>())
        .when(sliRecordService)
        .getSLIRecordsWithSLIVersion(serviceLevelIndicator.getUuid(), startTime.minus(5, ChronoUnit.MINUTES),
            startTime.minus(1, ChronoUnit.MINUTES), 0);
    List<SLIRecordParam> updatedSliRecordParams =
        sliConsecutiveMinutesProcessorService.process(sliRecordParams, serviceLevelIndicator);
    assertThat(updatedSliRecordParams.size()).isEqualTo(10);
    List<SLIState> expectedSliStates = Arrays.asList(GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD);
    List<Long> goodEventCount = Arrays.asList(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L);
    List<Long> badEventCount = Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getSliState).collect(Collectors.toList()))
        .isEqualTo(expectedSliStates);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getGoodEventCount).collect(Collectors.toList()))
        .isEqualTo(goodEventCount);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getBadEventCount).collect(Collectors.toList()))
        .isEqualTo(badEventCount);
  }
  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testProcessOnlyLastMinuteAsBad() {
    serviceLevelIndicator = builderFactory.ratioServiceLevelIndicatorBuilder()
                                .considerConsecutiveMinutes(5)
                                .considerAllConsecutiveMinutesFromStartAsBad(false)
                                .build();
    Instant startTime = clock.instant();
    List<SLIState> sliStates = Arrays.asList(BAD, BAD, SKIP_DATA, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, SKIP_DATA);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    List<SLIState> prevSLIStates = Arrays.asList(GOOD, BAD, BAD, BAD, BAD);
    doReturn(getPrevSLIRecords(startTime.minus(5, ChronoUnit.MINUTES), startTime.minus(1, ChronoUnit.MINUTES),
                 prevSLIStates, serviceLevelIndicator.getUuid()))
        .when(sliRecordService)
        .getSLIRecordsWithSLIVersion(serviceLevelIndicator.getUuid(), startTime.minus(5, ChronoUnit.MINUTES),
            startTime.minus(1, ChronoUnit.MINUTES), 0);
    List<SLIRecordParam> updatedSliRecordParams =
        sliConsecutiveMinutesProcessorService.process(sliRecordParams, serviceLevelIndicator);
    assertThat(updatedSliRecordParams.size()).isEqualTo(14);
    List<SLIState> expectedSliStates =
        Arrays.asList(GOOD, GOOD, GOOD, GOOD, BAD, BAD, SKIP_DATA, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, SKIP_DATA);
    List<Long> goodEventCount = Arrays.asList(1L, 1L, 1L, 1L, 0L, 0L, 0L, 0L, 1L, 1L, 1L, 1L, 1L, 0L);
    List<Long> badEventCount = Arrays.asList(0L, 0L, 0L, 0L, 1L, 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getSliState).collect(Collectors.toList()))
        .isEqualTo(expectedSliStates);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getGoodEventCount).collect(Collectors.toList()))
        .isEqualTo(goodEventCount);
    assertThat(updatedSliRecordParams.stream().map(SLIRecordParam::getBadEventCount).collect(Collectors.toList()))
        .isEqualTo(badEventCount);
  }

  private List<SLIRecord> getPrevSLIRecords(
      Instant startTime, Instant endTime, List<SLIState> sliStates, String sliId) {
    int index = 0;
    List<SLIRecord> sliRecords = new ArrayList<>();
    for (Instant instant = startTime; !instant.isAfter(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      SLIRecord sliRecord = SLIRecord.builder()
                                .verificationTaskId(sliId)
                                .sliId(sliId)
                                .version(0)
                                .sliState(sliStates.get(index))
                                .runningBadCount(0)
                                .runningGoodCount(index + 1)
                                .sliVersion(0)
                                .timestamp(instant)
                                .build();
      sliRecords.add(sliRecord);
      index++;
    }
    return sliRecords;
  }
  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      long goodCount = 0;
      long badCount = 0;
      if (sliState == GOOD) {
        goodCount += 1;
      }
      if (sliState == BAD) {
        badCount += 1;
      }
      sliRecordParams.add(SLIRecordParam.builder()
                              .sliState(sliState)
                              .timeStamp(startTime.plus(Duration.ofMinutes(i)))
                              .goodEventCount(goodCount)
                              .badEventCount(badCount)
                              .build());
    }
    return sliRecordParams;
  }
}

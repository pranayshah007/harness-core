package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.NO_DATA;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.Point;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mongodb.morphia.query.Sort;

public class SLIRecordServiceImplTest extends CvNextGenTestBase {
  @Inject private SLIRecordServiceImpl sliRecordService;
  @Inject private Clock clock;
  @Inject private HPersistence hPersistence;
  @Before
  public void setup() {
    SLIRecordServiceImpl.MAX_NUMBER_OF_POINTS = 5;
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves() {
    String verificationTaskId = generateUuid();
    String sliId = generateUuid();
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    createData(verificationTaskId, sliId, startTime, sliStates);
    SLIRecord lastRecord = getLastRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    createData(verificationTaskId, sliId, startTime.plus(Duration.ofMinutes(10)), sliStates);
    lastRecord = getLastRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(10);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(8);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetGraphData_noData() {
    assertThat(
        sliRecordService.getGraphData(generateUuid(), clock.instant(), clock.instant(), 10, SLIMissingDataType.GOOD)
            .getSloPerformanceTrend())
        .isEmpty();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetGraphData_withAllStates() {
    String verificationTaskId = generateUuid();
    String sliId = generateUuid();
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    createData(verificationTaskId, sliId, startTime, sliStates);

    assertThat(sliRecordService
                   .getGraphData(sliId, startTime, startTime.plus(Duration.ofMinutes(11)), 10, SLIMissingDataType.GOOD)
                   .getSloPerformanceTrend())
        .hasSize(5);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetGraphData_9TotalPointsWith5AsMaxValue() {
    String verificationTaskId = generateUuid();
    String sliId = generateUuid();
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(9));
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD);
    createData(verificationTaskId, sliId, startTime, sliStates);

    assertThat(sliRecordService
                   .getGraphData(sliId, startTime, startTime.plus(Duration.ofMinutes(10)), 10, SLIMissingDataType.GOOD)
                   .getSloPerformanceTrend())
        .hasSize(5);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetGraphData_perMinute() {
    String verificationTaskId = generateUuid();
    String sliId = generateUuid();
    Instant startTime = DateTimeUtils.roundDownTo1MinBoundary(clock.instant().minus(Duration.ofMinutes(4)));
    List<SLIState> sliStates = Arrays.asList(GOOD, NO_DATA, BAD, GOOD);
    createData(verificationTaskId, sliId, startTime, sliStates);
    SLOGraphData sloGraphData = sliRecordService.getGraphData(
        sliId, startTime, startTime.plus(Duration.ofMinutes(5)), 100, SLIMissingDataType.GOOD);
    List<Double> expectedSLITrend = Lists.newArrayList(100.0, 100.0, 66.66, 75.0);
    List<Double> expectedBurndown = Lists.newArrayList(100.0, 100.0, 99.0, 99.0);
    assertThat(sloGraphData.getSloPerformanceTrend()).hasSize(4);
    List<Point> sloPerformanceTrend = sloGraphData.getSloPerformanceTrend();
    List<Point> errorBudgetBurndown = sloGraphData.getErrorBudgetBurndown();
    for (int i = 0; i < expectedSLITrend.size(); i++) {
      assertThat(sloPerformanceTrend.get(i).getTimestamp())
          .isEqualTo(startTime.plus(Duration.ofMinutes(i)).toEpochMilli());
      assertThat(sloPerformanceTrend.get(i).getValue()).isCloseTo(expectedSLITrend.get(i), offset(0.01));
      assertThat(errorBudgetBurndown.get(i).getTimestamp())
          .isEqualTo(startTime.plus(Duration.ofMinutes(i)).toEpochMilli());
      assertThat(errorBudgetBurndown.get(i).getValue()).isCloseTo(expectedBurndown.get(i), offset(0.01));
    }
    assertThat(sloGraphData.getErrorBudgetRemainingPercentage()).isCloseTo(99, offset(0.01));
    assertThat(sloGraphData.getErrorBudgetRemaining()).isEqualTo(99);
  }

  private void createData(String verificationTaskId, String sliId, Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      sliRecordParams.add(
          SLIRecordParam.builder().sliState(sliState).timeStamp(startTime.plus(Duration.ofMinutes(i))).build());
    }
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId);
  }
  private SLIRecord getLastRecord(String sliId) {
    return hPersistence.createQuery(SLIRecord.class)
        .filter(SLIRecordKeys.sliId, sliId)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .get();
  }
}
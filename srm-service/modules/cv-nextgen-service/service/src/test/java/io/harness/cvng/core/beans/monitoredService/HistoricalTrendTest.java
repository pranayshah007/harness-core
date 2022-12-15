/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import static io.harness.rule.OwnerRule.KANHAIYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.rule.Owner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class HistoricalTrendTest extends CategoryTest {
  private Clock clock;

  @Before
  public void setUp() throws Exception {
    clock = Clock.fixed(Instant.parse("2020-04-22T10:00:00Z"), ZoneOffset.UTC);
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testReduceHealthScoreDataToXPoints_preconditionsCheck() {
    HistoricalTrend historicalTrend =
        HistoricalTrend.builder().size(2).trendStartTime(clock.instant()).trendEndTime(clock.instant()).build();
    historicalTrend.setHealthScores(null);
    assertThatThrownBy(() -> historicalTrend.reduceHealthScoreDataToXPoints(2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("HealthScores cannot be null");

    HistoricalTrend historicalTrendOne =
        HistoricalTrend.builder().size(2).trendStartTime(clock.instant()).trendEndTime(clock.instant()).build();
    historicalTrendOne.setHealthScores(new ArrayList<>());
    assertThatThrownBy(() -> historicalTrendOne.reduceHealthScoreDataToXPoints(2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format("cannot group historical trend with size %s to %s points", 0, 2));

    HistoricalTrend historicalTrendTwo =
        HistoricalTrend.builder().size(7).trendStartTime(clock.instant()).trendEndTime(clock.instant()).build();
    assertThatThrownBy(() -> historicalTrendTwo.reduceHealthScoreDataToXPoints(2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format("cannot group historical trend with size %s to %s points", 7, 2));
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testReduceHealthScoreDataToXPoints_grouping8healthScoreIntoGroupsOf2() {
    HistoricalTrend historicalTrend = HistoricalTrend.builder()
                                          .size(8)
                                          .trendStartTime(clock.instant())
                                          .trendEndTime(clock.instant().plus(40, ChronoUnit.MINUTES))
                                          .build();
    List<RiskData> healthScores = historicalTrend.getHealthScores();

    healthScores.get(0).setHealthScore(null);
    healthScores.get(0).setRiskStatus(Risk.NO_DATA);
    healthScores.get(1).setHealthScore(null);
    healthScores.get(1).setRiskStatus(Risk.NO_ANALYSIS);
    healthScores.get(2).setHealthScore(12);
    healthScores.get(2).setRiskStatus(Risk.UNHEALTHY);
    healthScores.get(3).setHealthScore(null);
    healthScores.get(3).setRiskStatus(Risk.NO_DATA);
    healthScores.get(4).setHealthScore(null);
    healthScores.get(4).setRiskStatus(Risk.NO_ANALYSIS);
    healthScores.get(5).setHealthScore(12);
    healthScores.get(5).setRiskStatus(Risk.UNHEALTHY);
    healthScores.get(6).setHealthScore(30);
    healthScores.get(6).setRiskStatus(Risk.NEED_ATTENTION);
    healthScores.get(7).setHealthScore(75);
    healthScores.get(7).setRiskStatus(Risk.HEALTHY);

    historicalTrend.reduceHealthScoreDataToXPoints(2);
    Instant time = clock.instant();
    List<RiskData> resultingHealthScore = historicalTrend.getHealthScores();

    assertThat(resultingHealthScore.get(0).getHealthScore()).isEqualTo(null);
    assertThat(resultingHealthScore.get(0).getRiskStatus()).isEqualTo(Risk.NO_ANALYSIS);
    assertThat(resultingHealthScore.get(0).getTimeRangeParams().getStartTime()).isEqualTo(time);
    assertThat(resultingHealthScore.get(0).getTimeRangeParams().getEndTime())
        .isEqualTo(time.plus(10, ChronoUnit.MINUTES));
    time = time.plus(10, ChronoUnit.MINUTES);

    assertThat(resultingHealthScore.get(1).getHealthScore()).isEqualTo(12);
    assertThat(resultingHealthScore.get(1).getRiskStatus()).isEqualTo(Risk.UNHEALTHY);
    assertThat(resultingHealthScore.get(1).getTimeRangeParams().getStartTime()).isEqualTo(time);
    assertThat(resultingHealthScore.get(1).getTimeRangeParams().getEndTime())
        .isEqualTo(time.plus(10, ChronoUnit.MINUTES));
    time = time.plus(10, ChronoUnit.MINUTES);

    assertThat(resultingHealthScore.get(2).getHealthScore()).isEqualTo(12);
    assertThat(resultingHealthScore.get(2).getRiskStatus()).isEqualTo(Risk.UNHEALTHY);
    assertThat(resultingHealthScore.get(2).getTimeRangeParams().getStartTime()).isEqualTo(time);
    assertThat(resultingHealthScore.get(2).getTimeRangeParams().getEndTime())
        .isEqualTo(time.plus(10, ChronoUnit.MINUTES));
    time = time.plus(10, ChronoUnit.MINUTES);

    assertThat(resultingHealthScore.get(3).getHealthScore()).isEqualTo(30);
    assertThat(resultingHealthScore.get(3).getRiskStatus()).isEqualTo(Risk.NEED_ATTENTION);
    assertThat(resultingHealthScore.get(3).getTimeRangeParams().getStartTime()).isEqualTo(time);
    assertThat(resultingHealthScore.get(3).getTimeRangeParams().getEndTime())
        .isEqualTo(time.plus(10, ChronoUnit.MINUTES));
  }
}

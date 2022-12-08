/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.Point;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLOGraphData;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLODashboardWidgetTest extends CategoryTest {
  @Before
  public void setup() {}

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetBurnRate_withSinglePoint() {
    Instant now = TIME_FOR_TESTS;
    assertThat(SLOGraphData.builder()
                   .errorBudgetRemaining(98)
                   .errorBudgetRemainingPercentage(98)
                   .sloPerformanceTrend(Arrays.asList(Point.builder().timestamp(now.toEpochMilli()).value(10).build()))
                   .build()
                   .dailyBurnRate(ZoneOffset.UTC))
        .isCloseTo(2, offset(.001));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetBurnRate_zeroHoursLeft() {
    Instant now = TIME_FOR_TESTS;
    assertThat(SLOGraphData.builder()
                   .errorBudgetRemaining(98)
                   .errorBudgetRemainingPercentage(98)
                   .sloPerformanceTrend(Arrays.asList(Point.builder().timestamp(now.toEpochMilli()).value(10).build(),
                       Point.builder().timestamp(now.plus(Duration.ofMinutes(59)).toEpochMilli()).value(10).build()))
                   .build()
                   .dailyBurnRate(ZoneOffset.UTC))
        .isCloseTo(2, offset(.001));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetBurnRate_withZeroPoint() {
    assertThat(SLOGraphData.builder()
                   .errorBudgetRemaining(98)
                   .errorBudgetRemainingPercentage(98)
                   .sloPerformanceTrend(Collections.emptyList())
                   .build()
                   .dailyBurnRate(ZoneOffset.UTC))
        .isCloseTo(0, offset(.001));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetBurnRate_multiplePoints() {
    Instant now = TIME_FOR_TESTS;
    assertThat(SLOGraphData.builder()
                   .errorBudgetRemaining(98)
                   .errorBudgetRemainingPercentage(98)
                   .sloPerformanceTrend(Arrays.asList(Point.builder().timestamp(now.toEpochMilli()).value(10).build(),
                       Point.builder().timestamp(now.plus(Duration.ofDays(2)).toEpochMilli()).value(10).build(),
                       Point.builder().timestamp(now.plus(Duration.ofDays(4)).toEpochMilli()).value(10).build()))
                   .build()
                   .dailyBurnRate(ZoneOffset.UTC))
        .isCloseTo(2 / 5.0, offset(.001));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetBurnRate_dayBoundary() {
    Instant now = Instant.parse("2020-07-27T00:00:00Z");
    assertThat(SLOGraphData.builder()
                   .errorBudgetRemaining(98)
                   .errorBudgetRemainingPercentage(98)
                   .sloPerformanceTrend(Arrays.asList(Point.builder().timestamp(now.toEpochMilli()).value(10).build(),
                       Point.builder().timestamp(now.plus(Duration.ofDays(2)).toEpochMilli()).value(10).build(),
                       Point.builder().timestamp(now.plus(Duration.ofDays(4)).toEpochMilli()).value(10).build()))
                   .build()
                   .dailyBurnRate(ZoneOffset.UTC))
        .isCloseTo(2 / 5.0, offset(.001));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetBurnRate_sameDay() {
    Instant now = Instant.parse("2020-07-27T00:00:00Z");
    Instant sameDay = Instant.parse("2020-07-27T00:01:00Z");
    assertThat(SLOGraphData.builder()
                   .errorBudgetRemaining(98)
                   .errorBudgetRemainingPercentage(98)
                   .sloPerformanceTrend(Arrays.asList(Point.builder().timestamp(now.toEpochMilli()).value(10).build(),
                       Point.builder().timestamp(sameDay.toEpochMilli()).value(10).build()))
                   .build()
                   .dailyBurnRate(ZoneOffset.UTC))
        .isCloseTo(2, offset(.001));
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetBurnRate_nextDay() {
    Instant now = Instant.parse("2020-07-26T23:59:00Z");
    Instant next = Instant.parse("2020-07-28T00:01:00Z");
    assertThat(SLOGraphData.builder()
                   .errorBudgetRemaining(98)
                   .errorBudgetRemainingPercentage(98)
                   .sloPerformanceTrend(Arrays.asList(Point.builder().timestamp(now.toEpochMilli()).value(10).build(),
                       Point.builder().timestamp(next.toEpochMilli()).value(10).build()))
                   .build()
                   .dailyBurnRate(ZoneOffset.UTC))
        .isCloseTo(2 / 3.0, offset(.001));
  }
}

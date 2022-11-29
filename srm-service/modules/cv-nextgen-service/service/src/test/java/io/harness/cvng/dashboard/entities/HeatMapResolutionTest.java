/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.entities;

import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.FIFTEEN_HOURS;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.FIFTEEN_MINUTES;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.FIVE_MIN;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.ONE_HOUR_THIRTY_MINUTES;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.THIRTY_MINUTES;
import static io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution.THREE_HOURS_THIRTY_MINUTES;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.dashboard.entities.HeatMap.HeatMapResolution;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HeatMapResolutionTest extends CvNextGenTestBase {
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetHeatMapResolution() {
    Instant endTime = Instant.now();
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(10, ChronoUnit.MINUTES), endTime))
        .isEqualTo(FIVE_MIN);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(4, ChronoUnit.HOURS), endTime)).isEqualTo(FIVE_MIN);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(6, ChronoUnit.HOURS), endTime))
        .isEqualTo(FIFTEEN_MINUTES);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(12, ChronoUnit.HOURS), endTime))
        .isEqualTo(FIFTEEN_MINUTES);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(16, ChronoUnit.HOURS), endTime))
        .isEqualTo(THIRTY_MINUTES);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(24, ChronoUnit.HOURS), endTime))
        .isEqualTo(THIRTY_MINUTES);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(26, ChronoUnit.HOURS), endTime))
        .isEqualTo(ONE_HOUR_THIRTY_MINUTES);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(7, ChronoUnit.DAYS), endTime))
        .isEqualTo(THREE_HOURS_THIRTY_MINUTES);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(4, ChronoUnit.DAYS), endTime))
        .isEqualTo(THREE_HOURS_THIRTY_MINUTES);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(8, ChronoUnit.DAYS), endTime))
        .isEqualTo(FIFTEEN_HOURS);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(30, ChronoUnit.DAYS), endTime))
        .isEqualTo(FIFTEEN_HOURS);
    assertThat(HeatMapResolution.getHeatMapResolution(endTime.minus(60, ChronoUnit.DAYS), endTime))
        .isEqualTo(FIFTEEN_HOURS);
  }
}

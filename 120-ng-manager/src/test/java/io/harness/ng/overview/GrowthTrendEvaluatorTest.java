/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview;

import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.NGDateUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.activityhistory.dto.TimeGroupType;
import io.harness.ng.overview.dto.EntityStatusDetails;
import io.harness.ng.overview.dto.TimeValuePair;
import io.harness.ng.overview.util.GrowthTrendEvaluator;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DX)
public class GrowthTrendEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testTuneQueryInterval() {
    long startTimeInMs = 1620086400000L; // May 4, 2021 00:00:00 AM UTC
    long endTimeInMs = 1621768944000L; // May 23, 2021 11:22:24 AM UTC

    Pair<Long, Long> resultTimestamp =
        GrowthTrendEvaluator.tuneQueryInterval(startTimeInMs, endTimeInMs, TimeGroupType.DAY);
    long resultantEndTime = resultTimestamp.getRight();
    long resultantStartTime = resultTimestamp.getLeft();
    assertThat(resultantEndTime).isEqualTo(1621814400000L);
    assertThat(resultantStartTime).isEqualTo(1620086400000L);

    resultTimestamp = GrowthTrendEvaluator.tuneQueryInterval(startTimeInMs, endTimeInMs, TimeGroupType.WEEK);
    resultantEndTime = resultTimestamp.getRight();
    resultantStartTime = resultTimestamp.getLeft();
    assertThat(resultantEndTime).isEqualTo(1621814400000L);
    assertThat(resultantStartTime).isEqualTo(1620000000000L);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetWindow() {
    long startTimeInMs = 1620000000000L; // May 3, 2021 00:00:00 AM UTC
    long entityTypeInMs = 1620213780000L; // 05 May 2021 11:23:00 GMT
    long windowTimestamp =
        GrowthTrendEvaluator.getWindow(entityTypeInMs, TimeGroupType.DAY.getDurationInMs(), startTimeInMs);
    assertThat(windowTimestamp).isEqualTo(1620259200000L);

    windowTimestamp =
        GrowthTrendEvaluator.getWindow(entityTypeInMs, TimeGroupType.WEEK.getDurationInMs(), startTimeInMs);
    assertThat(windowTimestamp).isEqualTo(1620604800000L);

    entityTypeInMs = 1619827200000L; // 1 May 2021 00:00:00 GMT
    windowTimestamp =
        GrowthTrendEvaluator.getWindow(entityTypeInMs, TimeGroupType.DAY.getDurationInMs(), startTimeInMs);
    assertThat(windowTimestamp).isEqualTo(1620000000000L);
    windowTimestamp =
        GrowthTrendEvaluator.getWindow(entityTypeInMs, TimeGroupType.WEEK.getDurationInMs(), startTimeInMs);
    assertThat(windowTimestamp).isEqualTo(1620000000000L);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetGrowthTrend() {
    long startTimeInMs = 1620086400000L; // May 4, 2021 00:00:00 AM UTC
    long endTimeInMs = 1621768944000L; // May 23, 2021 11:22:24 AM UTC

    List<EntityStatusDetails> entities = new ArrayList<>();
    long tempTimestamp = startTimeInMs + NGDateUtils.DAY_IN_MS;

    // Add an entity before search interval range
    entities.add(new EntityStatusDetails(1618584610000L, true, 1620571810000L));

    for (int i = 0; i < 10; i++) {
      if (i % 5 == 0) {
        entities.add(new EntityStatusDetails(tempTimestamp, true, 1621176610000L + i * NGDateUtils.DAY_IN_MS));
      } else {
        entities.add(new EntityStatusDetails(tempTimestamp));
      }
      tempTimestamp += NGDateUtils.DAY_IN_MS;
    }

    List<TimeValuePair<Integer>> trend =
        GrowthTrendEvaluator.getGrowthTrend(entities, startTimeInMs, endTimeInMs, TimeGroupType.DAY);
    List<Integer> finalTrendDataPointsExpected =
        new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 6, 7, 8, 9, 10, 10, 10, 9, 9, 9, 9, 9, 8, 8, 8));
    assertThat(trend.size()).isEqualTo(21);
    int i = 0;
    for (TimeValuePair<Integer> timeValuePair : trend) {
      assertThat(timeValuePair.getValue()).isEqualTo(finalTrendDataPointsExpected.get(i++));
    }
  }
}

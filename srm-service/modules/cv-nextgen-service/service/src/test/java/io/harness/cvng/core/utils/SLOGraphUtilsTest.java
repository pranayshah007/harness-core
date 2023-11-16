/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.utils.SLOGraphUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLOGraphUtilsTest extends CvNextGenTestBase {
  @Inject private Clock clock;

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetMinutes() {
    Instant startTime = clock.instant();
    Instant endTime = clock.instant().plus(5, ChronoUnit.MINUTES);
    List<Instant> minutes = SLOGraphUtils.getMinutesExclusiveOfStartAndEndTime(startTime, endTime, 0L);
    assertThat(minutes.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testGetMinutesForMaxRecords() {
    Instant startTime = clock.instant();
    Instant endTime = clock.instant().plus(7, ChronoUnit.DAYS);
    List<Instant> minutes = SLOGraphUtils.getMinutesExclusiveOfStartAndEndTime(startTime, endTime, 2000);
    assertThat(minutes.size()).isEqualTo(2015);
    assertThat(minutes.get(0)).isEqualTo(startTime.plus(5, ChronoUnit.MINUTES));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetBucketMinutes() {
    Instant startTime = clock.instant();
    Instant endTime = clock.instant().plus(5, ChronoUnit.MINUTES);
    List<Instant> minutes = SLOGraphUtils.getBucketMinutesExclusiveOfStartAndEndTime(startTime, endTime, 0L, 5);
    assertThat(minutes.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testGetBucketMinutesForMaxRecords() {
    Instant startTime = clock.instant();
    Instant endTime = clock.instant().plus(7, ChronoUnit.DAYS);
    List<Instant> minutes = SLOGraphUtils.getBucketMinutesExclusiveOfStartAndEndTime(startTime, endTime, 2000, 5);
    assertThat(minutes.size()).isEqualTo(1007); // the intervals will be 10 mins instead of 5
    assertThat(minutes.get(0)).isEqualTo(startTime.plus(10, ChronoUnit.MINUTES));
  }
}

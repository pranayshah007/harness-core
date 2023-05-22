/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HDurationsTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.AVMOHAN)
  @Category(UnitTests.class)
  public void shouldParseDurationString() throws Exception {
    assertThat(HDurations.parse("42s")).isEqualTo(Duration.newBuilder().setSeconds(42).build());
  }

  @Test
  @Owner(developers = OwnerRule.AVMOHAN)
  @Category(UnitTests.class)
  public void shouldThrowForInvalidDurationString() throws Exception {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> HDurations.parse("random"))
        .withMessageContaining("Invalid format");
  }

  @Test
  @Owner(developers = OwnerRule.AVMOHAN)
  @Category(UnitTests.class)
  public void shouldParseMinute() throws Exception {
    assertThat(HDurations.parse("1m0s")).isEqualTo(Durations.fromMinutes(1));
  }

  @Test
  @Owner(developers = OwnerRule.AVMOHAN)
  @Category(UnitTests.class)
  public void shouldParseMinuteAndSecond() throws Exception {
    assertThat(HDurations.parse("1m23s")).isEqualTo(Durations.fromSeconds(83));
  }

  @Test
  @Owner(developers = OwnerRule.TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void shouldParseSecondsWithOptionalMillis() throws Exception {
    assertThat(HDurations.parse("5m23.223s")).isEqualTo(Durations.fromSeconds(323));
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV3)
  @Category(UnitTests.class)
  public void shouldParseSecondsWithBigDecimalDigits() throws Exception {
    assertThat(HDurations.parse("12.3467743134534545435348899s"))
        .isEqualTo(Duration.newBuilder().setSeconds(12).build());
    assertThat(HDurations.parse("30.785332416s")).isEqualTo(Duration.newBuilder().setSeconds(30).build());
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV3)
  @Category(UnitTests.class)
  public void shouldParseStringWithHoursMinutesSeconds() throws Exception {
    assertThat(HDurations.parse("256h47m16.854775407s")).isEqualTo(Duration.newBuilder().setSeconds(924436).build());
    assertThat(HDurations.parse("1h")).isEqualTo(Duration.newBuilder().setSeconds(3600).build());
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV3)
  @Category(UnitTests.class)
  public void shouldParseMillisecondsString() throws Exception {
    assertThat(HDurations.parse("722ms")).isEqualTo(Duration.newBuilder().setSeconds(0).build());
  }
}

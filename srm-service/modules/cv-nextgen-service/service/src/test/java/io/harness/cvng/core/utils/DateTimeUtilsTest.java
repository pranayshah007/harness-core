/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.time.Instant;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DateTimeUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testRoundDownTo5MinBoundary() {
    assertThat(DateTimeUtils.roundDownTo5MinBoundary(Instant.parse("2020-04-22T10:02:06Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:00:00Z"));
    assertThat(DateTimeUtils.roundDownTo5MinBoundary(Instant.parse("2020-04-22T10:00:06Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:00:00Z"));
    assertThat(DateTimeUtils.roundDownTo5MinBoundary(Instant.parse("2020-04-22T10:59:59Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:55:00Z"));
    assertThat(DateTimeUtils.roundDownTo5MinBoundary(Instant.parse("2020-04-22T10:15:59Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:15:00Z"));
    assertThat(DateTimeUtils.roundDownTo5MinBoundary(Instant.parse("2020-04-22T10:26:00Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:25:00Z"));
    assertThat(DateTimeUtils.roundDownTo5MinBoundary(Instant.parse("2020-04-22T10:00:00Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:00:00Z"));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testRoundUpTo5MinBoundary() {
    assertThat(DateTimeUtils.roundUpTo5MinBoundary(Instant.parse("2020-04-22T10:02:06Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:05:00Z"));
    assertThat(DateTimeUtils.roundUpTo5MinBoundary(Instant.parse("2020-04-22T10:00:06Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:05:00Z"));
    assertThat(DateTimeUtils.roundUpTo5MinBoundary(Instant.parse("2020-04-22T10:59:59Z")))
        .isEqualTo(Instant.parse("2020-04-22T11:00:00Z"));
    assertThat(DateTimeUtils.roundUpTo5MinBoundary(Instant.parse("2020-04-22T10:15:59Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:20:00Z"));
    assertThat(DateTimeUtils.roundUpTo5MinBoundary(Instant.parse("2020-04-22T10:26:00Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:30:00Z"));
    assertThat(DateTimeUtils.roundUpTo5MinBoundary(Instant.parse("2020-04-22T10:00:00Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:00:00Z"));
    assertThat(DateTimeUtils.roundUpTo5MinBoundary(Instant.parse("2020-12-31T23:59:00Z")))
        .isEqualTo(Instant.parse("2021-01-01T00:00:00Z"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testRoundDownTo1MinBoundary() {
    assertThat(DateTimeUtils.roundDownTo1MinBoundary(Instant.parse("2020-04-22T10:02:06Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:02:00Z"));
    assertThat(DateTimeUtils.roundDownTo1MinBoundary(Instant.parse("2020-04-22T10:02:00Z")))
        .isEqualTo(Instant.parse("2020-04-22T10:02:00Z"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testRoundDownToMinBoundary() {
    assertThat(DateTimeUtils.roundDownToMinBoundary(Instant.parse("2020-04-22T10:02:06Z"), 10))
        .isEqualTo(Instant.parse("2020-04-22T10:00:00Z"));
    assertThat(DateTimeUtils.roundDownToMinBoundary(Instant.parse("2020-04-22T10:00:06Z"), 10))
        .isEqualTo(Instant.parse("2020-04-22T10:00:00Z"));
    assertThat(DateTimeUtils.roundDownToMinBoundary(Instant.parse("2020-04-22T10:59:59Z"), 15))
        .isEqualTo(Instant.parse("2020-04-22T10:45:00Z"));
    assertThat(DateTimeUtils.roundDownToMinBoundary(Instant.parse("2020-04-22T10:15:59Z"), 7))
        .isEqualTo(Instant.parse("2020-04-22T10:14:00Z"));
    assertThatThrownBy(() -> DateTimeUtils.roundDownToMinBoundary(Instant.parse("2020-04-22T10:26:00Z"), 60))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Minute boundary need to be between 1 to 59");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testEpochMinuteToInstant() {
    assertThat(DateTimeUtils.epochMinuteToInstant(242245)).isEqualTo(Instant.parse("1970-06-18T05:25:00Z"));
    assertThat(DateTimeUtils.epochMinuteToInstant(24224535)).isEqualTo(Instant.parse("2016-01-22T14:15:00Z"));
  }
}

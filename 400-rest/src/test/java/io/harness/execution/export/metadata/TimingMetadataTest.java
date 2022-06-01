/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.metadata;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.api.ExecutionDataValue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class TimingMetadataTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromStartAndEndTimeObjects() {
    assertThat(TimingMetadata.fromStartAndEndTimeObjects(Long.valueOf(-1), Long.valueOf(-1))).isNull();

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Instant minuteAgo = now.minus(1, ChronoUnit.MINUTES);
    TimingMetadata timingMetadata =
        TimingMetadata.fromStartAndEndTimeObjects(minuteAgo.toEpochMilli(), now.toEpochMilli());
    assertThat(timingMetadata).isNotNull();
    assertThat(timingMetadata.getStartTime().toInstant()).isEqualTo(minuteAgo);
    assertThat(timingMetadata.getEndTime().toInstant()).isEqualTo(now);
    assertThat(timingMetadata.getDuration()).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromStartAndEndTime() {
    assertThat(TimingMetadata.fromStartAndEndTime(-1, -1)).isNull();
    assertThat(TimingMetadata.fromStartAndEndTime(10, 8)).isNull();

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    TimingMetadata timingMetadata = TimingMetadata.fromStartAndEndTime(now.toEpochMilli(), -1);
    assertThat(timingMetadata).isNotNull();
    assertThat(timingMetadata.getStartTime().toInstant()).isEqualTo(now);
    assertThat(timingMetadata.getEndTime()).isNull();
    assertThat(timingMetadata.getDuration()).isNull();

    timingMetadata = TimingMetadata.fromStartAndEndTime(-1, now.toEpochMilli());
    assertThat(timingMetadata).isNotNull();
    assertThat(timingMetadata.getStartTime()).isNull();
    assertThat(timingMetadata.getEndTime().toInstant()).isEqualTo(now);
    assertThat(timingMetadata.getDuration()).isNull();

    Instant minuteAgo = now.minus(1, ChronoUnit.MINUTES);
    timingMetadata = TimingMetadata.fromStartAndEndTime(minuteAgo.toEpochMilli(), now.toEpochMilli());
    assertThat(timingMetadata).isNotNull();
    assertThat(timingMetadata.getStartTime().toInstant()).isEqualTo(minuteAgo);
    assertThat(timingMetadata.getEndTime().toInstant()).isEqualTo(now);
    assertThat(timingMetadata.getDuration()).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testExtractFromExecutionDetails() {
    assertThat(TimingMetadata.extractFromExecutionDetails(null)).isNull();

    Map<String, ExecutionDataValue> executionDetailsMap = new HashMap<>();
    executionDetailsMap.put("startTs", ExecutionDataValue.builder().value("hello").build());
    assertThat(TimingMetadata.extractFromExecutionDetails(executionDetailsMap)).isNull();

    executionDetailsMap.put("startTs", ExecutionDataValue.builder().value(-8).build());
    assertThat(TimingMetadata.extractFromExecutionDetails(executionDetailsMap)).isNull();

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    executionDetailsMap.put("startTs", ExecutionDataValue.builder().value(now.toEpochMilli()).build());
    TimingMetadata timingMetadata = TimingMetadata.extractFromExecutionDetails(executionDetailsMap);
    assertThat(timingMetadata).isNotNull();
    assertThat(timingMetadata.getStartTime().toInstant()).isEqualTo(now);
    assertThat(timingMetadata.getEndTime()).isNull();
    assertThat(timingMetadata.getDuration()).isNull();

    Instant minuteAgo = now.minus(1, ChronoUnit.MINUTES);
    executionDetailsMap.put("startTs", ExecutionDataValue.builder().value(minuteAgo.toEpochMilli()).build());
    executionDetailsMap.put("endTs", ExecutionDataValue.builder().value(now.toEpochMilli()).build());
    timingMetadata = TimingMetadata.extractFromExecutionDetails(executionDetailsMap);
    assertThat(timingMetadata).isNotNull();
    assertThat(timingMetadata.getStartTime().toInstant()).isEqualTo(minuteAgo);
    assertThat(timingMetadata.getEndTime().toInstant()).isEqualTo(now);
    assertThat(timingMetadata.getDuration()).isEqualTo(Duration.ofMinutes(1));
  }
}

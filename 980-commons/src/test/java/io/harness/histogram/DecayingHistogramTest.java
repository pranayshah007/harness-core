/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.histogram;

import static io.harness.histogram.HistogramImplTest.testHistogramOptions;
import static io.harness.histogram.HistogramImplTest.valueEpsilon;
import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.val;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DecayingHistogramTest extends CategoryTest {
  private static final Instant startTime = Instant.ofEpochSecond(1234567890L);
  private static final Duration oneHour = Duration.ofHours(1);

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testPercentilesEmptyDecayingHistogram() throws Exception {
    val h = new DecayingHistogram(testHistogramOptions, Duration.ofHours(1));
    for (double p = -.5; p <= 1.5; p += .5) {
      assertThat(h.getPercentile(p)).isEqualTo(0.0);
    }
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testSimpleDecay() throws Exception {
    val h = new DecayingHistogram(testHistogramOptions, oneHour);
    // Add a sample with a very large weight
    h.addSample(2, 1000, startTime);
    // Add another sample 20 half life periods later. Its relative
    // weight is expected to be 2^20 * 0.001 > 1000 times larger
    // than the first sample
    h.addSample(1, 1, startTime.plus(20, ChronoUnit.HOURS));
    assertThat(h.getPercentile(0.999)).isCloseTo(2, within(valueEpsilon));
    assertThat(h.getPercentile(1.0)).isCloseTo(3, within(valueEpsilon));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testLongTermDecay() throws Exception {
    val h = new DecayingHistogram(testHistogramOptions, oneHour);
    // Add a sample with a very large weight
    h.addSample(2, 1, startTime);
    // Add another sample later, such that the relative decay factor of the two samples will exceed 2^MAX_DECAY_EXPONENT
    h.addSample(1, 1, startTime.plus(101, ChronoUnit.HOURS));
    assertThat(h.getPercentile(1.0)).isCloseTo(2, within(valueEpsilon));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testDecayingHistogramPercentiles() throws Exception {
    val h = new DecayingHistogram(testHistogramOptions, oneHour);
    var timestamp = startTime;
    // Add four samples with both values and weights equal to 1,2,3,4, each separated by one half life period from the
    // previous one.
    for (int i = 1; i <= 4; i++) {
      h.addSample(i, i, timestamp);
      timestamp = timestamp.plus(oneHour);
    }
    // The expected distribution is:
    // bucket = [1..2], weight = 1 * 2^(-3), percentiles ~  0% ... 2%
    // bucket = [2..3], weight = 2 * 2^(-2), percentiles ~  3% ... 10%
    // bucket = [3..4], weight = 3 * 2^(-1), percentiles ~ 11% ... 34%
    // bucket = [4..5], weight = 4 * 2^(-0), percentiles ~ 35% ... 100%
    assertThat(h.getPercentile(0.00)).isCloseTo(2, within(valueEpsilon));
    assertThat(h.getPercentile(0.02)).isCloseTo(2, within(valueEpsilon));
    assertThat(h.getPercentile(0.03)).isCloseTo(3, within(valueEpsilon));
    assertThat(h.getPercentile(0.10)).isCloseTo(3, within(valueEpsilon));
    assertThat(h.getPercentile(0.11)).isCloseTo(4, within(valueEpsilon));
    assertThat(h.getPercentile(0.34)).isCloseTo(4, within(valueEpsilon));
    assertThat(h.getPercentile(0.35)).isCloseTo(5, within(valueEpsilon));
    assertThat(h.getPercentile(1.00)).isCloseTo(5, within(valueEpsilon));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testNoDecay() throws Exception {
    val h = new DecayingHistogram(testHistogramOptions, oneHour);
    for (int i = 1; i <= 4; i++) {
      h.addSample(i, i, startTime);
    }
    assertThat(h.getPercentile(0.0)).isCloseTo(2, within(valueEpsilon));
    assertThat(h.getPercentile(0.1)).isCloseTo(2, within(valueEpsilon));
    assertThat(h.getPercentile(0.2)).isCloseTo(3, within(valueEpsilon));
    assertThat(h.getPercentile(0.3)).isCloseTo(3, within(valueEpsilon));
    assertThat(h.getPercentile(0.4)).isCloseTo(4, within(valueEpsilon));
    assertThat(h.getPercentile(0.5)).isCloseTo(4, within(valueEpsilon));
    assertThat(h.getPercentile(0.6)).isCloseTo(4, within(valueEpsilon));
    assertThat(h.getPercentile(0.7)).isCloseTo(5, within(valueEpsilon));
    assertThat(h.getPercentile(0.8)).isCloseTo(5, within(valueEpsilon));
    assertThat(h.getPercentile(0.9)).isCloseTo(5, within(valueEpsilon));
    assertThat(h.getPercentile(1.0)).isCloseTo(5, within(valueEpsilon));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testDecayingHistogramMerge() throws Exception {
    val h1 = new DecayingHistogram(testHistogramOptions, oneHour);
    h1.addSample(1, 1, startTime);
    h1.addSample(2, 1, startTime.plus(oneHour));

    val h2 = new DecayingHistogram(testHistogramOptions, oneHour);
    h2.addSample(2, 1, startTime.plus(Duration.ofHours(2)));
    h2.addSample(3, 1, startTime.plus(oneHour));

    val expected = new DecayingHistogram(testHistogramOptions, oneHour);
    expected.addSample(2, 1, startTime.plus(Duration.ofHours(2)));
    expected.addSample(2, 1, startTime.plus(oneHour));
    expected.addSample(3, 1, startTime.plus(oneHour));
    expected.addSample(1, 1, startTime);

    h1.merge(h2);
    assertThat(h1).isEqualTo(expected);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testDecayingHistogramSaveToCheckpoint() throws Exception {
    val h = new DecayingHistogram(testHistogramOptions, oneHour);
    long referenceTimestampMs = Reflect.on(h).get("referenceTimestampMs");
    assertThat(referenceTimestampMs).isEqualTo(0);
    h.addSample(2, 1, startTime.plus(100, ChronoUnit.HOURS));
    referenceTimestampMs = Reflect.on(h).get("referenceTimestampMs");
    assertThat(referenceTimestampMs).isNotEqualTo(0);

    val checkpoint = h.saveToCheckpoint();
    assertThat(checkpoint.getReferenceTimestamp().toEpochMilli()).isEqualTo(referenceTimestampMs);
    // Just check that buckets are not empty, actual testing of bucketing
    // belongs to Histogram
    assertThat(checkpoint.getBucketWeights()).isNotEmpty();
    assertThat(checkpoint.getTotalWeight()).isNotZero();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testDecayingHistogramLoadFromCheckpoint() throws Exception {
    Instant timestamp = LocalDateTime.of(2018, Month.JANUARY, 2, 3, 4, 5).atZone(ZoneId.of("UTC")).toInstant();
    val checkpoint =
        HistogramCheckpoint.builder().totalWeight(6.0).bucketWeight(0, 1).referenceTimestamp(timestamp).build();
    val h = new DecayingHistogram(testHistogramOptions, oneHour);
    h.loadFromCheckPoint(checkpoint);
    assertThat(h.isEmpty()).isFalse();
    val referenceTimestampMs = Reflect.on(h).get("referenceTimestampMs");
    assertThat(referenceTimestampMs).isEqualTo(timestamp.toEpochMilli());
  }
}

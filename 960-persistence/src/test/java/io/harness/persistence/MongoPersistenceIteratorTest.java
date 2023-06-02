/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.persistence;

import static io.harness.rule.OwnerRule.GEORGE;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PersistenceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.iterator.PersistentIterable;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.rule.Owner;

import java.time.Duration;
import lombok.Builder;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MongoPersistenceIteratorTest extends PersistenceTestBase {
  private static Duration targetInterval = ofMinutes(1);
  private static Duration maximumDelayForCheck = ofMinutes(2);

  @Builder
  static class TestPersistentIterable implements PersistentIterable {
    private Long nextIteration;

    @Override
    public Long obtainNextIteration(String fieldName) {
      return nextIteration;
    }

    @Override
    public String getUuid() {
      return null;
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCalculateSleepDurationAsTargetInterval() {
    MongoPersistenceIterator<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>> iterator =
        MongoPersistenceIterator.<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>>builder()
            .targetInterval(targetInterval)
            .build();
    assertThat(iterator.calculateSleepDuration(null)).isEqualTo(targetInterval);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCalculateSleepDurationAsMaximumDelayForCheck() {
    MongoPersistenceIterator<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>> iterator =
        MongoPersistenceIterator.<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>>builder()
            .targetInterval(targetInterval)
            .maximumDelayForCheck(maximumDelayForCheck)
            .build();
    assertThat(iterator.calculateSleepDuration(null)).isEqualTo(maximumDelayForCheck);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCalculateSleepDurationAsZero() {
    MongoPersistenceIterator<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>> iterator =
        MongoPersistenceIterator.<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>>builder()
            .targetInterval(targetInterval)
            .maximumDelayForCheck(maximumDelayForCheck)
            .build();

    TestPersistentIterable testPersistentIterable = TestPersistentIterable.builder().build();
    assertThat(iterator.calculateSleepDuration(testPersistentIterable)).isEqualTo(maximumDelayForCheck);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCalculateSleepDurationAsNextIteration() {
    MongoPersistenceIterator<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>> iterator =
        MongoPersistenceIterator.<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>>builder()
            .targetInterval(targetInterval)
            .maximumDelayForCheck(maximumDelayForCheck)
            .build();

    TestPersistentIterable testPersistentIterable =
        TestPersistentIterable.builder().nextIteration(currentTimeMillis() + 10000).build();
    assertThat(iterator.calculateSleepDuration(testPersistentIterable).toMillis()).isLessThanOrEqualTo(10000);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCalculateSleepDurationLimitedAsNextIteration() {
    MongoPersistenceIterator<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>> iterator =
        MongoPersistenceIterator.<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>>builder()
            .targetInterval(targetInterval)
            .build();

    TestPersistentIterable testPersistentIterable =
        TestPersistentIterable.builder().nextIteration(currentTimeMillis() + targetInterval.toMillis()).build();
    assertThat(iterator.calculateSleepDuration(testPersistentIterable).toMillis())
        .isLessThanOrEqualTo(targetInterval.toMillis());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldCalculateSleepDurationLimitedAsMaximumDelayForCheck() {
    MongoPersistenceIterator<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>> iterator =
        MongoPersistenceIterator.<TestPersistentIterable, MorphiaFilterExpander<TestPersistentIterable>>builder()
            .targetInterval(targetInterval)
            .maximumDelayForCheck(maximumDelayForCheck)
            .build();

    TestPersistentIterable testPersistentIterable =
        TestPersistentIterable.builder()
            .nextIteration(currentTimeMillis() + maximumDelayForCheck.toMillis() + 10000)
            .build();
    assertThat(iterator.calculateSleepDuration(testPersistentIterable)).isEqualTo(maximumDelayForCheck);
  }
}

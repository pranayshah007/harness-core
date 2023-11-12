/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.entities.EntityDisableTime;
import io.harness.cvng.core.services.api.EntityDisabledTimeService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EntityDisabledTimeServiceImplTest extends CvNextGenTestBase {
  @Inject private EntityDisabledTimeService entityDisabledTimeService;
  @Before
  public void setup() {}

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_minDataWithLargeRange() {
    long startTime = 1659345900000L;
    long endTime = 1659345960000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659345980000L).build());
    Pair<Long, Long> result =
        entityDisabledTimeService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(1);
    assertThat(result.getRight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_minDataWithSmallRange() {
    long startTime = 1659345900000L;
    long endTime = 1659345960000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659345950000L).build());
    Pair<Long, Long> result =
        entityDisabledTimeService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(0);
    assertThat(result.getRight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_minDataWithExactRange() {
    long startTime = 1659345900000L;
    long endTime = 1659345960000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659345960000L).build());
    Pair<Long, Long> result =
        entityDisabledTimeService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(1);
    assertThat(result.getRight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_minDataWithExtraRanges() {
    long startTime = 1659345900000L;
    long endTime = 1659345960000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659344900000L).endTime(1659345680000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659345700000L).endTime(1659345780000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659345800000L).endTime(1659345880000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659345940000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659345950000L).endTime(1659345980000L).build());
    disableTimes.add(EntityDisableTime.builder().startTime(1659346500000L).endTime(1659346580000L).build());
    Pair<Long, Long> result =
        entityDisabledTimeService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(1);
    assertThat(result.getRight()).isEqualTo(5);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_withLargeRange() {
    long startTime = 1659345900000L;
    long endTime = 1659346500000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659346600000L).build());
    Pair<Long, Long> result =
        entityDisabledTimeService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(10);
    assertThat(result.getRight()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_withSmallRange() {
    long startTime = 1659345900000L;
    long endTime = 1659346500000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659346300000L).build());
    Pair<Long, Long> result =
        entityDisabledTimeService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(6);
    assertThat(result.getRight()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetDisabledMinBetweenRecords_withExactRange() {
    long startTime = 1659345900000L;
    long endTime = 1659346500000L;
    List<EntityDisableTime> disableTimes = new ArrayList<>();
    disableTimes.add(EntityDisableTime.builder().startTime(1659345900000L).endTime(1659346500000L).build());
    Pair<Long, Long> result =
        entityDisabledTimeService.getDisabledMinBetweenRecords(startTime, endTime, 0, disableTimes);
    assertThat(result.getLeft()).isEqualTo(10);
    assertThat(result.getRight()).isEqualTo(1);
  }
}

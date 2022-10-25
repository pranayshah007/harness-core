/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.governance;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.governance.TimeRangeBasedFreezeConfig.FreezeWindowStateType;
import io.harness.rule.Owner;

import software.wings.resources.stats.model.TimeRange;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TimeRangeBasedFreezeConfigTest {
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCheckIfActiveWhenTimeRangeIsNull() {
    TimeRange timeRange = Mockito.mock(TimeRange.class);
    final TimeRangeBasedFreezeConfig config =
        TimeRangeBasedFreezeConfig.builder().timeRange(timeRange).applicable(true).build();
    // FORCE NULL TIME RANGE
    config.setTimeRange(null);
    assertThat(config.checkIfActive()).isFalse();
    verify(timeRange, never()).getFreezeOccurrence();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetFreezeWindowStateBeInactiveWhenApplicableIsFalse() {
    TimeRange timeRange = Mockito.mock(TimeRange.class);
    final TimeRangeBasedFreezeConfig config =
        TimeRangeBasedFreezeConfig.builder().timeRange(timeRange).applicable(false).build();
    assertThat(config.getFreezeWindowState()).isEqualTo(FreezeWindowStateType.INACTIVE);
    verify(timeRange, never()).getFreezeOccurrence();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetFreezeWindowStateBeInactiveWhenTimeRangeIsNull() {
    TimeRange timeRange = Mockito.mock(TimeRange.class);
    final TimeRangeBasedFreezeConfig config =
        TimeRangeBasedFreezeConfig.builder().timeRange(timeRange).applicable(true).build();
    config.setTimeRange(null);
    assertThat(config.getFreezeWindowState()).isEqualTo(FreezeWindowStateType.INACTIVE);
    verify(timeRange, never()).getFreezeOccurrence();
  }
}

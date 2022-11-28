/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ProcessorHelperTest extends WingsBaseTest {
  private final String KEY = "KEY";

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLongValueNullCases() {
    Long result = ProcessorHelper.getLongValue(KEY, null);
    assertThat(result).isEqualTo(0l);

    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder().build();
    result = ProcessorHelper.getLongValue(KEY, timeSeriesEventInfo);
    assertThat(result).isEqualTo(0l);

    timeSeriesEventInfo = TimeSeriesEventInfo.builder().longData(new HashMap<>()).build();
    result = ProcessorHelper.getLongValue(KEY, timeSeriesEventInfo);
    assertThat(result).isEqualTo(0l);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLongValueCase() {
    Map<String, Long> map = new HashMap<>();
    map.put(KEY, 1L);
    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder().longData(map).build();
    long result = ProcessorHelper.getLongValue(KEY, timeSeriesEventInfo);
    assertThat(result).isEqualTo(1l);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetBooleanValueNullCases() {
    Boolean result = ProcessorHelper.getBooleanValue(KEY, null);
    assertThat(result).isEqualTo(false);

    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder().build();
    result = ProcessorHelper.getBooleanValue(KEY, timeSeriesEventInfo);
    assertThat(result).isEqualTo(false);

    timeSeriesEventInfo = TimeSeriesEventInfo.builder().booleanData(new HashMap<>()).build();
    result = ProcessorHelper.getBooleanValue(KEY, timeSeriesEventInfo);
    assertThat(result).isEqualTo(false);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetBooleanValueCase() {
    Map<String, Boolean> map = new HashMap<>();
    map.put(KEY, true);
    TimeSeriesEventInfo timeSeriesEventInfo = TimeSeriesEventInfo.builder().booleanData(map).build();
    Boolean result = ProcessorHelper.getBooleanValue(KEY, timeSeriesEventInfo);
    assertThat(result).isEqualTo(true);
  }
}

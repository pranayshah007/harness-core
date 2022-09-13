/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging;

import static io.harness.rule.OwnerRule.ARVIND;

import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class LogCallbackUtilsTest extends CategoryTest {
  private LogCallback logCallback;

  private static final String LINE = "line";
  private static final LogLevel LOG_LEVEL = LogLevel.INFO;
  private static final CommandExecutionStatus STATUS = CommandExecutionStatus.FAILURE;

  @Before
  public void before() {
    logCallback = Mockito.mock(LogCallback.class);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testLine() {
    LogCallbackUtils.saveExecutionLogSafely(null, LINE);
    LogCallbackUtils.saveExecutionLogSafely(logCallback, LINE);
    verify(logCallback).saveExecutionLog(LINE);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testLineLogLevel() {
    LogCallbackUtils.saveExecutionLogSafely(null, LINE, LOG_LEVEL);
    LogCallbackUtils.saveExecutionLogSafely(logCallback, LINE, LOG_LEVEL);
    verify(logCallback).saveExecutionLog(LINE, LOG_LEVEL);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testLineLogLevelStatus() {
    LogCallbackUtils.saveExecutionLogSafely(null, LINE, LOG_LEVEL, STATUS);
    LogCallbackUtils.saveExecutionLogSafely(logCallback, LINE, LOG_LEVEL, STATUS);
    verify(logCallback).saveExecutionLog(LINE, LOG_LEVEL, STATUS);
  }
}

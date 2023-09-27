/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cli;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class TerraformCliErrorLogOutputStreamTest extends CategoryTest {
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testNoLinesSanitized() {
    LogCallback logCallback = mock(LogCallback.class);
    TerraformCliErrorLogOutputStream logOutputStream = new TerraformCliErrorLogOutputStream(logCallback, true);

    String ansiTagsLogLine = "│[31m\u001B[0m\u001B[0m \u001B[";
    String simpleTextLogLine = "simple text";

    logOutputStream.processLine(ansiTagsLogLine);
    logOutputStream.processLine(simpleTextLogLine);

    verify(logCallback, times(1)).saveExecutionLog(eq(ansiTagsLogLine), eq(LogLevel.ERROR), eq(true));
    verify(logCallback, times(1)).saveExecutionLog(eq(simpleTextLogLine), eq(LogLevel.ERROR), eq(true));

    assertThat(logOutputStream.getError()).isEqualTo(ansiTagsLogLine + " " + simpleTextLogLine);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testLinesSanitized() {
    LogCallback logCallback = mock(LogCallback.class);
    TerraformCliErrorLogOutputStream logOutputStream = new TerraformCliErrorLogOutputStream(logCallback, true);
    String expectedSanitizedLine = "secret_key = testsecret/test";

    String ansiUnderlinedLogLine = "secret_key = \u001B[4mtestsecret\u001B[0m/test";
    String simpleTextLogLine = "simple text";

    logOutputStream.processLine(ansiUnderlinedLogLine);
    logOutputStream.processLine(simpleTextLogLine);

    verify(logCallback, times(1)).saveExecutionLog(eq(expectedSanitizedLine), eq(LogLevel.ERROR), eq(true));
    verify(logCallback, times(1)).saveExecutionLog(eq(simpleTextLogLine), eq(LogLevel.ERROR), eq(true));

    assertThat(logOutputStream.getError()).isEqualTo(expectedSanitizedLine + " " + simpleTextLogLine);
  }
}

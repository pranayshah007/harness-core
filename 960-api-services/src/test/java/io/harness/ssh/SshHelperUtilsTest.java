/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssh;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.harness.ApiServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;
import io.harness.shell.SshSessionFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.powermock.core.classloader.annotations.PrepareForTest;

@Slf4j
@PrepareForTest(SshSessionFactory.class)
public class SshHelperUtilsTest extends ApiServiceTestBase {
  private LogCallback logCallback = mock(LogCallback.class);
  private Writer writer = mock(Writer.class);

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteLocalCommand() throws IOException {
    SshHelperUtils.executeLocalCommand("echo test", logCallback, writer, false, Collections.emptyMap());
    verify(logCallback, times(1)).saveExecutionLog("test", LogLevel.INFO);
    verifyNoInteractions(writer);

    SshHelperUtils.executeLocalCommand("echo test", logCallback, writer, true, Collections.emptyMap());
    verify(writer, times(1)).write("test");
    verifyNoMoreInteractions(logCallback);

    SshHelperUtils.executeLocalCommand("echo test >&2", logCallback, writer, false, Collections.emptyMap());
    verify(logCallback, times(1)).saveExecutionLog("test", LogLevel.ERROR);
    verifyNoMoreInteractions(writer);
  }
}

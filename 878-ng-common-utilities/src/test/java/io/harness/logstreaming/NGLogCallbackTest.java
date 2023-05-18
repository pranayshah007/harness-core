/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGLogCallbackTest extends CategoryTest {
  private static final String LOG_SUFFIX = "logSuffix";

  @Mock LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock LogStreamingStepClientImpl logStreamingStepClient;

  private NGLogCallback ngManagerLogCallback;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(logStreamingStepClientFactory.getLogStreamingStepClient(ambiance)).thenReturn(logStreamingStepClient);
    ngManagerLogCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, LOG_SUFFIX, false);
  }

  @After
  public void verifyInteractions() {
    verifyNoMoreInteractions(logStreamingStepClient);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testSaveExecutionLogWithOneParam() {
    ngManagerLogCallback.saveExecutionLog("test");
    verify(logStreamingStepClient).writeLogLine(any(), eq(LOG_SUFFIX));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testSaveExecutionLogWithTwoParam() {
    ngManagerLogCallback.saveExecutionLog("line", LogLevel.INFO);
    verify(logStreamingStepClient).writeLogLine(any(), eq(LOG_SUFFIX));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testSaveExecutionLogWithThreeParam() {
    ngManagerLogCallback.saveExecutionLog("line", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    verify(logStreamingStepClient).writeLogLine(any(), eq(LOG_SUFFIX));
    verify(logStreamingStepClient).closeStream(eq(LOG_SUFFIX));
  }
}

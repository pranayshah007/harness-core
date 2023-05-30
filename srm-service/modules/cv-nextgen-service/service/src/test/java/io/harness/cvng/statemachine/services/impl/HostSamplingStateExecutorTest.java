/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.HostSamplingState;
import io.harness.cvng.statemachine.services.api.HostSamplingStateExecutor;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class HostSamplingStateExecutorTest extends CategoryTest {
  private HostSamplingState hostSamplingState;
  private HostSamplingStateExecutor hostSamplingStateExecutor;

  @Before
  public void setup() throws Exception {
    hostSamplingStateExecutor = Mockito.mock(HostSamplingStateExecutor.class, Mockito.CALLS_REAL_METHODS);
    hostSamplingState = new HostSamplingState();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetExecutionStatus() {
    List.of(AnalysisStatus.values()).forEach(analysisStatus -> {
      hostSamplingState.setStatus(analysisStatus);
      AnalysisStatus status = hostSamplingStateExecutor.getExecutionStatus(hostSamplingState);
      assertThat(status).isEqualTo(analysisStatus);
    });
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testHandleRunning() {
    List.of(AnalysisStatus.values()).forEach(analysisStatus -> {
      hostSamplingState.setStatus(analysisStatus);
      AnalysisState analysisState = hostSamplingStateExecutor.handleRunning(hostSamplingState);
      assertThat(analysisState.getStatus()).isEqualTo(AnalysisStatus.TRANSITION);
    });
  }
}

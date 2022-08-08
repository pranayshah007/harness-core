/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitForExecutionInputCallbackTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private OrchestrationEngine engine;
  @Mock private ExecutorService executorService;
  @InjectMocks private WaitForExecutionInputCallback waitForExecutionInputCallback;
  String nodeExecutionId = "nodeExecutionId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    waitForExecutionInputCallback.setNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNotify() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("id").build();
    doReturn(NodeExecution.builder().ambiance(ambiance).build()).when(nodeExecutionService).get(nodeExecutionId);
    waitForExecutionInputCallback.notify(null);
    verify(executorService, times(1)).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNotifyTimeout() {
    // TODO(BRIJESH): will write after completing the implementation of the method.
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNotifyError() {
    // TODO(BRIJESH): will write after completing the implementation of the method.
  }
}

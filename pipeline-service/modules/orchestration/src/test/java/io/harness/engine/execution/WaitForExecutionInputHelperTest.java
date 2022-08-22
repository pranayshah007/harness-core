/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.PmsFeatureFlagService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitForExecutionInputHelperTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private ExecutionInputService executionInputService;
  @InjectMocks private WaitForExecutionInputHelper waitForExecutionInputHelper;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(pmsFeatureFlagService.isEnabled("accountId", FeatureName.NG_EXECUTION_INPUT)).thenReturn(true);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testWaitForExecutionInput() {
    String nodeExecutionId = "nodeExecutionId";
    String template = "template";
    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).build();
    ArgumentCaptor<WaitForExecutionInputCallback> callbackArgumentCaptor =
        ArgumentCaptor.forClass(WaitForExecutionInputCallback.class);
    ArgumentCaptor<ExecutionInputInstance> inputInstanceArgumentCaptor =
        ArgumentCaptor.forClass(ExecutionInputInstance.class);
    waitForExecutionInputHelper.waitForExecutionInput(
        Ambiance.newBuilder().putSetupAbstractions("accountId", "accountId").build(), nodeExecution.getUuid(),
        PlanNode.builder().executionInputTemplate(template).build());
    verify(waitNotifyEngine, times(1)).waitForAllOnInList(any(), callbackArgumentCaptor.capture(), any(), any());
    WaitForExecutionInputCallback waitForExecutionInputCallback = callbackArgumentCaptor.getValue();

    assertNotNull(waitForExecutionInputCallback);
    assertEquals(waitForExecutionInputCallback.getNodeExecutionId(), nodeExecutionId);

    verify(executionInputService, times(1)).save(inputInstanceArgumentCaptor.capture());
    ExecutionInputInstance inputInstance = inputInstanceArgumentCaptor.getValue();

    assertNotNull(inputInstance);
    assertEquals(inputInstance.getNodeExecutionId(), nodeExecutionId);
    assertEquals(inputInstance.getTemplate(), template);

    // InputInstanceId should be same in inputInstance and callback.
    assertEquals(inputInstance.getInputInstanceId(), waitForExecutionInputCallback.getInputInstanceId());

    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.INPUT_WAITING), eq(null), eq(EnumSet.noneOf(Status.class)));
  }
}

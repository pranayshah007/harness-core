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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.common.ExpressionMode;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;

import java.util.EnumSet;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitForExecutionInputHelperTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private ExecutionInputService executionInputService;
  @InjectMocks private WaitForExecutionInputHelper waitForExecutionInputHelper;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;

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
    String fieldYaml = "pipeline:\n  name: \"pipeline1\"\n  var: \"var/<+pipeline.name>\"\n";
    String resolvedFieldYaml = "pipeline:\n  name: pipeline1\"\n"
        + "  var: var/pipeline1\n";
    doReturn(Optional.of(PlanExecutionMetadata.builder().yaml(fieldYaml).build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(any());
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setOriginalIdentifier("pipeline").buildPartial())
                            .putSetupAbstractions("accountId", "accountId")
                            .build();
    doReturn(YamlUtils.readYamlTree(resolvedFieldYaml).getNode().getCurrJsonNode())
        .when(pmsEngineExpressionService)
        .resolve(ambiance, YamlNode.getNodeYaml(YamlUtils.readYamlTree(fieldYaml).getNode(), ambiance.getLevelsList()),
            ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    waitForExecutionInputHelper.waitForExecutionInput(
        ambiance, nodeExecution.getUuid(), PlanNode.builder().executionInputTemplate(template).build());
    verify(waitNotifyEngine, times(1)).waitForAllOnInList(any(), callbackArgumentCaptor.capture(), any(), any());
    WaitForExecutionInputCallback waitForExecutionInputCallback = callbackArgumentCaptor.getValue();

    assertNotNull(waitForExecutionInputCallback);
    assertEquals(waitForExecutionInputCallback.getNodeExecutionId(), nodeExecutionId);

    verify(executionInputService, times(1)).save(inputInstanceArgumentCaptor.capture());
    ExecutionInputInstance inputInstance = inputInstanceArgumentCaptor.getValue();

    assertNotNull(inputInstance);
    assertEquals(inputInstance.getNodeExecutionId(), nodeExecutionId);
    assertEquals(inputInstance.getTemplate(), template);
    // expressions will be resolved in the fieldYaml and then saved in executionInputInstance.
    assertEquals(inputInstance.getFieldYaml(), resolvedFieldYaml);

    // InputInstanceId should be same in inputInstance and callback.
    assertEquals(inputInstance.getInputInstanceId(), waitForExecutionInputCallback.getInputInstanceId());

    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.INPUT_WAITING), eq(null), eq(EnumSet.noneOf(Status.class)));
  }
}

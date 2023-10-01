/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.SPG;
import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static software.wings.api.EnvStateExecutionData.Builder.anEnvStateExecutionData;
import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.EnvStateExecutionData;
import software.wings.api.SkipStateExecutionData;
import software.wings.beans.PipelineExecution;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(SPG)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class EnvRollbackStateTest extends WingsBaseTest {
  @Mock private ExecutionContextImpl context;
  @Mock private WorkflowExecutionService executionService;

  @InjectMocks private EnvRollbackState envRollbackState = new EnvRollbackState("Rollback-STAGE 1");

  @Before
  public void setUp() throws Exception {
    EnvStateExecutionData prevEnvExecution =
        anEnvStateExecutionData().withWorkflowExecutionId("WORKFLOW_EXECUTION_ID").build();
    Map<String, StateExecutionData> stateExecutionDataMap = new HashMap<>();
    stateExecutionDataMap.put("STAGE 1", prevEnvExecution);
    StateExecutionInstance instance =
        aStateExecutionInstance().executionUuid("EXECUTION_PIPE_ID").stateExecutionMap(stateExecutionDataMap).build();
    doReturn(WORKFLOW_ID).when(context).getWorkflowId();
    doReturn(APP_ID).when(context).getAppId();
    doReturn(instance).when(context).getStateExecutionInstance();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldExecute() {
    WorkflowExecution mockRollbackExecution = mock(WorkflowExecution.class);
    doReturn("WORKFLOW_ROLLBACK_EXEC_ID").when(mockRollbackExecution).getUuid();
    doReturn(mockRollbackExecution).when(executionService).triggerRollbackExecutionWorkflow(any(), any(), eq(true));

    List<PipelineStageExecution> pipelineStageExecutions =
        List.of(PipelineStageExecution.builder().stateName("STAGE 1").build());

    PipelineExecution pipelineExecution =
        aPipelineExecution().withPipelineStageExecutions(pipelineStageExecutions).build();
    WorkflowExecution mockExecution = WorkflowExecution.builder().pipelineExecution(pipelineExecution).build();
    doReturn(mockExecution)
        .when(executionService)
        .getWorkflowExecution(eq(APP_ID), eq("WORKFLOW_EXECUTION_ID"), any(String[].class));

    ExecutionResponse executionResponse = envRollbackState.execute(context);

    verify(executionService).triggerRollbackExecutionWorkflow(APP_ID, mockExecution, true);
    assertThat(executionResponse.getCorrelationIds().get(0)).isEqualTo("WORKFLOW_ROLLBACK_EXEC_ID");
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testExecutionFail_shouldSendSkipData() {
    WorkflowExecution mockRollbackExecution = mock(WorkflowExecution.class);
    doReturn("WORKFLOW_ROLLBACK_EXEC_ID").when(mockRollbackExecution).getUuid();
    doThrow(new InvalidRequestException("Deployment already exists"))
        .when(executionService)
        .triggerRollbackExecutionWorkflow(any(), any(), eq(true));

    List<PipelineStageExecution> pipelineStageExecutions =
        List.of(PipelineStageExecution.builder().stateName("STAGE 1").build());

    PipelineExecution pipelineExecution =
        aPipelineExecution().withPipelineStageExecutions(pipelineStageExecutions).build();
    WorkflowExecution mockExecution = WorkflowExecution.builder().pipelineExecution(pipelineExecution).build();
    doReturn(mockExecution)
        .when(executionService)
        .getWorkflowExecution(eq(APP_ID), eq("WORKFLOW_EXECUTION_ID"), any(String[].class));

    ExecutionResponse executionResponse = envRollbackState.execute(context);

    verify(executionService).triggerRollbackExecutionWorkflow(APP_ID, mockExecution, true);
    assertThat(executionResponse.getStateExecutionData()).isInstanceOf(SkipStateExecutionData.class);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.QUEUED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.infra.InfraDefinitionTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.WorkflowExecution;
import software.wings.service.intfc.AppService;
import software.wings.sm.StateMachineExecutor;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by sgurubelli on 7/30/18.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ExecutionEventListenerTest extends WingsBaseTest {
  @Inject @InjectMocks private software.wings.service.impl.ExecutionEventListener executionEventListener;
  @Inject private HPersistence persistence;
  @Mock private StateMachineExecutor stateMachineExecutor;
  @Mock private AppService appService;

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNoQueueIfNotRunningOrPaused() throws Exception {
    persistence.save(WorkflowExecution.builder().appId(APP_ID).workflowId(WORKFLOW_ID).status(SUCCESS).build());

    executionEventListener.onMessage(ExecutionEvent.builder().appId(APP_ID).workflowId(WORKFLOW_ID).build());

    verify(stateMachineExecutor, times(0)).startQueuedExecution(APP_ID, WORKFLOW_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNoQueueIfNotQueued() throws Exception {
    persistence.save(WorkflowExecution.builder().appId(APP_ID).workflowId(WORKFLOW_ID).status(RUNNING).build());

    executionEventListener.onMessage(ExecutionEvent.builder().appId(APP_ID).workflowId(WORKFLOW_ID).build());

    verify(stateMachineExecutor, times(0)).startQueuedExecution(APP_ID, WORKFLOW_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotQueueBuildWorkflow() throws Exception {
    WorkflowExecution queuedExecution =
        WorkflowExecution.builder().appId(APP_ID).workflowId(WORKFLOW_ID).status(QUEUED).build();

    persistence.save(queuedExecution);

    when(stateMachineExecutor.startQueuedExecution(APP_ID, queuedExecution.getUuid())).thenReturn(true);
    executionEventListener.onMessage(ExecutionEvent.builder().appId(APP_ID).workflowId(WORKFLOW_ID).build());

    verify(stateMachineExecutor, never()).startQueuedExecution(anyString(), anyString());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotQueueWorkflow() throws Exception {
    WorkflowExecution queuedExecution = WorkflowExecution.builder()
                                            .infraMappingIds(asList(INFRA_MAPPING_ID))
                                            .appId(APP_ID)
                                            .workflowId(WORKFLOW_ID)
                                            .status(QUEUED)
                                            .build();

    persistence.save(queuedExecution);

    executionEventListener.onMessage(ExecutionEvent.builder()
                                         .infraMappingIds(asList(INFRA_MAPPING_ID))
                                         .appId(APP_ID)
                                         .workflowId(WORKFLOW_ID)
                                         .build());

    verify(stateMachineExecutor, never()).startQueuedExecution(anyString(), anyString());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldQueueWorkflow() throws Exception {
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);
    WorkflowExecution queuedExecution = WorkflowExecution.builder()
                                            .infraDefinitionIds(asList(INFRA_DEFINITION_ID))
                                            .appId(APP_ID)
                                            .workflowId(WORKFLOW_ID)
                                            .status(QUEUED)
                                            .build();

    persistence.save(queuedExecution);

    executionEventListener.onMessage(ExecutionEvent.builder()
                                         .infraDefinitionIds(asList(INFRA_DEFINITION_ID))
                                         .appId(APP_ID)
                                         .workflowId(WORKFLOW_ID)
                                         .build());

    verify(stateMachineExecutor, never()).startQueuedExecution(APP_ID, queuedExecution.getUuid());
  }
}

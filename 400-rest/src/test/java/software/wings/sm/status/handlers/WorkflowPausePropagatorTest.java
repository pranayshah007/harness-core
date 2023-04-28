/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.status.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.POOJA;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.sm.status.StateStatusUpdateInfo;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class WorkflowPausePropagatorTest extends WingsBaseTest {
  @Inject WingsPersistence wingsPersistence;
  @Inject private WorkflowPausePropagator pausePropagator;
  @Inject private AccountService accountService;

  public static final String APP_ID = generateUuid();
  public static final String APP_NAME = "App Name";
  public static final String ACCOUNT_ID = generateUuid();
  public static final String WORKFLOW_EXECUTION_ID = generateUuid();
  public static final String PIPELINE_EXECUTION_ID = generateUuid();
  public static final String STATE_EXECUTION_ID = generateUuid();

  @Before
  public void setupMocks() {
    Account account = anAccount()
                          .withUuid(ACCOUNT_ID)
                          .withAccountName(WingsTestConstants.ACCOUNT_NAME)
                          .withCompanyName(WingsTestConstants.COMPANY_NAME)
                          .withLicenseInfo(getLicenseInfo())
                          .build();
    accountService.save(account, false);
  }

  @Test

  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldTestHandleStatusUpdateForWorkflow() {
    buildAndSave(ExecutionStatus.PAUSED);
    StateStatusUpdateInfo updateInfo = StateStatusUpdateInfo.builder()
                                           .appId(APP_ID)
                                           .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                           .stateExecutionInstanceId(STATE_EXECUTION_ID)
                                           .status(ExecutionStatus.PAUSED)
                                           .build();
    pausePropagator.handleStatusUpdate(updateInfo);
    WorkflowExecution execution = fetchExecution(APP_ID, WORKFLOW_EXECUTION_ID);
    assertThat(execution).isNotNull();
    assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PAUSED);

    WorkflowExecution pipelineExecution = fetchExecution(APP_ID, PIPELINE_EXECUTION_ID);
    assertThat(pipelineExecution).isNotNull();
    assertThat(pipelineExecution.getStatus()).isEqualTo(ExecutionStatus.PAUSED);
  }

  private WorkflowExecution fetchExecution(String appId, String workflowExecutionId) {
    return wingsPersistence.createQuery(WorkflowExecution.class)
        .filter(WorkflowExecutionKeys.appId, appId)
        .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
        .get();
  }

  private void buildAndSave(ExecutionStatus internalStatus) {
    wingsPersistence.save(
        WorkflowExecution.builder()
            .uuid(PIPELINE_EXECUTION_ID)
            .appId(APP_ID)
            .appName(APP_NAME)
            .accountId(ACCOUNT_ID)
            .workflowId(generateUuid())
            .workflowType(WorkflowType.PIPELINE)
            .status(ExecutionStatus.RUNNING)
            .pipelineExecution(
                aPipelineExecution()
                    .withPipeline(Pipeline.builder()
                                      .pipelineStages(singletonList(PipelineStage.builder()
                                                                        .name("Stage 1")
                                                                        .pipelineStageElements(singletonList(
                                                                            PipelineStageElement.builder().build()))
                                                                        .build()))
                                      .build())
                    .withPipelineStageExecutions(
                        singletonList(PipelineStageExecution.builder().status(internalStatus).build()))
                    .build())
            .build());
    wingsPersistence.save(WorkflowExecution.builder()
                              .uuid(WORKFLOW_EXECUTION_ID)
                              .appId(APP_ID)
                              .appName(APP_NAME)
                              .accountId(ACCOUNT_ID)
                              .workflowId(generateUuid())
                              .workflowType(WorkflowType.ORCHESTRATION)
                              .pipelineExecutionId(PIPELINE_EXECUTION_ID)
                              .status(ExecutionStatus.RUNNING)
                              .build());

    wingsPersistence.save(
        Application.Builder.anApplication().name(APP_NAME).uuid(APP_ID).appId(APP_ID).accountId(ACCOUNT_ID).build());
  }
}

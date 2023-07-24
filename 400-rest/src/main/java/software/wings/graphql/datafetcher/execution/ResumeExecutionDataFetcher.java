/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.validation.Validator.notBlankCheck;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.execution.input.QLResumeExecutionInput;
import software.wings.graphql.schema.mutation.execution.payload.QLResumeExecutionPayload;
import software.wings.graphql.schema.type.QLPipelineExecution;
import software.wings.graphql.schema.type.QLPipelineExecution.QLPipelineExecutionBuilder;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Data Fetcher for Resume Execution GraphQL Api.
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ResumeExecutionDataFetcher
    extends BaseMutatorDataFetcher<QLResumeExecutionInput, QLResumeExecutionPayload> {
  public static final String ERROR_MESSAGE_EMPTY_APP_ID = "Empty app id.";
  public static final String ERROR_MESSAGE_EMPTY_PIPELINE_STAGE_NAME = "Empty pipeline stage name.";
  public static final String ERROR_MESSAGE_EMPTY_WORKFLOW_EXECUTION_ID = "Empty workflow execution Id.";
  public static final String ERROR_MESSAGE_EXECUTION_S_DOESN_T_EXIST = "Execution [%s] doesn't exist.";

  @Inject WorkflowExecutionService workflowExecutionService;
  @Inject PipelineExecutionController pipelineExecutionController;
  @Inject DeploymentAuthHandler deploymentAuthHandler;
  @Inject ExecutionController executionController;

  @Inject
  public ResumeExecutionDataFetcher() {
    super(QLResumeExecutionInput.class, QLResumeExecutionPayload.class);
  }

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLResumeExecutionPayload mutateAndFetch(QLResumeExecutionInput parameter, MutationContext mutationContext) {
    return resumePipelineExecution(parameter, mutationContext);
  }

  public QLResumeExecutionPayload resumePipelineExecution(
      QLResumeExecutionInput parameter, MutationContext mutationContext) {
    final String workflowExecutionId = parameter.getPipelineExecutionId();
    final String appId = parameter.getApplicationId();
    final String pipelineStageName = parameter.getPipelineStageName();
    final String clientMutationId = parameter.getClientMutationId();

    notBlankCheck(ERROR_MESSAGE_EMPTY_APP_ID, appId);
    notBlankCheck(ERROR_MESSAGE_EMPTY_PIPELINE_STAGE_NAME, pipelineStageName);
    notBlankCheck(ERROR_MESSAGE_EMPTY_WORKFLOW_EXECUTION_ID, workflowExecutionId);

    final WorkflowExecution previousWorkflowExecution = validateAndGetWorkflowExecution(appId, workflowExecutionId);
    deploymentAuthHandler.authorize(appId, previousWorkflowExecution);

    executionController.setCreatedByTypeInExecutionArgs(mutationContext, previousWorkflowExecution.getExecutionArgs());

    final WorkflowExecution resumedExecution =
        workflowExecutionService.triggerPipelineResumeExecution(appId, pipelineStageName, previousWorkflowExecution);

    final QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
    pipelineExecutionController.populatePipelineExecution(resumedExecution, builder);

    QLPipelineExecution pipelineExecution = builder.build();
    return QLResumeExecutionPayload.builder().clientMutationId(clientMutationId).execution(pipelineExecution).build();
  }

  @VisibleForTesting
  private WorkflowExecution validateAndGetWorkflowExecution(String appId, String workflowExecutionId) {
    String[] fields = {WorkflowExecutionKeys.appId, WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.envId,
        WorkflowExecutionKeys.executionArgs, WorkflowExecutionKeys.latestPipelineResume,
        WorkflowExecutionKeys.pipelineExecution, WorkflowExecutionKeys.pipelineResumeId,
        WorkflowExecutionKeys.pipelineSummary, WorkflowExecutionKeys.status, WorkflowExecutionKeys.workflowId,
        WorkflowExecutionKeys.workflowType};
    WorkflowExecution workflowExecution =
        workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId, fields);
    notNullCheck(String.format(ERROR_MESSAGE_EXECUTION_S_DOESN_T_EXIST, workflowExecutionId), workflowExecution);
    return workflowExecution;
  }
}

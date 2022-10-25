/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;

import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.execution.input.QLExecutionType;
import software.wings.graphql.schema.mutation.execution.input.QLStartExecutionInput;
import software.wings.graphql.schema.mutation.execution.payload.QLStartExecutionPayload;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class StartExecutionDataFetcher extends BaseMutatorDataFetcher<QLStartExecutionInput, QLStartExecutionPayload> {
  @Inject private PipelineExecutionController pipelineExecutionController;
  @Inject private WorkflowExecutionController workflowExecutionController;
  @Inject private AppService appService;
  @Inject private DeploymentAuthHandler deploymentAuthHandler;

  private static final String APPLICATION_DOES_NOT_EXIST_MSG = "Application does not exist";

  @Inject
  public StartExecutionDataFetcher(PipelineExecutionController pipelineExecutionController,
      WorkflowExecutionController workflowExecutionController, AppService appService,
      DeploymentAuthHandler deploymentAuthHandler) {
    super(QLStartExecutionInput.class, QLStartExecutionPayload.class);
    this.pipelineExecutionController = pipelineExecutionController;
    this.workflowExecutionController = workflowExecutionController;
    this.appService = appService;
    this.deploymentAuthHandler = deploymentAuthHandler;
  }

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLStartExecutionPayload mutateAndFetch(
      QLStartExecutionInput triggerExecutionInput, MutationContext mutationContext) {
    try (AutoLogContext ignore0 =
             new AccountLogContext(mutationContext.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      if (isEmpty(triggerExecutionInput.getEntityId())) {
        throw new InvalidRequestException("Entity Id cannot be empty", WingsException.USER);
      }

      validateAppBelongsToAccount(triggerExecutionInput, mutationContext);

      QLExecutionType executionType = triggerExecutionInput.getExecutionType();
      QLStartExecutionPayload response;
      switch (executionType) {
        case PIPELINE:
          deploymentAuthHandler.authorizePipelineExecution(
              triggerExecutionInput.getApplicationId(), triggerExecutionInput.getEntityId());
          response = pipelineExecutionController.startPipelineExecution(triggerExecutionInput, mutationContext);
          break;
        case WORKFLOW:
          deploymentAuthHandler.authorizeWorkflowExecution(
              triggerExecutionInput.getApplicationId(), triggerExecutionInput.getEntityId());
          response = workflowExecutionController.startWorkflowExecution(triggerExecutionInput, mutationContext);
          break;
        default:
          throw new InvalidRequestException("Unsupported execution type: " + executionType);
      }
      return response;
    }
  }

  private void validateAppBelongsToAccount(
      QLStartExecutionInput triggerExecutionInput, MutationContext mutationContext) {
    String appId = triggerExecutionInput.getApplicationId();
    if (isEmpty(appId)) {
      throw new InvalidRequestException("Application Id cannot be empty", WingsException.USER);
    }
    String accountIdFromApp = appService.getAccountIdByAppId(appId);
    if (EmptyPredicate.isEmpty(accountIdFromApp) || !accountIdFromApp.equals(mutationContext.getAccountId())) {
      throw new InvalidRequestException(APPLICATION_DOES_NOT_EXIST_MSG, WingsException.USER);
    }
  }
}

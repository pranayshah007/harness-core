/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.exception.GraphQLException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.ApprovalDetails;
import software.wings.beans.NameValuePair;
import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.approval.input.QLApproveOrRejectApprovalsInput;
import software.wings.graphql.schema.mutation.approval.payload.QLApproveOrRejectApprovalsPayload;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.states.ApprovalState.ApprovalStateType;

import com.google.inject.Inject;
import graphql.GraphQLContext;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDC)
public class ApproveOrRejectApprovalsDataFetcher
    extends BaseMutatorDataFetcher<QLApproveOrRejectApprovalsInput, QLApproveOrRejectApprovalsPayload> {
  private WingsPersistence persistence;
  private WorkflowExecutionService workflowExecutionService;
  private DeploymentAuthHandler deploymentAuthHandler;
  private FeatureFlagService featureFlagService;

  @Inject
  public ApproveOrRejectApprovalsDataFetcher(WorkflowExecutionService workflowExecutionService,
      DeploymentAuthHandler deploymentAuthHandler, WingsPersistence persistence,
      FeatureFlagService featureFlagService) {
    super(QLApproveOrRejectApprovalsInput.class, QLApproveOrRejectApprovalsPayload.class);
    this.workflowExecutionService = workflowExecutionService;
    this.deploymentAuthHandler = deploymentAuthHandler;
    this.persistence = persistence;
    this.featureFlagService = featureFlagService;
  }

  @Override
  @AuthRule(permissionType = LOGGED_IN)
  protected QLApproveOrRejectApprovalsPayload mutateAndFetch(
      QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput, MutationContext mutationContext) {
    final String executionId = approveOrRejectApprovalsInput.getExecutionId();
    WorkflowExecution execution =
        persistence.createAuthorizedQuery(WorkflowExecution.class).filter("_id", executionId).get();
    if (execution == null) {
      throw new InvalidRequestException("Execution does not exist or access is denied", WingsException.USER);
    }

    GraphQLContext graphQLContext = mutationContext.getDataFetchingEnvironment().getContext();
    ApiKeyEntry apiKeyEntry = graphQLContext.get("apiKeyEntry");

    ApprovalDetails approvalDetails = constructApprovalDetailsFromInput(approveOrRejectApprovalsInput, apiKeyEntry);
    final String appId = approveOrRejectApprovalsInput.getApplicationId();
    ApprovalStateExecutionData approvalStateExecutionData =
        workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
            appId, executionId, null, approvalDetails);

    verifyApproveOrRejectApprovalsInput(approveOrRejectApprovalsInput, approvalStateExecutionData);

    if (approvalStateExecutionData.isAutoRejectPreviousDeployments()
        && approvalDetails.getAction() == ApprovalDetails.Action.APPROVE
        && featureFlagService.isEnabled(FeatureName.AUTO_REJECT_PREVIOUS_APPROVALS, execution.getAccountId())) {
      workflowExecutionService.rejectPreviousDeployments(appId, executionId, approvalDetails);
    }

    if (isEmpty(approvalStateExecutionData.getUserGroups())) {
      deploymentAuthHandler.authorize(appId, executionId);
    }

    boolean success = workflowExecutionService.approveOrRejectExecution(
        appId, approvalStateExecutionData.getUserGroups(), approvalDetails, apiKeyEntry);

    return QLApproveOrRejectApprovalsPayload.builder()
        .success(success)
        .clientMutationId(approveOrRejectApprovalsInput.getClientMutationId())
        .build();
  }

  private void verifyApproveOrRejectApprovalsInput(QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput,
      ApprovalStateExecutionData approvalStateExecutionData) {
    if (!ApprovalStateType.USER_GROUP.equals(approvalStateExecutionData.getApprovalStateType())) {
      throw new GraphQLException(
          approvalStateExecutionData.getApprovalStateType() + " Approval Type not supported", USER);
    }
    if (approveOrRejectApprovalsInput.getVariableInputs() != null) {
      if (approvalStateExecutionData.getVariables() == null) {
        throw new InvalidRequestException("Variable were not present for the given approval");
      }
      approveOrRejectApprovalsInput.getVariableInputs().forEach(nameValuePair -> {
        if (approvalStateExecutionData.getVariables() != null
            && !approvalStateExecutionData.getVariables()
                    .stream()
                    .map(NameValuePair::getName)
                    .collect(Collectors.toSet())
                    .contains(nameValuePair.getName())) {
          throw new InvalidRequestException("Variable with name \"" + nameValuePair.getName() + "\" not present");
        }
      });
    }
  }

  private ApprovalDetails constructApprovalDetailsFromInput(
      QLApproveOrRejectApprovalsInput approveOrRejectApprovalsInput, ApiKeyEntry apiKeyEntry) {
    EmbeddedUser user = null;
    boolean isApprovedViaApiKey = false;
    if (apiKeyEntry != null) {
      user = EmbeddedUser.builder().uuid(apiKeyEntry.getUuid()).name(apiKeyEntry.getName()).build();
      isApprovedViaApiKey = true;
    }

    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setAction(approveOrRejectApprovalsInput.getAction());
    approvalDetails.setApprovalId(approveOrRejectApprovalsInput.getApprovalId());
    approvalDetails.setApprovedBy(user);
    approvalDetails.setComments(approveOrRejectApprovalsInput.getComments());
    approvalDetails.setVariables(approveOrRejectApprovalsInput.getVariableInputs());
    approvalDetails.setApprovalFromGraphQL(true);
    approvalDetails.setApprovalViaApiKey(isApprovedViaApiKey);

    return approvalDetails;
  }
}

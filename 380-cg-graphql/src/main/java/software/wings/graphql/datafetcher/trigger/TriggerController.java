/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.WorkflowType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;

import software.wings.beans.Pipeline;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerBuilder;
import software.wings.graphql.datafetcher.execution.PipelineExecutionController;
import software.wings.graphql.datafetcher.execution.WorkflowExecutionController;
import software.wings.graphql.datafetcher.user.UserController;
import software.wings.graphql.schema.mutation.execution.input.QLExecutionType;
import software.wings.graphql.schema.mutation.execution.input.QLVariableInput;
import software.wings.graphql.schema.type.trigger.QLArtifactSelectionInput;
import software.wings.graphql.schema.type.trigger.QLConditionType;
import software.wings.graphql.schema.type.trigger.QLCreateOrUpdateTriggerInput;
import software.wings.graphql.schema.type.trigger.QLTrigger;
import software.wings.graphql.schema.type.trigger.QLTrigger.QLTriggerBuilder;
import software.wings.graphql.schema.type.trigger.QLTriggerPayload;
import software.wings.security.ExecutableElementsFilter;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class TriggerController {
  @Inject AppService appService;
  @Inject TriggerActionController triggerActionController;
  @Inject TriggerConditionController triggerConditionController;
  @Inject HPersistence persistence;
  @Inject DeploymentAuthHandler deploymentAuthHandler;
  @Inject PipelineService pipelineService;
  @Inject WorkflowService workflowService;
  @Inject AuthService authService;
  @Inject WorkflowExecutionController workflowExecutionController;
  @Inject PipelineExecutionController pipelineExecutionController;
  @Inject SettingsService settingsService;
  @Inject FeatureFlagService featureFlagService;

  public void populateTrigger(Trigger trigger, QLTriggerBuilder qlTriggerBuilder, String accountId) {
    qlTriggerBuilder.id(trigger.getUuid())
        .name(trigger.getName())
        .description(trigger.getDescription())
        .condition(triggerConditionController.populateTriggerCondition(trigger, accountId))
        .action(triggerActionController.populateTriggerAction(trigger))
        .createdAt(trigger.getCreatedAt())
        .excludeHostsWithSameArtifact(trigger.isExcludeHostsWithSameArtifact())
        .createdBy(UserController.populateUser(trigger.getCreatedBy()));
  }

  public QLTriggerPayload prepareQLTrigger(Trigger trigger, String clientMutationId, String accountId) {
    QLTriggerBuilder builder = QLTrigger.builder();
    populateTrigger(trigger, builder, accountId);
    return QLTriggerPayload.builder().clientMutationId(clientMutationId).trigger(builder.build()).build();
  }

  public Trigger prepareTrigger(QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput, String accountId) {
    validateTrigger(qlCreateOrUpdateTriggerInput, accountId);

    TriggerBuilder triggerBuilder = Trigger.builder();
    triggerBuilder.uuid(qlCreateOrUpdateTriggerInput.getTriggerId());
    triggerBuilder.name(qlCreateOrUpdateTriggerInput.getName().trim());
    triggerBuilder.appId(qlCreateOrUpdateTriggerInput.getApplicationId());
    triggerBuilder.description(qlCreateOrUpdateTriggerInput.getDescription());

    // EntityId not empty is validated in validateTrigger
    triggerBuilder.workflowId(qlCreateOrUpdateTriggerInput.getAction().getEntityId());

    triggerBuilder.workflowType(triggerActionController.resolveWorkflowType(qlCreateOrUpdateTriggerInput));
    triggerBuilder.continueWithDefaultValues(
        triggerActionController.resolveContinueWithDefault(qlCreateOrUpdateTriggerInput));

    Boolean excludeHostsWithSameArtifact = qlCreateOrUpdateTriggerInput.getAction().getExcludeHostsWithSameArtifact();
    if (excludeHostsWithSameArtifact != null) {
      triggerBuilder.excludeHostsWithSameArtifact(excludeHostsWithSameArtifact);
    }

    String envId = null;
    Map<String, String> resolvedWorkflowVariables = null;
    String appId = qlCreateOrUpdateTriggerInput.getApplicationId();
    List<QLVariableInput> variables = qlCreateOrUpdateTriggerInput.getAction().getVariables();

    switch (qlCreateOrUpdateTriggerInput.getAction().getExecutionType()) {
      case WORKFLOW:
        String workflowId = qlCreateOrUpdateTriggerInput.getAction().getEntityId();
        Workflow workflow = workflowService.readWorkflow(appId, workflowId);
        validateWorkflow(appId, workflowId, workflow);

        envId = workflowExecutionController.resolveEnvId(workflow, variables);
        deploymentAuthHandler.authorizeWorkflowExecution(appId, workflowId);

        authService.checkIfUserAllowedToDeployWorkflowToEnv(appId, envId);

        resolvedWorkflowVariables =
            triggerActionController.validateAndResolveWorkflowVariables(variables, workflow, envId);
        triggerBuilder.workflowVariables(resolvedWorkflowVariables);
        validateAndSetArtifactSelectionsWorkflow(
            resolvedWorkflowVariables, qlCreateOrUpdateTriggerInput, workflow, triggerBuilder);
        triggerActionController.validateAndSetManifestSelectionsWorkflow(
            resolvedWorkflowVariables, qlCreateOrUpdateTriggerInput, workflow, triggerBuilder);
        triggerBuilder.continueWithDefaultValues(false);
        break;
      case PIPELINE:
        String pipelineId = qlCreateOrUpdateTriggerInput.getAction().getEntityId();
        Pipeline pipeline = pipelineService.readPipeline(appId, pipelineId, true);
        validatePipeline(appId, pipelineId, pipeline);
        deploymentAuthHandler.authorizePipelineExecution(appId, pipelineId);

        envId = pipelineExecutionController.resolveEnvId(pipeline, variables, true);
        authService.checkIfUserAllowedToDeployPipelineToEnv(appId, envId);
        if (featureFlagService.isEnabled(PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION, pipeline.getAccountId())) {
          deploymentAuthHandler.authorizeExecutableDeployableInEnv(
              new HashSet<>(pipeline.getEnvIds()), appId, pipelineId, ExecutableElementsFilter.FilterType.PIPELINE);
        }
        resolvedWorkflowVariables =
            triggerActionController.validateAndResolvePipelineVariables(variables, pipeline, envId);
        triggerBuilder.workflowVariables(resolvedWorkflowVariables);
        validateAndSetArtifactSelectionsPipeline(
            resolvedWorkflowVariables, qlCreateOrUpdateTriggerInput, pipeline, triggerBuilder);
        triggerActionController.validateAndSetManifestSelectionsPipeline(
            resolvedWorkflowVariables, qlCreateOrUpdateTriggerInput, pipeline, triggerBuilder);
        break;
      default:
    }

    triggerBuilder.condition(triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput));

    return triggerBuilder.build();
  }

  void validateWorkflow(String appId, String workflowId, Workflow workflow) {
    notNullCheck("Workflow " + workflowId + " doesn't exist in the specified application " + appId, workflow, USER);
    notNullCheck(
        "Error reading workflow " + workflowId + " Might be deleted", workflow.getOrchestrationWorkflow(), USER);
  }

  void validatePipeline(String appId, String pipelineId, Pipeline pipeline) {
    notNullCheck("Pipeline " + pipelineId + " doesn't exist in the specified application " + appId, pipeline, USER);
  }

  void validateTrigger(QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput, String accountId) {
    validateUpdateTrigger(qlCreateOrUpdateTriggerInput, accountId);

    if (EmptyPredicate.isEmpty(qlCreateOrUpdateTriggerInput.getApplicationId())) {
      throw new InvalidRequestException("ApplicationId must not be empty", USER);
    }
    if (!accountId.equals(appService.getAccountIdByAppId(qlCreateOrUpdateTriggerInput.getApplicationId()))) {
      throw new InvalidRequestException("ApplicationId doesn't belong to this account", USER);
    }

    if (EmptyPredicate.isEmpty(qlCreateOrUpdateTriggerInput.getName().trim())) {
      throw new InvalidRequestException("Trigger name must not be empty", USER);
    }

    if (EmptyPredicate.isEmpty(qlCreateOrUpdateTriggerInput.getAction().getEntityId())) {
      throw new InvalidRequestException("Entity Id must not be empty", USER);
    }

    validateGitConnector(qlCreateOrUpdateTriggerInput, accountId);
  }

  private void validateUpdateTrigger(QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput, String accountId) {
    if (EmptyPredicate.isNotEmpty(qlCreateOrUpdateTriggerInput.getTriggerId())) {
      Trigger trigger = persistence.get(Trigger.class, qlCreateOrUpdateTriggerInput.getTriggerId());
      if (trigger == null) {
        throw new InvalidRequestException("Trigger doesn't exist", USER);
      }

      if (!accountId.equals(appService.getAccountIdByAppId(trigger.getAppId()))) {
        throw new InvalidRequestException("Trigger doesn't exist", USER);
      }

      QLExecutionType qlExecutionType =
          trigger.getWorkflowType() == WorkflowType.ORCHESTRATION ? QLExecutionType.WORKFLOW : QLExecutionType.PIPELINE;

      if (qlCreateOrUpdateTriggerInput.getAction().getExecutionType() != qlExecutionType) {
        throw new InvalidRequestException("Execution Type cannot be modified", USER);
      }
    }
  }

  private void validateGitConnector(QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput, String accountId) {
    if (qlCreateOrUpdateTriggerInput.getCondition().getConditionType() == QLConditionType.ON_WEBHOOK) {
      if (null != qlCreateOrUpdateTriggerInput.getCondition().getWebhookConditionInput()) {
        Boolean deployOnlyIfFilesChanged =
            qlCreateOrUpdateTriggerInput.getCondition().getWebhookConditionInput().getDeployOnlyIfFilesChanged();

        if (deployOnlyIfFilesChanged != null && deployOnlyIfFilesChanged) {
          validateGitConnectorChecks(
              qlCreateOrUpdateTriggerInput.getCondition().getWebhookConditionInput().getGitConnectorId(), accountId,
              qlCreateOrUpdateTriggerInput.getApplicationId());
        }
      }
    }
  }

  private void validateGitConnectorChecks(String gitConnectorId, String accountId, String appId) {
    if (EmptyPredicate.isNotEmpty(gitConnectorId)) {
      SettingAttribute gitConfig = settingsService.get(gitConnectorId);
      if (gitConfig == null) {
        throw new InvalidRequestException(String.format("GitConnector: %s doesn't exists", gitConnectorId), USER);
      }
      if (!accountId.equals(gitConfig.getAccountId())) {
        throw new InvalidRequestException(
            String.format("GitConnector: %s doesn't belong to this account", gitConnectorId), USER);
      }
      if (settingsService.getFilteredSettingAttributes(Collections.singletonList(gitConfig), appId, null, false)
              .isEmpty()) {
        throw new InvalidRequestException(
            String.format("User doesn't have access to use the GitConnector: %s", gitConnectorId), USER);
      }
    }
  }

  void validateAndSetArtifactSelectionsWorkflow(Map<String, String> variables,
      QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput, Workflow workflow, TriggerBuilder triggerBuilder) {
    /* Fetch the deployment data to find out the required artifacts */
    List<QLArtifactSelectionInput> artifactSelections =
        qlCreateOrUpdateTriggerInput.getAction().getArtifactSelections();
    DeploymentMetadata deploymentMetadata = workflowService.fetchDeploymentMetadata(
        workflow.getAppId(), workflow, variables, null, null, DeploymentMetadata.Include.ARTIFACT_SERVICE);

    List<String> artifactNeededServiceIds =
        deploymentMetadata == null ? new ArrayList<>() : deploymentMetadata.getArtifactRequiredServiceIds();
    validateArtifactSelections(artifactSelections, artifactNeededServiceIds);
    triggerBuilder.artifactSelections(
        triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, artifactNeededServiceIds));
  }

  void validateAndSetArtifactSelectionsPipeline(Map<String, String> variables,
      QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput, Pipeline pipeline, TriggerBuilder triggerBuilder) {
    /* Fetch the deployment data to find out the required entity types */
    List<QLArtifactSelectionInput> artifactSelections =
        qlCreateOrUpdateTriggerInput.getAction().getArtifactSelections();
    DeploymentMetadata deploymentMetadata = pipelineService.fetchDeploymentMetadata(
        pipeline.getAppId(), pipeline.getUuid(), variables, null, null, false, null);

    List<String> artifactNeededServiceIds =
        deploymentMetadata == null ? new ArrayList<>() : deploymentMetadata.getArtifactRequiredServiceIds();
    validateArtifactSelections(artifactSelections, artifactNeededServiceIds);
    triggerBuilder.artifactSelections(
        triggerActionController.resolveArtifactSelections(qlCreateOrUpdateTriggerInput, artifactNeededServiceIds));
  }

  private void validateArtifactSelections(
      List<QLArtifactSelectionInput> artifactSelections, List<String> artifactNeededServiceIds) {
    if (artifactSelections == null) {
      artifactSelections = new ArrayList<>();
    }

    for (String id : artifactNeededServiceIds) {
      if (artifactSelections.stream().noneMatch(e -> id.equals(e.getServiceId()))) {
        throw new InvalidRequestException(
            String.format("Artifact Source for service id: %s must be specified", id), USER);
      }
    }
  }
}

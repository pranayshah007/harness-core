/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.azure.model.AzureConstants.ARM_DEPLOYMENT_STATUS_CHECK_INTERVAL;
import static io.harness.azure.model.AzureConstants.ROLE_ASSIGNMENT_EXISTS_CLOUD_ERROR_CODE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureBlueprintClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.model.blueprint.Blueprint;
import io.harness.azure.model.blueprint.PublishedBlueprint;
import io.harness.azure.model.blueprint.artifact.Artifact;
import io.harness.azure.model.blueprint.assignment.Assignment;
import io.harness.azure.model.blueprint.assignment.WhoIsBlueprintContract;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentBlueprintSteadyStateContext;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.delegatetasks.azure.arm.deployment.context.DeploymentBlueprintContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.CloudError;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.graphrbac.BuiltInRole;
import com.microsoft.azure.management.graphrbac.RoleAssignment;
import com.microsoft.azure.management.network.ResourceIdentityType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(HarnessTeam.CDP)
public class AzureBlueprintDeploymentService {
  public static final String CREATING = "creating";
  public static final String UPDATING = "updating";
  public static final String CREATED = "created";
  public static final String UPDATED = "updated";

  @Inject private AzureBlueprintClient azureBlueprintClient;
  @Inject private AzureAuthorizationClient azureAuthorizationClient;
  @Inject private ARMDeploymentSteadyStateChecker deploymentSteadyStateChecker;

  public void deployBlueprintAtResourceScope(DeploymentBlueprintContext context) {
    LogCallback blueprintDeploymentLogCallback = getBlueprintDeploymentLogCallback(context);
    blueprintDeploymentLogCallback.saveExecutionLog(String.format(
        "Starting Blueprint deployment %n- Blueprint Name: [%s] %n- Assignment Name: [%s] %n- Version ID: [%s]",
        context.getBlueprintName(), context.getAssignment().getName(), context.getVersionId()));

    try {
      checkExistingBlueprint(context, blueprintDeploymentLogCallback);

      if (isBlueprintPublished(context, blueprintDeploymentLogCallback)) {
        createAssignment(context, blueprintDeploymentLogCallback);
      } else {
        deployBlueprint(context, blueprintDeploymentLogCallback);
      }
    } catch (Exception ex) {
      String message = AzureResourceUtility.getAzureCloudExceptionMessage(ex);
      blueprintDeploymentLogCallback.saveExecutionLog(
          format("%nBlueprint deployment failed."), LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new InvalidRequestException(format(
          "Unable to deploy blueprint, Definition Scope: %s, Blueprint Name: %s, Assignment Name: %s, Assignment Scope: %s, Error msg: %s",
          context.getDefinitionResourceScope(), context.getBlueprintName(), context.getAssignment().getName(),
          context.getAssignmentResourceScope(), message));
    }
    blueprintDeploymentLogCallback.saveExecutionLog(
        format("%nBlueprint is assigned successfully."), LogLevel.INFO, SUCCESS);

    performSteadyStateCheck(context);
  }

  private void checkExistingBlueprint(DeploymentBlueprintContext context, LogCallback blueprintDeploymentLogCallback) {
    AzureConfig azureConfig = context.getAzureConfig();
    String definitionResourceScope = context.getDefinitionResourceScope();
    String blueprintName = context.getBlueprintName();

    blueprintDeploymentLogCallback.saveExecutionLog(
        String.format("%nStart getting exiting blueprint definition at scope %n- Scope: [%s] %n- Blueprint Name: [%s]",
            definitionResourceScope, blueprintName));

    Blueprint blueprint = azureBlueprintClient.getBlueprint(azureConfig, definitionResourceScope, blueprintName);
    context.setExistingBlueprint(blueprint);

    blueprintDeploymentLogCallback.saveExecutionLog(blueprint == null
            ? "Not found blueprint definition at requested scope"
            : "Found blueprint definition at requested scope");
  }

  private boolean isBlueprintPublished(DeploymentBlueprintContext context, LogCallback blueprintDeploymentLogCallback) {
    AzureConfig azureConfig = context.getAzureConfig();
    String resourceScope = context.getDefinitionResourceScope();
    String blueprintName = context.getBlueprintName();
    String versionId = context.getVersionId();

    blueprintDeploymentLogCallback.saveExecutionLog(
        String.format("Start getting already published blueprint with version - [%s]", versionId));

    Optional<PublishedBlueprint> publishedBlueprintVersion =
        getPublishedBlueprintVersion(azureConfig, resourceScope, blueprintName, versionId);

    if (publishedBlueprintVersion.isPresent()) {
      blueprintDeploymentLogCallback.saveExecutionLog(
          String.format("Found already published blueprint with display name - [%s], continue assignment",
              publishedBlueprintVersion.get().getDisplayName()));
      return true;
    } else {
      blueprintDeploymentLogCallback.saveExecutionLog("Not found published blueprint, continue deployment");
    }
    return false;
  }

  private Optional<PublishedBlueprint> getPublishedBlueprintVersion(
      AzureConfig azureConfig, String resourceScope, String blueprintName, String versionId) {
    return Optional.ofNullable(
        azureBlueprintClient.getPublishedBlueprintVersion(azureConfig, resourceScope, blueprintName, versionId));
  }

  private void deployBlueprint(DeploymentBlueprintContext context, LogCallback blueprintDeploymentLogCallback) {
    createBlueprintDefinition(context, blueprintDeploymentLogCallback);
    createArtifacts(context, blueprintDeploymentLogCallback);
    publishBlueprintDefinition(context, blueprintDeploymentLogCallback);
    createAssignment(context, blueprintDeploymentLogCallback);
  }

  private void createBlueprintDefinition(
      final DeploymentBlueprintContext context, LogCallback blueprintDeploymentLogCallback) {
    String definitionResourceScope = context.getDefinitionResourceScope();
    String blueprintName = context.getBlueprintName();
    String creatingOrUpdating = context.getExistingBlueprint() == null ? CREATING : UPDATING;
    String createdOrUpdated = context.getExistingBlueprint() == null ? CREATED : UPDATED;

    blueprintDeploymentLogCallback.saveExecutionLog(
        format("%nStart %s blueprint definition %n- Scope: [%s]", creatingOrUpdating, definitionResourceScope));

    azureBlueprintClient.createOrUpdateBlueprint(
        context.getAzureConfig(), definitionResourceScope, blueprintName, context.getBlueprintJSON());

    blueprintDeploymentLogCallback.saveExecutionLog(format("Blueprint definition %s successfully", createdOrUpdated));
  }

  private void createArtifacts(final DeploymentBlueprintContext context, LogCallback blueprintDeploymentLogCallback) {
    AzureConfig azureConfig = context.getAzureConfig();
    String definitionResourceScope = context.getDefinitionResourceScope();
    String blueprintName = context.getBlueprintName();
    Map<String, String> artifacts = context.getArtifacts();
    String creatingOrUpdating = context.getExistingBlueprint() == null ? CREATING : UPDATING;
    String createdOrUpdated = context.getExistingBlueprint() == null ? CREATED : UPDATED;

    blueprintDeploymentLogCallback.saveExecutionLog(format("Start %s blueprint artifacts", creatingOrUpdating));

    List<Artifact> result = new ArrayList<>();
    artifacts.forEach((artifactName, artifactJSON) -> {
      blueprintDeploymentLogCallback.saveExecutionLog(
          StringUtils.capitalize(format("%s artifact with name - [%s]", creatingOrUpdating, artifactName)));

      Artifact artifact = azureBlueprintClient.createOrUpdateArtifact(
          azureConfig, definitionResourceScope, blueprintName, artifactName, artifactJSON);

      blueprintDeploymentLogCallback.saveExecutionLog(format("Artifact %s successfully", createdOrUpdated));

      result.add(artifact);
    });

    blueprintDeploymentLogCallback.saveExecutionLog(
        format("All artifacts %s successfully. Number of %s artifacts - [%s]", createdOrUpdated, createdOrUpdated,
            result.size()));
  }

  private void publishBlueprintDefinition(
      final DeploymentBlueprintContext context, LogCallback blueprintDeploymentLogCallback) {
    String definitionResourceScope = context.getDefinitionResourceScope();
    String blueprintName = context.getBlueprintName();
    String versionId = context.getVersionId();

    blueprintDeploymentLogCallback.saveExecutionLog(
        format("%nStart publishing blueprint definition %n- Blueprint Definition Name: [%s]%n- New Version Id: [%s]",
            blueprintName, versionId));

    azureBlueprintClient.publishBlueprintDefinition(
        context.getAzureConfig(), definitionResourceScope, blueprintName, versionId);

    blueprintDeploymentLogCallback.saveExecutionLog("Blueprint published successfully");
  }

  private void createAssignment(final DeploymentBlueprintContext context, LogCallback blueprintDeploymentLogCallback) {
    boolean assignmentExists = checkExistingAssignment(context, blueprintDeploymentLogCallback);
    grantAzureBlueprintsSPOwnerRole(context, blueprintDeploymentLogCallback);
    AzureConfig azureConfig = context.getAzureConfig();
    String assignmentResourceScope = context.getAssignmentResourceScope();
    String assignmentName = context.getAssignment().getName();
    String assignmentJSON = context.getAssignmentJSON();
    String creatingOrUpdating = assignmentExists ? UPDATING : CREATING;

    blueprintDeploymentLogCallback.saveExecutionLog(
        format("%nStart %s assignment %n- Scope: [%s] %n- Assignment Name: [%s]", creatingOrUpdating,
            assignmentResourceScope, assignmentName));

    azureBlueprintClient.beginCreateOrUpdateAssignment(
        azureConfig, assignmentResourceScope, assignmentName, assignmentJSON);

    blueprintDeploymentLogCallback.saveExecutionLog("Blueprint assignment request sent successfully");
  }

  private boolean checkExistingAssignment(
      DeploymentBlueprintContext context, LogCallback blueprintDeploymentLogCallback) {
    String assignmentResourceScope = context.getAssignmentResourceScope();
    String assignmentName = context.getAssignment().getName();

    blueprintDeploymentLogCallback.saveExecutionLog(
        String.format("%nChecking existing assignment at scope %n- Scope: [%s] %n- Assignment Name: [%s]",
            assignmentResourceScope, assignmentName));

    Assignment assignment =
        azureBlueprintClient.getAssignment(context.getAzureConfig(), assignmentResourceScope, assignmentName);

    blueprintDeploymentLogCallback.saveExecutionLog(assignment == null
            ? format("Not found assignment at requested scope %n")
            : format("Found existing assignment at requested scope %n"));

    return assignment != null && isNotBlank(assignment.getId());
  }

  private void grantAzureBlueprintsSPOwnerRole(
      final DeploymentBlueprintContext context, final LogCallback blueprintDeploymentLogCallback) {
    AzureConfig azureConfig = context.getAzureConfig();
    String assignmentResourceScope = context.getAssignmentResourceScope();
    String roleAssignmentName = context.getRoleAssignmentName();
    String assignmentSubscriptionId = context.getAssignmentSubscriptionId();
    Assignment assignment = context.getAssignment();
    String assignmentName = assignment.getName();

    blueprintDeploymentLogCallback.saveExecutionLog("Start granting the rights to Azure Blueprints service principal");

    if (ResourceIdentityType.SYSTEM_ASSIGNED == assignment.getIdentity().type()) {
      blueprintDeploymentLogCallback.saveExecutionLog("Assignment is using system-assigned managed identity. "
          + "Owner rights need to be assign to Azure Blueprints service principal");
      blueprintDeploymentLogCallback.saveExecutionLog(
          format("Start getting Azure Blueprints Service Principal details at scope - [%s]", assignmentResourceScope));

      WhoIsBlueprintContract whoIsBlueprintContract =
          azureBlueprintClient.whoIsBlueprint(azureConfig, assignmentResourceScope, assignmentName);
      String objectId = whoIsBlueprintContract.getObjectId();

      blueprintDeploymentLogCallback.saveExecutionLog(format(
          "Azure Blueprints Service Principal details successfully obtained - Azure Blueprints SP Object ID: [%s]",
          objectId));

      blueprintDeploymentLogCallback.saveExecutionLog(format(
          "Start creating role assignment for Azure Blueprints Service Principal on subscription"
              + "%n- Role Assignment Name: [%s] %n- Built In Role: [%s] %n- Azure Blueprints SP Object ID: [%s] %n- Subscription Id: [%s]",
          roleAssignmentName, BuiltInRole.OWNER.toString(), objectId, assignmentSubscriptionId));

      try {
        RoleAssignment roleAssignment = azureAuthorizationClient.roleAssignmentAtSubscriptionScope(
            azureConfig, assignmentSubscriptionId, objectId, roleAssignmentName, BuiltInRole.OWNER);

        blueprintDeploymentLogCallback.saveExecutionLog(format(
            "Role assignment successfully created %n- Principal ID: [%s] %n- Role Definition ID: [%s] %n- Scope: [%s]",
            roleAssignment.principalId(), roleAssignment.roleDefinitionId(), roleAssignment.scope()));

      } catch (CloudException ex) {
        CloudError body = ex.body();
        if (body != null && ROLE_ASSIGNMENT_EXISTS_CLOUD_ERROR_CODE.equals(body.code())) {
          blueprintDeploymentLogCallback.saveExecutionLog(
              format("The role assignment already exists. %n- Scope: [%s]", assignmentResourceScope));
        } else {
          throw ex;
        }
      }
    } else {
      blueprintDeploymentLogCallback.saveExecutionLog(format("Assignment is using user-assigned managed identity. "
              + "User is responsible for managing the rights and lifecycle of a user-assigned managed identity. "
              + "%n- Principal ID: [%s]",
          assignment.getIdentity().principalId()));
    }
  }

  private void performSteadyStateCheck(DeploymentBlueprintContext context) {
    LogCallback blueprintDeploymentSteadyStateLogCallback = getBlueprintDeploymentSteadyStateLogCallback(context);

    deploymentSteadyStateChecker.waitUntilCompleteWithTimeout(toBlueprintDeploymentSteadyStateContext(context),
        azureBlueprintClient, blueprintDeploymentSteadyStateLogCallback);
  }

  private DeploymentBlueprintSteadyStateContext toBlueprintDeploymentSteadyStateContext(
      DeploymentBlueprintContext context) {
    return DeploymentBlueprintSteadyStateContext.builder()
        .azureConfig(context.getAzureConfig())
        .assignmentResourceScope(context.getAssignmentResourceScope())
        .assignmentName(context.getAssignment().getName())
        .statusCheckIntervalInSeconds(ARM_DEPLOYMENT_STATUS_CHECK_INTERVAL)
        .steadyCheckTimeoutInMinutes(context.getSteadyStateTimeoutInMin())
        .build();
  }

  private LogCallback getBlueprintDeploymentLogCallback(DeploymentBlueprintContext context) {
    return context.getLogStreamingTaskClient().obtainLogCallback(AzureConstants.BLUEPRINT_DEPLOYMENT);
  }

  private LogCallback getBlueprintDeploymentSteadyStateLogCallback(DeploymentBlueprintContext context) {
    return context.getLogStreamingTaskClient().obtainLogCallback(AzureConstants.BLUEPRINT_DEPLOYMENT_STEADY_STATE);
  }
}

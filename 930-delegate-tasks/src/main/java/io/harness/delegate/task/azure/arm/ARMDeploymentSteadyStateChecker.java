/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureBlueprintClient;
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.context.ARMDeploymentSteadyStateContext;
import io.harness.azure.model.blueprint.assignment.Assignment;
import io.harness.azure.model.blueprint.assignment.AssignmentProvisioningState;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentDeploymentJob;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentDeploymentJobResult;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentJobCreatedResource;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentOperation;
import io.harness.azure.model.blueprint.assignment.operation.AzureResourceManagerError;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentBlueprintSteadyStateContext;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.AzureARMTaskException;
import io.harness.exception.runtime.azure.AzureARMDeploymentException;
import io.harness.exception.runtime.azure.AzureARMRuntimeException;
import io.harness.exception.runtime.azure.AzureBPDeploymentException;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.polling.PollResult;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.resourcemanager.resources.fluent.models.DeploymentOperationInner;
import com.azure.resourcemanager.resources.models.DeploymentOperationProperties;
import com.azure.resourcemanager.resources.models.StatusMessage;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
public class ARMDeploymentSteadyStateChecker {
  @Inject protected TimeLimiter timeLimiter;

  public <SyncPollerResult, ResponseObject> void waitUntilCompleteWithTimeout(ARMDeploymentSteadyStateContext context,
      AzureManagementClient azureManagementClient, LogCallback logCallback,
      SyncPoller<SyncPollerResult, ResponseObject> syncPoller) {
    try {
      Callable<Object> objectCallable = () -> {
        while (true) {
          PollResponse<SyncPollerResult> syncPollResponse = syncPoller.poll();

          logCallback.saveExecutionLog(String.format(
              "Deployment Status for - [%s] is [%s]", context.getDeploymentName(), syncPollResponse.getStatus()));

          if (syncPollResponse.getStatus().isComplete()) {
            String errorMessage = printProvisioningResults(context, azureManagementClient, logCallback);

            if (syncPollResponse.getStatus() == LongRunningOperationStatus.SUCCESSFULLY_COMPLETED) {
              logCallback.saveExecutionLog(
                  color(format("%nARM Deployment - [%s] completed successfully", context.getDeploymentName()),
                      LogColor.White, LogWeight.Bold),
                  INFO, SUCCESS);
              return Boolean.TRUE;
            } else {
              SyncPollerResult result = syncPollResponse.getValue();
              if (result instanceof PollResult) {
                PollResult.Error error = ((PollResult) result).getError();
                if (error != null) {
                  errorMessage = format("%s%n%s", error, error.getMessage());
                }
              }

              String message = format(
                  "%nARM Deployment failed for deployment - [%s]%n[%s]", context.getDeploymentName(), errorMessage);
              logCallback.saveExecutionLog(color(message, LogColor.Red), ERROR, FAILURE);
              throw new AzureARMDeploymentException(message);
            }
          }

          sleep(ofSeconds(context.getStatusCheckIntervalInSeconds()));
        }
      };

      HTimeLimiter.callInterruptible21(
          timeLimiter, Duration.ofMinutes(context.getSteadyCheckTimeoutInMinutes()), objectCallable);
    } catch (UncheckedTimeoutException e) {
      String message = format("Timed out waiting for executing operation deployment - [%s], %n %s",
          context.getDeploymentName(), e.getMessage());
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw new AzureARMTaskException(message, EnumSet.of(FailureType.TIMEOUT_ERROR));
    } catch (AzureARMRuntimeException ex) {
      throw ex;
    } catch (Exception e) {
      String message = format(
          "Error while waiting for executing operation [%s], %n %s", context.getDeploymentName(), e.getMessage());
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw new AzureARMDeploymentException(message, e);
    }
  }

  private String printProvisioningResults(
      ARMDeploymentSteadyStateContext context, AzureManagementClient azureManagementClient, LogCallback logCallback) {
    StringBuilder errorMessage = new StringBuilder("");
    logCallback.saveExecutionLog("");
    PagedIterable<DeploymentOperationInner> deploymentOperations =
        azureManagementClient.getDeploymentOperations(context);
    for (DeploymentOperationInner operation : deploymentOperations) {
      if (operation.properties() != null && operation.properties().targetResource() != null) {
        DeploymentOperationProperties properties = operation.properties();
        String resourceType = properties.targetResource().resourceType();
        String resourceName = properties.targetResource().resourceName();
        String resourceId = String.format("%s - %s", resourceType, resourceName);
        String status = isEmpty(properties.provisioningState()) ? "Not Available" : properties.provisioningState();
        logCallback.saveExecutionLog(String.format("%s :: [%s]", resourceId, status), hasFailed(status) ? ERROR : INFO);

        StatusMessage statusMessage = properties.statusMessage();
        if (hasFailed(status) && statusMessage != null && statusMessage.error() != null) {
          errorMessage
              .append(String.format("Resource - [%s], %nFailed due to - [%s]", resourceId, statusMessage.error()))
              .append('\n');
        }
      }
    }
    return errorMessage.toString();
  }

  private boolean hasFailed(String status) {
    return ARMDeploymentStatus.FAILED.getStatus().equalsIgnoreCase(status);
  }

  private boolean isDeploymentComplete(String provisioningState) {
    return (provisioningState == null) || ARMDeploymentStatus.SUCCEEDED.getStatus().equalsIgnoreCase(provisioningState)
        || ARMDeploymentStatus.FAILED.getStatus().equalsIgnoreCase(provisioningState)
        || ARMDeploymentStatus.CANCELLED.getStatus().equalsIgnoreCase(provisioningState);
  }

  private enum ARMDeploymentStatus {
    ACCEPTED("Accepted"),
    RUNNING("Running"),
    SUCCEEDED("Succeeded"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    private final String status;
    ARMDeploymentStatus(String status) {
      this.status = status;
    }
    public String getStatus() {
      return status;
    }
    public static boolean isSuccess(String status) {
      return SUCCEEDED.getStatus().equalsIgnoreCase(status);
    }
  }

  public void waitUntilCompleteWithTimeout(DeploymentBlueprintSteadyStateContext context,
      AzureBlueprintClient azureBlueprintClient, LogCallback logCallback) {
    try {
      Callable<Object> objectCallable = () -> {
        while (true) {
          String assignmentName = context.getAssignmentName();
          String assignmentProvisioningStatus = getAssignmentProvisioningStatus(context, azureBlueprintClient);
          logCallback.saveExecutionLog(
              String.format("Deployment Status for - [%s] is [%s]", assignmentName, assignmentProvisioningStatus));

          if (isDeploymentComplete(assignmentProvisioningStatus)) {
            printAssignmentOperations(context, azureBlueprintClient, logCallback);
            if (AssignmentProvisioningState.SUCCEEDED
                == AssignmentProvisioningState.fromString(assignmentProvisioningStatus)) {
              logCallback.saveExecutionLog(
                  color(format("%nBlueprint Deployment - [%s] completed successfully", assignmentName), LogColor.White,
                      LogWeight.Bold),
                  INFO, SUCCESS);
              return Boolean.TRUE;
            } else {
              String message = format("%nBlueprint deployment failed - Assignment Name: [%s]", assignmentName);
              throw new InvalidRequestException(message);
            }
          }
          sleep(ofSeconds(context.getStatusCheckIntervalInSeconds()));
        }
      };

      HTimeLimiter.callInterruptible21(
          timeLimiter, Duration.ofMinutes(context.getSteadyCheckTimeoutInMinutes()), objectCallable);
    } catch (UncheckedTimeoutException e) {
      String message = format("Timed out waiting for executing operation deployment - [%s], %n %s",
          context.getAssignmentName(), e.getMessage());
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw new AzureBPDeploymentException(message, e);
    } catch (InvalidRequestException e) {
      logCallback.saveExecutionLog(e.getMessage(), ERROR, FAILURE);
      throw e;
    } catch (Exception e) {
      String message = format(
          "Error while waiting for executing operation [%s], %n %s", context.getAssignmentName(), e.getMessage());
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw new AzureBPDeploymentException(message, e);
    }
  }

  private String getAssignmentProvisioningStatus(
      DeploymentBlueprintSteadyStateContext context, AzureBlueprintClient azureBlueprintClient) {
    String assignmentName = context.getAssignmentName();
    String assignmentResourceScope = context.getAssignmentResourceScope();
    Assignment assignment =
        azureBlueprintClient.getAssignment(context.getAzureConfig(), assignmentResourceScope, assignmentName);

    if (assignment == null) {
      throw new InvalidRequestException(format("Assignment is null, assignmentName: %s, assignmentResourceScope: %s",
          assignmentName, assignmentResourceScope));
    }

    if (assignment.getProperties() == null) {
      throw new InvalidRequestException(
          format("Assignment properties are null, assignmentName: %s, assignmentResourceScope: %s", assignmentName,
              assignmentResourceScope));
    }

    return assignment.getProperties().getProvisioningState().getValue();
  }

  private void printAssignmentOperations(DeploymentBlueprintSteadyStateContext context,
      AzureBlueprintClient azureBlueprintClient, LogCallback logCallback) {
    PagedFlux<AssignmentOperation> assignmentOperations = azureBlueprintClient.listAssignmentOperations(
        context.getAzureConfig(), context.getAssignmentResourceScope(), context.getAssignmentName());
    logCallback.saveExecutionLog(format("%nDeployment Jobs:"));
    logAssignmentOperations(assignmentOperations, logCallback);
  }

  private void logAssignmentOperations(PagedFlux<AssignmentOperation> assignmentOperations, LogCallback logCallback) {
    assignmentOperations.map(AssignmentOperation::getProperties)
        .map(AssignmentOperation.Properties::getDeployments)
        .doOnEach(assignmentDeploymentJobArray
            -> Arrays.stream(assignmentDeploymentJobArray.get())
                   .forEach(
                       assignmentDeploymentJob -> logAssignmentDeploymentJob(assignmentDeploymentJob, logCallback)))
        .subscribe();
  }

  private void logAssignmentDeploymentJob(final AssignmentDeploymentJob deploymentJob, final LogCallback logCallback) {
    AssignmentDeploymentJobResult deploymentJobResult = deploymentJob.getResult();
    if (deploymentJobResult == null) {
      logCallback.saveExecutionLog(format("%nDeployment Jobs list is empty"));
      return;
    }

    String resourceIds = getJobCreatedResourceIds(deploymentJobResult);
    AzureResourceManagerError deploymentError = deploymentJobResult.getError();
    String error = deploymentError != null ? deploymentError.getMessage() : "";
    logCallback.saveExecutionLog(format(
        "%n- Job Id: [%s]%n- Job Kind: [%s]%n- Job State: [%s]%n- Job Created Resource IDs: [%s]%n- Job Result Error: [%s]",
        deploymentJob.getJobId(), deploymentJob.getKind(), deploymentJob.getJobState(), resourceIds, error));
  }

  private String getJobCreatedResourceIds(AssignmentDeploymentJobResult deploymentJobResult) {
    if (deploymentJobResult.getResources() == null) {
      return "";
    }
    AssignmentJobCreatedResource[] resources = deploymentJobResult.getResources();
    StringJoiner resourceIds = new StringJoiner(",");
    for (AssignmentJobCreatedResource res : resources) {
      resourceIds.add(res.getId());
    }
    return resourceIds.toString();
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL;
import static io.harness.azure.model.AzureConstants.CLEAR_SCALING_POLICY;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_ERROR;
import static io.harness.azure.model.AzureConstants.VM_PROVISIONING_SPECIALIZED_STATUS;
import static io.harness.azure.model.AzureConstants.VM_PROVISIONING_SUCCEEDED_STATUS;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureAutoScaleSettingsClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.client.AzureNetworkClient;
import io.harness.azure.model.AzureConfig;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.compute.InstanceViewStatus;
import com.microsoft.azure.management.compute.VirtualMachineInstanceView;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public abstract class AzureVMSSTaskHandler {
  @Inject protected AzureComputeClient azureComputeClient;
  @Inject protected AzureAutoScaleSettingsClient azureAutoScaleSettingsClient;
  @Inject protected AzureNetworkClient azureNetworkClient;
  @Inject protected DelegateLogService delegateLogService;
  @Inject protected TimeLimiter timeLimiter;

  public AzureVMSSTaskExecutionResponse executeTask(
      AzureVMSSTaskParameters azureVMSSTaskParameters, AzureConfig azureConfig) {
    try {
      AzureVMSSTaskExecutionResponse response = executeTaskInternal(azureVMSSTaskParameters, azureConfig);
      if (!azureVMSSTaskParameters.isSyncTask()) {
        ExecutionLogCallback logCallback = getLogCallBack(azureVMSSTaskParameters, DEPLOYMENT_ERROR);
        if (response.getCommandExecutionStatus() == FAILURE) {
          logCallback.saveExecutionLog(
              format("Deployment error. Exception: [%s]", response.getErrorMessage()), ERROR, FAILURE);
        } else {
          logCallback.saveExecutionLog("No deployment error. Execution success", INFO, SUCCESS);
        }
      }
      return response;
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      String message = getErrorMessage(sanitizedException);
      if (azureVMSSTaskParameters.isSyncTask()) {
        throw new InvalidRequestException(message, sanitizedException);
      } else {
        ExecutionLogCallback logCallback = getLogCallBack(azureVMSSTaskParameters, DEPLOYMENT_ERROR);
        logCallback.saveExecutionLog(message, ERROR, FAILURE);
        log.error(format("Exception: [%s] while processing azure vmss task: [%s].", message,
                      azureVMSSTaskParameters.getCommandType().name()),
            sanitizedException);
        return AzureVMSSTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
      }
    }
  }

  public String getErrorMessage(Exception ex) {
    String message = ex.getMessage();
    if (ex.getCause() instanceof CloudException) {
      CloudException cloudException = (CloudException) ex.getCause();
      String cloudExMsg = cloudException.getMessage();
      message = format("%s, %nAzure Cloud Exception Message: %s", message, cloudExMsg);
    }
    return message;
  }

  protected void createAndFinishEmptyExecutionLog(
      AzureVMSSTaskParameters taskParameters, String commandUnit, String message) {
    ExecutionLogCallback logCallback = getLogCallBack(taskParameters, commandUnit);
    logCallback.saveExecutionLog(message, INFO, SUCCESS);
  }

  public ExecutionLogCallback getLogCallBack(AzureVMSSTaskParameters parameters, String commandUnit) {
    return new ExecutionLogCallback(
        delegateLogService, parameters.getAccountId(), parameters.getAppId(), parameters.getActivityId(), commandUnit);
  }

  protected void clearScalingPolicy(AzureConfig azureConfig, String subscriptionId, VirtualMachineScaleSet scaleSet,
      String resourceGroupName, ExecutionLogCallback logCallBack) {
    String scaleSetName = scaleSet.name();
    String scaleSetId = scaleSet.id();
    logCallBack.saveExecutionLog(format(CLEAR_SCALING_POLICY, scaleSetName));
    azureAutoScaleSettingsClient.clearAutoScaleSettingOnTargetResourceId(
        azureConfig, subscriptionId, resourceGroupName, scaleSetId);
  }

  protected abstract AzureVMSSTaskExecutionResponse executeTaskInternal(
      AzureVMSSTaskParameters azureVMSSTaskParameters, AzureConfig azureConfig);

  protected void updateVMSSCapacityAndWaitForSteadyState(AzureConfig azureConfig, AzureVMSSTaskParameters parameters,
      String virtualMachineScaleSetName, String subscriptionId, String resourceGroupName, int capacity,
      int autoScalingSteadyStateTimeout, String scaleCommandUnit, String waitCommandUnit) {
    ExecutionLogCallback logCallBack = getLogCallBack(parameters, scaleCommandUnit);
    logCallBack.saveExecutionLog(
        format("Set VMSS: [%s] desired capacity to [%s]", virtualMachineScaleSetName, capacity), INFO);

    VirtualMachineScaleSet vmss =
        getVirtualMachineScaleSet(azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);

    AzureRestCallBack<VirtualMachineScaleSet> restCallBack =
        new AzureRestCallBack<>(logCallBack, virtualMachineScaleSetName);
    vmss.update().withCapacity(capacity).applyAsync(restCallBack);
    logCallBack.saveExecutionLog("Successfully set desired capacity", INFO, SUCCESS);

    logCallBack = getLogCallBack(parameters, waitCommandUnit);
    waitForVMSSToBeDownSized(vmss, capacity, autoScalingSteadyStateTimeout, logCallBack, restCallBack);
    String message =
        "All the VM instances of VMSS: [%s] are " + (capacity == 0 ? "deleted " : "provisioned ") + "successfully";
    logCallBack.saveExecutionLog(format(message, virtualMachineScaleSetName), INFO, SUCCESS);
  }

  private VirtualMachineScaleSet getVirtualMachineScaleSet(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String virtualMachineScaleSetName) {
    Optional<VirtualMachineScaleSet> updatedVMSSOp = azureComputeClient.getVirtualMachineScaleSetByName(
        azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);
    return updatedVMSSOp.orElseThrow(
        ()
            -> new InvalidRequestException(
                format("There is no Virtual Machine Scale Set with name: %s, subscriptionId: %s, resourceGroupName: %s",
                    virtualMachineScaleSetName, subscriptionId, resourceGroupName)));
  }

  protected void waitForVMSSToBeDownSized(VirtualMachineScaleSet virtualMachineScaleSet, int capacity,
      int autoScalingSteadyStateTimeout, ExecutionLogCallback logCallBack,
      AzureRestCallBack<VirtualMachineScaleSet> restCallBack) {
    try {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(autoScalingSteadyStateTimeout), () -> {
        logCallBack.saveExecutionLog(
            format("Checking the status of VMSS: [%s] VM instances", virtualMachineScaleSet.name()), INFO);
        while (true) {
          if (restCallBack.updateFailed()) {
            String errorMessage = restCallBack.getErrorMessage();
            logCallBack.saveExecutionLog(errorMessage, ERROR, FAILURE);
            throw new InvalidRequestException(errorMessage);
          }
          if (checkAllVMSSInstancesProvisioned(virtualMachineScaleSet, capacity, logCallBack)) {
            return Boolean.TRUE;
          }
          sleep(ofSeconds(AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL));
        }
      });
    } catch (InterruptedException | UncheckedTimeoutException e) {
      String message = "Timed out waiting for provisioning VMSS VM instances to desired capacity. \n" + e.getMessage();
      logCallBack.saveExecutionLog(message, ERROR, FAILURE);
      throw new InvalidRequestException(message, e);
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      String message = "Error while waiting for provisioning VMSS VM instances to desired capacity. \n"
          + sanitizedException.getMessage();
      logCallBack.saveExecutionLog(message, ERROR, FAILURE);
      throw new InvalidRequestException(message, sanitizedException);
    }
  }

  private boolean checkAllVMSSInstancesProvisioned(
      VirtualMachineScaleSet newVirtualMachineScaleSet, int desiredCapacity, ExecutionLogCallback logCallBack) {
    PagedList<VirtualMachineScaleSetVM> vmssInstanceList = newVirtualMachineScaleSet.virtualMachines().list();
    logVMInstancesStatus(vmssInstanceList, logCallBack);
    return desiredCapacity == 0 ? vmssInstanceList.isEmpty()
                                : desiredCapacity == vmssInstanceList.size()
            && vmssInstanceList.stream().allMatch(this::isVMInstanceProvisioned);
  }

  private void logVMInstancesStatus(
      PagedList<VirtualMachineScaleSetVM> vmssInstanceList, ExecutionLogCallback logCallBack) {
    for (VirtualMachineScaleSetVM instance : vmssInstanceList) {
      String virtualMachineScaleSetVMName = instance.name();
      String provisioningDisplayStatus = getProvisioningDisplayStatus(instance);
      logCallBack.saveExecutionLog(String.format("Virtual machine instance: [%s] provisioning state: [%s]",
          virtualMachineScaleSetVMName, provisioningDisplayStatus));
    }
  }

  private boolean isVMInstanceProvisioned(VirtualMachineScaleSetVM instance) {
    String provisioningDisplayStatus = getProvisioningDisplayStatus(instance);
    return provisioningDisplayStatus.equals(VM_PROVISIONING_SPECIALIZED_STATUS)
        || provisioningDisplayStatus.equals(VM_PROVISIONING_SUCCEEDED_STATUS);
  }

  @NotNull
  private String getProvisioningDisplayStatus(VirtualMachineScaleSetVM instance) {
    return Optional.ofNullable(instance)
        .map(VirtualMachineScaleSetVM::instanceView)
        .map(VirtualMachineInstanceView::statuses)
        .map(instanceViewStatuses -> instanceViewStatuses.get(0))
        .map(InstanceViewStatus::displayStatus)
        .orElse(StringUtils.EMPTY);
  }
}

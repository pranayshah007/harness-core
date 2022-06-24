/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.azure.model.AzureConstants.DEFAULT_AZURE_VMSS_TIMEOUT_MIN;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_STATUS;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.NO_SCALING_POLICY_DURING_DOWN_SIZING;
import static io.harness.azure.model.AzureConstants.SKIP_RESIZE_SCALE_SET;
import static io.harness.azure.model.AzureConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.request.AzureVMSSDeployTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMInstanceData;
import io.harness.delegate.task.azure.response.AzureVMSSDeployTaskResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.beans.command.ExecutionLogCallback;

import com.google.inject.Singleton;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.monitor.ScaleCapacity;
import com.microsoft.azure.management.network.PublicIPAddressDnsSettings;
import com.microsoft.azure.management.network.VirtualMachineScaleSetNetworkInterface;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSDeployTaskHandler extends AzureVMSSTaskHandler {
  @Override
  protected AzureVMSSTaskExecutionResponse executeTaskInternal(
      final AzureVMSSTaskParameters azureVMSSTaskParameters, final AzureConfig azureConfig) {
    try {
      AzureVMSSDeployTaskParameters deployTaskParameters = (AzureVMSSDeployTaskParameters) azureVMSSTaskParameters;
      AzureVMSSDeployTaskResponse deployTaskResponse = resizeVirtualMachineScaleSet(azureConfig, deployTaskParameters);
      return logAndGenerateSuccessResponse(deployTaskResponse, deployTaskParameters);
    } catch (Exception ex) {
      return logAndGenerateSFailureResponse(ExceptionMessageSanitizer.sanitizeException(ex));
    }
  }

  protected AzureVMSSResizeDetail getScaleSetDetailsForUpSizing(AzureVMSSDeployTaskParameters deployTaskParameters) {
    String scaleSetNameForUpSize = deployTaskParameters.getNewVirtualMachineScaleSetName();
    int currentDesiredCountForUpSize =
        deployTaskParameters.getNewDesiredCount() != null ? deployTaskParameters.getNewDesiredCount() : 0;
    List<String> baseScalingPolicyJSONs = deployTaskParameters.getBaseScalingPolicyJSONs();
    int desiredInstances = deployTaskParameters.getDesiredInstances();
    boolean attachScalingPolicy = false;
    String scalingPolicyMessage =
        "Skipping attaching scaling policy to VMSS: [%s] as current capacity is lesser than desired capacity";

    if (currentDesiredCountForUpSize >= desiredInstances) {
      attachScalingPolicy = true;
      scalingPolicyMessage =
          "Attaching scaling policy to VMSS: [%s] as number of Virtual Machine instances has reached to desired capacity";
    }

    return AzureVMSSResizeDetail.builder()
        .scaleSetName(scaleSetNameForUpSize)
        .desiredCount(currentDesiredCountForUpSize)
        .scalingPolicyJSONs(baseScalingPolicyJSONs)
        .attachScalingPolicy(attachScalingPolicy)
        .scalingPolicyMessage(scalingPolicyMessage)
        .build();
  }

  protected AzureVMSSResizeDetail getScaleSetDetailsForDownSizing(AzureVMSSDeployTaskParameters deployTaskParameters) {
    String scaleSetNameForDownSize = deployTaskParameters.getOldVirtualMachineScaleSetName();
    int desiredCountForDownSize =
        deployTaskParameters.getOldDesiredCount() != null ? deployTaskParameters.getOldDesiredCount() : 0;
    List<String> baseScalingPolicyJSONs = deployTaskParameters.getPreDeploymentData().getScalingPolicyJSON();
    return AzureVMSSResizeDetail.builder()
        .scaleSetName(scaleSetNameForDownSize)
        .desiredCount(desiredCountForDownSize)
        .scalingPolicyJSONs(baseScalingPolicyJSONs)
        .attachScalingPolicy(false)
        .scalingPolicyMessage(NO_SCALING_POLICY_DURING_DOWN_SIZING)
        .build();
  }

  protected Optional<VirtualMachineScaleSet> getScaleSet(
      AzureConfig azureConfig, AzureVMSSDeployTaskParameters deployTaskParameters, String scaleSetName) {
    return azureComputeClient.getVirtualMachineScaleSetByName(azureConfig, deployTaskParameters.getSubscriptionId(),
        deployTaskParameters.getResourceGroupName(), scaleSetName);
  }

  private AzureVMSSDeployTaskResponse resizeVirtualMachineScaleSet(
      AzureConfig azureConfig, AzureVMSSDeployTaskParameters deployTaskParameters) {
    List<String> existingInstanceIds = getExistingInstanceIds(
        azureConfig, deployTaskParameters.getNewVirtualMachineScaleSetName(), deployTaskParameters);

    if (deployTaskParameters.isResizeNewFirst()) {
      upSizeScaleSet(azureConfig, deployTaskParameters);
      downSizeScaleSet(azureConfig, deployTaskParameters);
    } else {
      downSizeScaleSet(azureConfig, deployTaskParameters);
      upSizeScaleSet(azureConfig, deployTaskParameters);
    }

    List<AzureVMInstanceData> instancesAdded = getNewInstancesAdded(azureConfig,
        deployTaskParameters.getNewVirtualMachineScaleSetName(), deployTaskParameters, existingInstanceIds);
    List<AzureVMInstanceData> existingInstancesForOldScaleSet =
        getInstances(azureConfig, deployTaskParameters.getOldVirtualMachineScaleSetName(), deployTaskParameters);

    return AzureVMSSDeployTaskResponse.builder()
        .vmInstancesAdded(instancesAdded)
        .vmInstancesExisting(existingInstancesForOldScaleSet)
        .build();
  }

  private void upSizeScaleSet(AzureConfig azureConfig, AzureVMSSDeployTaskParameters deployTaskParameters) {
    AzureVMSSResizeDetail scaleSetDetailsForUpSize = getScaleSetDetailsForUpSizing(deployTaskParameters);
    resizeScaleSet(azureConfig, deployTaskParameters, UP_SCALE_COMMAND_UNIT, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
        scaleSetDetailsForUpSize);
  }

  private void downSizeScaleSet(AzureConfig azureConfig, AzureVMSSDeployTaskParameters deployTaskParameters) {
    AzureVMSSResizeDetail scaleSetDetailsForDownSizing = getScaleSetDetailsForDownSizing(deployTaskParameters);
    if (deployTaskParameters.isBlueGreen()) {
      createAndLogSkipDownSizeMessage(deployTaskParameters, scaleSetDetailsForDownSizing.getScaleSetName());
      return;
    }
    resizeScaleSet(azureConfig, deployTaskParameters, DOWN_SCALE_COMMAND_UNIT,
        DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, scaleSetDetailsForDownSizing);
  }

  private void createAndLogSkipDownSizeMessage(
      AzureVMSSDeployTaskParameters deployTaskParameters, String scaleSetName) {
    String message = format("BG deployment, hence skipping downsize production scale set: [%s] in deploy step",
        isEmpty(scaleSetName) ? EMPTY : scaleSetName);
    handleExecutionLog(
        deployTaskParameters, message, DOWN_SCALE_COMMAND_UNIT, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
  }

  private void resizeScaleSet(AzureConfig azureConfig, AzureVMSSDeployTaskParameters deployTaskParameters,
      String scaleCommandUnit, String waitCommandUnit, AzureVMSSResizeDetail azureVMSSResizeDetail) {
    String subscriptionId = deployTaskParameters.getSubscriptionId();
    String resourceGroupName = deployTaskParameters.getResourceGroupName();
    String scaleSetName = azureVMSSResizeDetail.getScaleSetName();
    Optional<VirtualMachineScaleSet> scaleSet = getScaleSet(azureConfig, deployTaskParameters, scaleSetName);
    if (!scaleSet.isPresent()) {
      createAndLogSkipReSizeMessage(deployTaskParameters, scaleCommandUnit, waitCommandUnit, scaleSetName);
      return;
    }

    VirtualMachineScaleSet virtualMachineScaleSet = scaleSet.get();
    int desiredCount = azureVMSSResizeDetail.getDesiredCount();
    ExecutionLogCallback logCallBack = getLogCallBack(deployTaskParameters, scaleCommandUnit);

    clearScalingPolicy(azureConfig, subscriptionId, virtualMachineScaleSet, resourceGroupName, logCallBack);
    updateScaleSetCapacity(
        azureConfig, deployTaskParameters, scaleSetName, desiredCount, scaleCommandUnit, waitCommandUnit);
    attachScalingPolicy(
        azureConfig, virtualMachineScaleSet, deployTaskParameters, azureVMSSResizeDetail, waitCommandUnit);
  }

  private void createAndLogSkipReSizeMessage(AzureVMSSDeployTaskParameters deployTaskParameters,
      String scaleCommandUnit, String waitCommandUnit, String scaleSetName) {
    String message = format(SKIP_RESIZE_SCALE_SET, isEmpty(scaleSetName) ? EMPTY : scaleSetName);
    handleExecutionLog(deployTaskParameters, message, scaleCommandUnit, waitCommandUnit);
  }

  private void updateScaleSetCapacity(AzureConfig azureConfig, AzureVMSSDeployTaskParameters deployTaskParameters,
      String scaleSetName, int capacity, String scaleCommandUnit, String waitCommandUnit) {
    String subscriptionId = deployTaskParameters.getSubscriptionId();
    String resourceGroupName = deployTaskParameters.getResourceGroupName();
    int timeoutIntervalInMin = deployTaskParameters.getTimeoutIntervalInMin() != null
        ? deployTaskParameters.getTimeoutIntervalInMin()
        : DEFAULT_AZURE_VMSS_TIMEOUT_MIN;

    updateVMSSCapacityAndWaitForSteadyState(azureConfig, deployTaskParameters, scaleSetName, subscriptionId,
        resourceGroupName, capacity, timeoutIntervalInMin, scaleCommandUnit, waitCommandUnit);
  }

  private void attachScalingPolicy(AzureConfig azureConfig, VirtualMachineScaleSet scaleSet,
      AzureVMSSDeployTaskParameters deployTaskParameters, AzureVMSSResizeDetail azureVMSSResizeDetail,
      String waitCommandUnit) {
    ExecutionLogCallback logCallBack = getLogCallBack(deployTaskParameters, waitCommandUnit);
    if (!azureVMSSResizeDetail.isAttachScalingPolicy()) {
      logCallBack.saveExecutionLog(
          format(azureVMSSResizeDetail.getScalingPolicyMessage(), scaleSet.name()), INFO, SUCCESS);
      return;
    }

    String scaleSetId = scaleSet.id();
    String subscriptionId = deployTaskParameters.getSubscriptionId();
    String resourceGroupName = deployTaskParameters.getResourceGroupName();
    String minimum = String.valueOf(deployTaskParameters.getMinInstances());
    String maximum = String.valueOf(deployTaskParameters.getMaxInstances());
    String desired = String.valueOf(deployTaskParameters.getDesiredInstances());
    List<String> scalingPolicyJSONs = azureVMSSResizeDetail.getScalingPolicyJSONs();
    ScaleCapacity capacity = new ScaleCapacity();
    capacity.withMinimum(minimum).withMaximum(maximum).withDefaultProperty(desired);

    logCallBack.saveExecutionLog(
        format(azureVMSSResizeDetail.getScalingPolicyMessage(), scaleSet.name()), INFO, SUCCESS);
    azureAutoScaleSettingsClient.attachAutoScaleSettingToTargetResourceId(
        azureConfig, subscriptionId, resourceGroupName, scaleSetId, scalingPolicyJSONs, capacity);
  }

  private void handleExecutionLog(AzureVMSSDeployTaskParameters deployTaskParameters, String message,
      String scaleCommandUnit, String waitCommandUnit) {
    log.warn(message);
    createAndFinishEmptyExecutionLog(deployTaskParameters, scaleCommandUnit, message);
    createAndFinishEmptyExecutionLog(deployTaskParameters, waitCommandUnit, message);
  }

  private List<AzureVMInstanceData> getNewInstancesAdded(AzureConfig azureConfig, String scaleSetName,
      AzureVMSSDeployTaskParameters deployTaskParameters, List<String> existingInstanceIds) {
    List<AzureVMInstanceData> allInstances = getInstances(azureConfig, scaleSetName, deployTaskParameters);

    return allInstances.stream()
        .filter(vmInstanceData -> !existingInstanceIds.contains(vmInstanceData.getInstanceId()))
        .collect(Collectors.toList());
  }

  public List<String> getExistingInstanceIds(
      AzureConfig azureConfig, String scaleSetName, AzureVMSSDeployTaskParameters deployTaskParameters) {
    List<AzureVMInstanceData> existingInstances = getInstances(azureConfig, scaleSetName, deployTaskParameters);
    return existingInstances.stream().map(AzureVMInstanceData::getInstanceId).collect(Collectors.toList());
  }

  private List<AzureVMInstanceData> getInstances(
      AzureConfig azureConfig, String scaleSetName, AzureVMSSDeployTaskParameters deployTaskParameters) {
    List<VirtualMachineScaleSetVM> scaleSetVMs = azureComputeClient.listVirtualMachineScaleSetVMs(azureConfig,
        deployTaskParameters.getSubscriptionId(), deployTaskParameters.getResourceGroupName(), scaleSetName);

    return scaleSetVMs.stream().map(this::generateVMInstanceData).collect(Collectors.toList());
  }

  private AzureVMInstanceData generateVMInstanceData(VirtualMachineScaleSetVM scaleSetVM) {
    String vmId = scaleSetVM.inner().vmId();
    String vmName = scaleSetVM.name();
    String privateIP = EMPTY;
    String publicDnsName = EMPTY;

    List<VirtualMachineScaleSetNetworkInterface> vmScaleSetNetworkInterfaces =
        azureComputeClient.listVMVirtualMachineScaleSetNetworkInterfaces(scaleSetVM);
    if (!vmScaleSetNetworkInterfaces.isEmpty()) {
      VirtualMachineScaleSetNetworkInterface virtualMachineScaleSetNetworkInterface =
          vmScaleSetNetworkInterfaces.get(0);
      privateIP = virtualMachineScaleSetNetworkInterface.primaryPrivateIP();

      PublicIPAddressInner publicIPAddressInner =
          virtualMachineScaleSetNetworkInterface.primaryIPConfiguration().inner().publicIPAddress();
      if (publicIPAddressInner != null) {
        PublicIPAddressDnsSettings publicIPAddressDnsSettings = publicIPAddressInner.dnsSettings();
        publicDnsName = publicIPAddressDnsSettings != null ? publicIPAddressDnsSettings.fqdn() : EMPTY;
      }
    }

    return AzureVMInstanceData.builder()
        .instanceId(vmId)
        .privateDnsName(vmName)
        .privateIpAddress(privateIP)
        .publicDnsName(publicDnsName)
        .build();
  }

  private AzureVMSSTaskExecutionResponse logAndGenerateSuccessResponse(
      AzureVMSSDeployTaskResponse deployTaskResponse, AzureVMSSDeployTaskParameters deployTaskParameters) {
    String newVirtualMachineScaleSetName = deployTaskParameters.getNewVirtualMachineScaleSetName();
    String oldVirtualMachineScaleSetName = deployTaskParameters.getOldVirtualMachineScaleSetName();

    int newScaleSetInstancesSize = deployTaskResponse.getVmInstancesAdded().size();
    int oldScaleSetInstancesSize = deployTaskResponse.getVmInstancesExisting().size();

    ExecutionLogCallback logCallBack = getLogCallBack(deployTaskParameters, DEPLOYMENT_STATUS);
    logCallBack.saveExecutionLog(
        format("Total number of new instances deployed for Scale Set: [%s] is [%d] ",
            isEmpty(newVirtualMachineScaleSetName) ? "" : newVirtualMachineScaleSetName, newScaleSetInstancesSize),
        INFO);
    logCallBack.saveExecutionLog(
        format("Total number of instances of old Scale Set: [%s] is [%d]",
            isEmpty(oldVirtualMachineScaleSetName) ? "" : oldVirtualMachineScaleSetName, oldScaleSetInstancesSize),
        INFO, SUCCESS);

    return AzureVMSSTaskExecutionResponse.builder()
        .azureVMSSTaskResponse(deployTaskResponse)
        .commandExecutionStatus(SUCCESS)
        .build();
  }

  private AzureVMSSTaskExecutionResponse logAndGenerateSFailureResponse(Exception ex) {
    String errorMessage = ExceptionUtils.getMessage(ex);
    log.error(errorMessage, ex);
    return AzureVMSSTaskExecutionResponse.builder().errorMessage(errorMessage).commandExecutionStatus(FAILURE).build();
  }
}

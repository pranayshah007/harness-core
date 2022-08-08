/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.AZURE_LOAD_BALANCER_DETAIL_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.AZURE_VMSS_SWAP_BACKEND_POOL;
import static io.harness.azure.model.AzureConstants.BG_BLUE_TAG_VALUE;
import static io.harness.azure.model.AzureConstants.BG_GREEN_TAG_VALUE;
import static io.harness.azure.model.AzureConstants.BG_VERSION_TAG_NAME;
import static io.harness.azure.model.AzureConstants.DOWNSIZING_FLAG_DISABLED;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.END_BLUE_GREEN_SWAP;
import static io.harness.azure.model.AzureConstants.NEW_VMSS_NAME_NULL_VALIDATION_MSG;
import static io.harness.azure.model.AzureConstants.NO_VMSS_FOR_DOWN_SIZING;
import static io.harness.azure.model.AzureConstants.NO_VMSS_FOR_UPSCALE_DURING_ROLLBACK;
import static io.harness.azure.model.AzureConstants.START_BLUE_GREEN_SWAP;
import static io.harness.azure.model.AzureConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.delegate.task.azure.request.AzureVMSSSwitchRouteTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSSwitchRoutesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.beans.command.ExecutionLogCallback;

import com.google.inject.Singleton;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM;
import com.microsoft.azure.management.network.LoadBalancer;
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasName;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AzureVMSSSwitchRouteTaskHandler extends AzureVMSSTaskHandler {
  @Override
  protected AzureVMSSTaskExecutionResponse executeTaskInternal(
      AzureVMSSTaskParameters azureVMSSTaskParameters, AzureConfig azureConfig) {
    AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters =
        (AzureVMSSSwitchRouteTaskParameters) azureVMSSTaskParameters;
    validateTaskParams(switchRouteTaskParameters);

    String newVMSSName = switchRouteTaskParameters.getNewVMSSName();
    String oldVMSSName = switchRouteTaskParameters.getOldVMSSName();
    String loadBalancerName = switchRouteTaskParameters.getAzureLoadBalancerDetail().getLoadBalancerName();

    VirtualMachineScaleSet newVMSS = getVirtualMachineScaleSet(azureConfig, switchRouteTaskParameters, newVMSSName);
    VirtualMachineScaleSet oldVMSS = getVirtualMachineScaleSet(azureConfig, switchRouteTaskParameters, oldVMSSName);
    LoadBalancer loadBalancer = getLoadBalancer(azureConfig, switchRouteTaskParameters, loadBalancerName);

    if (switchRouteTaskParameters.isRollback()) {
      executeRollback(azureConfig, switchRouteTaskParameters, newVMSS, oldVMSS, loadBalancer);
    } else {
      executeSwapRoutes(azureConfig, switchRouteTaskParameters, newVMSS, oldVMSS, loadBalancer);
    }
    return buildAzureVMSSTaskExecutionResponse();
  }

  private void validateTaskParams(AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters) {
    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail =
        switchRouteTaskParameters.getAzureLoadBalancerDetail();
    String newVMSSName = switchRouteTaskParameters.getNewVMSSName();

    if (isNull(azureLoadBalancerDetail)) {
      throw new InvalidArgumentsException(AZURE_LOAD_BALANCER_DETAIL_NULL_VALIDATION_MSG);
    }

    if (isBlank(newVMSSName)) {
      throw new InvalidArgumentsException(NEW_VMSS_NAME_NULL_VALIDATION_MSG);
    }
  }

  private VirtualMachineScaleSet getVirtualMachineScaleSet(
      AzureConfig azureConfig, AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters, String vmssName) {
    if (isBlank(vmssName)) {
      return null;
    }
    String subscriptionId = switchRouteTaskParameters.getSubscriptionId();
    String resourceGroupName = switchRouteTaskParameters.getResourceGroupName();

    Optional<VirtualMachineScaleSet> vmss =
        azureComputeClient.getVirtualMachineScaleSetByName(azureConfig, subscriptionId, resourceGroupName, vmssName);
    return vmss.orElseThrow(
        ()
            -> new InvalidRequestException(
                format("Not found virtual machine scale set with name: %s, subscriptionId: %s, resourceGroupName: %s",
                    vmssName, subscriptionId, resourceGroupName)));
  }

  private LoadBalancer getLoadBalancer(
      AzureConfig azureConfig, AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters, String loadBalancerName) {
    String subscriptionId = switchRouteTaskParameters.getSubscriptionId();
    String resourceGropeName = switchRouteTaskParameters.getResourceGroupName();

    Optional<LoadBalancer> loadBalancerOp =
        azureNetworkClient.getLoadBalancerByName(azureConfig, subscriptionId, resourceGropeName, loadBalancerName);
    return loadBalancerOp.orElseThrow(
        ()
            -> new InvalidRequestException(format(
                "Not found load balancer with name: %s, resourceGroupName: %s", loadBalancerName, resourceGropeName)));
  }

  private void executeSwapRoutes(AzureConfig azureConfig, AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters,
      VirtualMachineScaleSet newVMSS, VirtualMachineScaleSet oldVMSS, LoadBalancer loadBalancer) {
    ExecutionLogCallback bgSwapRoutesLogCallback =
        getLogCallBack(switchRouteTaskParameters, AZURE_VMSS_SWAP_BACKEND_POOL);
    bgSwapRoutesLogCallback.saveExecutionLog(START_BLUE_GREEN_SWAP, INFO);
    executeSwapRoutesNewVMSS(azureConfig, switchRouteTaskParameters, newVMSS, loadBalancer, bgSwapRoutesLogCallback);

    if (oldVMSS != null) {
      executeSwapRoutesOldVMSS(azureConfig, oldVMSS, switchRouteTaskParameters, bgSwapRoutesLogCallback);
    } else {
      bgSwapRoutesLogCallback.saveExecutionLog(END_BLUE_GREEN_SWAP, INFO, SUCCESS);
      createAndFinishEmptyExecutionLog(switchRouteTaskParameters, DOWN_SCALE_COMMAND_UNIT, NO_VMSS_FOR_DOWN_SIZING);
      createAndFinishEmptyExecutionLog(
          switchRouteTaskParameters, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, NO_VMSS_FOR_DOWN_SIZING);
    }
  }

  private void executeSwapRoutesNewVMSS(AzureConfig azureConfig,
      AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters, VirtualMachineScaleSet newVMSS,
      LoadBalancer loadBalancer, ExecutionLogCallback bgSwapRoutesLogCallback) {
    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail =
        switchRouteTaskParameters.getAzureLoadBalancerDetail();
    int timeoutIntervalInMin = switchRouteTaskParameters.getTimeoutIntervalInMin();

    detachNewVMSSFromStageBackendPool(
        azureConfig, newVMSS, azureLoadBalancerDetail, timeoutIntervalInMin, bgSwapRoutesLogCallback);
    attachNewVMSSToProdBackendPool(
        azureConfig, newVMSS, loadBalancer, azureLoadBalancerDetail, timeoutIntervalInMin, bgSwapRoutesLogCallback);
    addTagsToVMSS(newVMSS, BG_BLUE_TAG_VALUE, bgSwapRoutesLogCallback);
  }

  private void detachNewVMSSFromStageBackendPool(AzureConfig azureConfig, VirtualMachineScaleSet newVMSS,
      AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail, int timeoutIntervalInMin,
      ExecutionLogCallback bgSwapRoutesLogCallback) {
    String stageBackendPool = azureLoadBalancerDetail.getStageBackendPool();
    bgSwapRoutesLogCallback.saveExecutionLog(
        format("Sending request to detach virtual machine scale set:[%s] from stage backend pool:[%s]", newVMSS.name(),
            stageBackendPool),
        INFO);
    VirtualMachineScaleSet detachedVMSS =
        azureComputeClient.detachVMSSFromBackendPools(azureConfig, newVMSS, stageBackendPool);
    waitForUpdatingVMInstances(detachedVMSS, timeoutIntervalInMin, bgSwapRoutesLogCallback);
  }

  private void attachNewVMSSToProdBackendPool(AzureConfig azureConfig, VirtualMachineScaleSet newVMSS,
      LoadBalancer primaryInternetFacingLoadBalancer, AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail,
      int timeoutIntervalInMin, ExecutionLogCallback bgSwapRoutesLogCallback) {
    String prodBackendPool = azureLoadBalancerDetail.getProdBackendPool();
    bgSwapRoutesLogCallback.saveExecutionLog(
        format("Sending request to attach virtual machine scale set:[%s] to prod backend pool:[%s]", newVMSS.name(),
            prodBackendPool));
    VirtualMachineScaleSet attachedVMSS = azureComputeClient.attachVMSSToBackendPools(
        azureConfig, newVMSS, primaryInternetFacingLoadBalancer, prodBackendPool);
    waitForUpdatingVMInstances(attachedVMSS, timeoutIntervalInMin, bgSwapRoutesLogCallback);
  }

  private void executeSwapRoutesOldVMSS(AzureConfig azureConfig, VirtualMachineScaleSet oldVMSS,
      AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters, ExecutionLogCallback bgSwapRoutesLogCallback) {
    String subscriptionId = switchRouteTaskParameters.getSubscriptionId();
    String resourceGroupName = switchRouteTaskParameters.getResourceGroupName();
    int timeoutIntervalInMin = switchRouteTaskParameters.getTimeoutIntervalInMin();
    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail =
        switchRouteTaskParameters.getAzureLoadBalancerDetail();

    detachOldVMSSFromProdBackendPool(
        azureConfig, oldVMSS, azureLoadBalancerDetail, timeoutIntervalInMin, bgSwapRoutesLogCallback);
    addTagsToVMSS(oldVMSS, BG_GREEN_TAG_VALUE, bgSwapRoutesLogCallback);
    bgSwapRoutesLogCallback.saveExecutionLog(END_BLUE_GREEN_SWAP, INFO, SUCCESS);

    if (switchRouteTaskParameters.isDownscaleOldVMSS()) {
      ExecutionLogCallback logCallBack = getLogCallBack(switchRouteTaskParameters, DOWN_SCALE_COMMAND_UNIT);
      clearScalingPolicy(azureConfig, subscriptionId, oldVMSS, resourceGroupName, logCallBack);
      scaleDownVMSSToZeroCapacityAndWait(azureConfig, switchRouteTaskParameters, oldVMSS, logCallBack);
    } else {
      createAndFinishEmptyExecutionLog(
          switchRouteTaskParameters, DOWN_SCALE_COMMAND_UNIT, format(DOWNSIZING_FLAG_DISABLED, oldVMSS.name()));
      createAndFinishEmptyExecutionLog(switchRouteTaskParameters, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT,
          format(DOWNSIZING_FLAG_DISABLED, oldVMSS.name()));
    }
  }

  private void detachOldVMSSFromProdBackendPool(AzureConfig azureConfig, VirtualMachineScaleSet oldVMSS,
      AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail, int timeoutIntervalInMin,
      ExecutionLogCallback bgSwapRoutesLogCallback) {
    String prodBackendPool = azureLoadBalancerDetail.getProdBackendPool();
    bgSwapRoutesLogCallback.saveExecutionLog(
        format("Sending request to detach virtual machine scale set:[%s] from prod backend pool:[%s]", oldVMSS.name(),
            prodBackendPool));
    VirtualMachineScaleSet detachedVMSS =
        azureComputeClient.detachVMSSFromBackendPools(azureConfig, oldVMSS, prodBackendPool);
    waitForUpdatingVMInstances(detachedVMSS, timeoutIntervalInMin, bgSwapRoutesLogCallback);
  }

  private void executeRollback(AzureConfig azureConfig, AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters,
      VirtualMachineScaleSet newVMSS, VirtualMachineScaleSet oldVMSS, LoadBalancer loadBalancer) {
    ExecutionLogCallback logCallBack = getLogCallBack(switchRouteTaskParameters, UP_SCALE_COMMAND_UNIT);
    logCallBack.saveExecutionLog("Start Blue Green swap rollback", INFO);
    if (oldVMSS != null) {
      executeRollbackOldVMSS(azureConfig, switchRouteTaskParameters, oldVMSS, loadBalancer, logCallBack);
    } else {
      createAndFinishEmptyExecutionLog(
          switchRouteTaskParameters, UP_SCALE_COMMAND_UNIT, NO_VMSS_FOR_UPSCALE_DURING_ROLLBACK);
      createAndFinishEmptyExecutionLog(
          switchRouteTaskParameters, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT, NO_VMSS_FOR_UPSCALE_DURING_ROLLBACK);
    }
    executeRollbackNewVMSS(azureConfig, switchRouteTaskParameters, newVMSS);
  }

  private void executeRollbackOldVMSS(AzureConfig azureConfig,
      AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters, VirtualMachineScaleSet oldVMSS,
      LoadBalancer loadBalancer, ExecutionLogCallback logCallBack) {
    String subscriptionId = switchRouteTaskParameters.getSubscriptionId();
    String resourceGroupName = switchRouteTaskParameters.getResourceGroupName();
    int timeoutIntervalInMin = switchRouteTaskParameters.getTimeoutIntervalInMin();
    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail =
        switchRouteTaskParameters.getAzureLoadBalancerDetail();
    AzureVMSSPreDeploymentData preDeploymentData = switchRouteTaskParameters.getPreDeploymentData();
    String oldScalingPolicyJSON = EMPTY;
    if (!preDeploymentData.getScalingPolicyJSON().isEmpty()) {
      oldScalingPolicyJSON = preDeploymentData.getScalingPolicyJSON().get(0);
    }

    clearScalingPolicy(azureConfig, subscriptionId, oldVMSS, resourceGroupName, logCallBack);
    scaleUpVMSSToDesiredCapacityAndWait(azureConfig, switchRouteTaskParameters, oldVMSS);
    attachAutoScalePolicyToVMSS(
        azureConfig, oldVMSS, resourceGroupName, oldScalingPolicyJSON, switchRouteTaskParameters);
    addBlueTagToOldVMSS(oldVMSS, switchRouteTaskParameters);

    attachOldVMSSToProdBackendPool(azureConfig, oldVMSS, loadBalancer, azureLoadBalancerDetail, timeoutIntervalInMin,
        getLogCallBack(switchRouteTaskParameters, AZURE_VMSS_SWAP_BACKEND_POOL));
  }

  private void addBlueTagToOldVMSS(
      VirtualMachineScaleSet vmss, AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters) {
    ExecutionLogCallback logCallBack =
        getLogCallBack(switchRouteTaskParameters, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
    addTagsToVMSS(vmss, BG_BLUE_TAG_VALUE, logCallBack);
    logCallBack.saveExecutionLog(format("Tagged successfully VMSS: [%s]", vmss.name()), INFO, SUCCESS);
  }

  private void attachOldVMSSToProdBackendPool(AzureConfig azureConfig, VirtualMachineScaleSet oldVMSS,
      LoadBalancer primaryInternetFacingLoadBalancer, AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail,
      int timeoutIntervalInMin, ExecutionLogCallback bgRollbackLogCallback) {
    String prodBackendPool = azureLoadBalancerDetail.getProdBackendPool();
    bgRollbackLogCallback.saveExecutionLog(
        format("Sending request to attach virtual machine scale set:[%s] to prod backend pool:[%s]", oldVMSS.name(),
            prodBackendPool));
    VirtualMachineScaleSet attachedVMSS = azureComputeClient.attachVMSSToBackendPools(
        azureConfig, oldVMSS, primaryInternetFacingLoadBalancer, prodBackendPool);
    waitForUpdatingVMInstances(attachedVMSS, timeoutIntervalInMin, bgRollbackLogCallback);
    bgRollbackLogCallback.saveExecutionLog(
        format("Old VMSS: [%s] attached successfully to prod backend pool [%s]", oldVMSS.name(), prodBackendPool),
        INFO);
  }

  private void executeRollbackNewVMSS(AzureConfig azureConfig,
      AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters, VirtualMachineScaleSet newVMSS) {
    String subscriptionId = switchRouteTaskParameters.getSubscriptionId();
    String resourceGroupName = switchRouteTaskParameters.getResourceGroupName();
    int timeoutIntervalInMin = switchRouteTaskParameters.getTimeoutIntervalInMin();
    AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail =
        switchRouteTaskParameters.getAzureLoadBalancerDetail();

    ExecutionLogCallback logCallBack = getLogCallBack(switchRouteTaskParameters, AZURE_VMSS_SWAP_BACKEND_POOL);
    detachNewVMSSFromProdBackendPool(azureConfig, newVMSS, azureLoadBalancerDetail, timeoutIntervalInMin, logCallBack);
    clearScalingPolicy(azureConfig, subscriptionId, newVMSS, resourceGroupName,
        getLogCallBack(switchRouteTaskParameters, DOWN_SCALE_COMMAND_UNIT));
    scaleDownVMSSToZeroCapacityAndWait(azureConfig, switchRouteTaskParameters, newVMSS,
        getLogCallBack(switchRouteTaskParameters, DOWN_SCALE_COMMAND_UNIT));
    deleteVMSS(azureConfig, subscriptionId, newVMSS,
        getLogCallBack(switchRouteTaskParameters, DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT));
  }

  private void detachNewVMSSFromProdBackendPool(AzureConfig azureConfig, VirtualMachineScaleSet newVMSS,

      AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail, int timeoutIntervalInMin,
      ExecutionLogCallback bgRollbackLogCallback) {
    String prodBackendPool = azureLoadBalancerDetail.getProdBackendPool();
    bgRollbackLogCallback.saveExecutionLog(
        format("Sending request to detach virtual machine scale set:[%s] from prod backend pool:[%s]", newVMSS.name(),
            prodBackendPool));
    VirtualMachineScaleSet detachedVMSS =
        azureComputeClient.detachVMSSFromBackendPools(azureConfig, newVMSS, prodBackendPool);
    waitForUpdatingVMInstances(detachedVMSS, timeoutIntervalInMin, bgRollbackLogCallback);
    bgRollbackLogCallback.saveExecutionLog(
        format("New VMSS: [%s] detach successfully from prod backend pool:[%s]", newVMSS.name(), prodBackendPool), INFO,
        SUCCESS);
  }

  private void waitForUpdatingVMInstances(
      VirtualMachineScaleSet vmss, int timeoutIntervalInMin, ExecutionLogCallback logCallback) {
    Map<String, String> nameToInstanceId = vmss.virtualMachines().list().stream().collect(
        Collectors.toMap(HasName::name, VirtualMachineScaleSetVM::instanceId));

    try {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(timeoutIntervalInMin), () -> {
        nameToInstanceId.keySet().forEach(vmName -> {
          logCallback.saveExecutionLog(
              format("Updating virtual machine instance: [%s] for the scale set: [%s]", vmName, vmss.name()));
          azureComputeClient.updateVMInstances(vmss, nameToInstanceId.get(vmName));
        });

        logCallback.saveExecutionLog(
            format("All virtual machine instances updated for the scale set: [%s]", vmss.name()));
        return Boolean.TRUE;
      });
    } catch (Exception e) {
      throw new InvalidRequestException("Error while updating Virtual Machine Scale Set VM instances",
          ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  private void scaleDownVMSSToZeroCapacityAndWait(AzureConfig azureConfig,
      AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters, VirtualMachineScaleSet vmss,
      ExecutionLogCallback logCallBack) {
    logCallBack.saveExecutionLog(
        format("Start down sizing Virtual Machine Scale Set: [%s] to capacity [0]", vmss.name()));
    updateVMSSCapacityAndWait(azureConfig, switchRouteTaskParameters, vmss, 0, DOWN_SCALE_COMMAND_UNIT,
        DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
  }

  private void scaleUpVMSSToDesiredCapacityAndWait(AzureConfig azureConfig,
      AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters, VirtualMachineScaleSet vmss) {
    int desiredCapacity = switchRouteTaskParameters.getPreDeploymentData().getDesiredCapacity();

    updateVMSSCapacityAndWait(azureConfig, switchRouteTaskParameters, vmss, desiredCapacity, UP_SCALE_COMMAND_UNIT,
        UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
  }

  private void updateVMSSCapacityAndWait(AzureConfig azureConfig,
      AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters, VirtualMachineScaleSet vmss, int desiredCapacity,
      String scaleCommandUnit, String waitCommandUnit) {
    String subscriptionId = switchRouteTaskParameters.getSubscriptionId();
    String resourceGroupName = switchRouteTaskParameters.getResourceGroupName();
    Integer timeoutIntervalInMin = switchRouteTaskParameters.getTimeoutIntervalInMin();
    String vmssName = vmss.name();

    updateVMSSCapacityAndWaitForSteadyState(azureConfig, switchRouteTaskParameters, vmssName, subscriptionId,
        resourceGroupName, desiredCapacity, timeoutIntervalInMin, scaleCommandUnit, waitCommandUnit);
  }

  private void attachAutoScalePolicyToVMSS(AzureConfig azureConfig, VirtualMachineScaleSet vmss,
      String resourceGroupName, String scalePolicyJson, AzureVMSSSwitchRouteTaskParameters switchRouteTaskParameters) {
    String subscriptionId = switchRouteTaskParameters.getSubscriptionId();
    ExecutionLogCallback logCallBack =
        getLogCallBack(switchRouteTaskParameters, UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT);
    String vmssId = vmss.id();
    logCallBack.saveExecutionLog(format("Attaching scaling policy to VMSS: [%s]", vmss.name()), INFO);
    azureAutoScaleSettingsClient.attachAutoScaleSettingToTargetResourceId(
        azureConfig, subscriptionId, resourceGroupName, vmssId, scalePolicyJson);
    logCallBack.saveExecutionLog(format("Scaling policy attached to VMSS: [%s]", vmss.name()), INFO);
  }

  private void addTagsToVMSS(VirtualMachineScaleSet vmss, String tagValue, ExecutionLogCallback logCallback) {
    logCallback.saveExecutionLog(format("Tagging VMSS: [%s] as [%s] deployment", vmss.name(), tagValue));
    vmss.update().withTag(BG_VERSION_TAG_NAME, tagValue).apply();
    logCallback.saveExecutionLog(format("Tagged successfully VMSS: [%s]", vmss.name()), INFO, SUCCESS);
  }

  private void deleteVMSS(AzureConfig azureConfig, String subscriptionId, VirtualMachineScaleSet vmss,
      ExecutionLogCallback bgRollbackLogCallback) {
    String virtualMachineScaleSet = vmss.name();
    bgRollbackLogCallback.saveExecutionLog(
        format("Start deleting Virtual Machine Scale Set: [%s]", virtualMachineScaleSet));
    String vmssId = vmss.id();
    azureComputeClient.deleteVirtualMachineScaleSetById(azureConfig, subscriptionId, vmssId);
    bgRollbackLogCallback.saveExecutionLog(
        format("Successful deleted Virtual Machine Scale Set: [%s]", virtualMachineScaleSet), INFO, SUCCESS);
  }

  private AzureVMSSTaskExecutionResponse buildAzureVMSSTaskExecutionResponse() {
    AzureVMSSSwitchRoutesResponse switchRoutesTaskResponse = AzureVMSSSwitchRoutesResponse.builder().build();
    return AzureVMSSTaskExecutionResponse.builder()
        .azureVMSSTaskResponse(switchRoutesTaskResponse)
        .commandExecutionStatus(SUCCESS)
        .build();
  }
}

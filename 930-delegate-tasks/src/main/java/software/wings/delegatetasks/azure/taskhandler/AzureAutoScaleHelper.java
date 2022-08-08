/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.azure.model.AzureConstants.DEFAULT_AZURE_VMSS_DESIRED_INSTANCES;
import static io.harness.azure.model.AzureConstants.DEFAULT_AZURE_VMSS_MAX_INSTANCES;
import static io.harness.azure.model.AzureConstants.DEFAULT_AZURE_VMSS_MIN_INSTANCES;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureAutoScaleSettingsClient;
import io.harness.azure.client.AzureComputeClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureVMSSAutoScaleSettingsData;
import io.harness.delegate.task.azure.request.AzureVMSSSetupTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import com.microsoft.azure.management.monitor.AutoscaleProfile;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureAutoScaleHelper {
  @Inject private AzureAutoScaleSettingsClient azureAutoScaleSettingsClient;
  @Inject private AzureComputeClient azureComputeClient;
  @Inject private DelegateLogService delegateLogService;

  public AzureVMSSAutoScaleSettingsData getVMSSAutoScaleInstanceLimits(AzureConfig azureConfig,
      AzureVMSSSetupTaskParameters setupTaskParameters, VirtualMachineScaleSet mostRecentActiveVMSS,
      boolean isUseCurrentRunningCount, String commandUnit) {
    ExecutionLogCallback logCallback = getLogCallBack(setupTaskParameters, commandUnit);

    if (isUseCurrentRunningCount) {
      if (mostRecentActiveVMSS != null) {
        Optional<AutoscaleProfile> defaultAutoScaleProfileOp = azureAutoScaleSettingsClient.getDefaultAutoScaleProfile(
            azureConfig, setupTaskParameters.getSubscriptionId(), mostRecentActiveVMSS.resourceGroupName(),
            mostRecentActiveVMSS.id());
        return defaultAutoScaleProfileOp
            .map(defaultAutoScaleProfile
                -> getMostRecentActiveVMSSInstanceLimits(defaultAutoScaleProfile, mostRecentActiveVMSS, logCallback))
            .orElse(getMostRecentActiveVMSSInstanceLimits(mostRecentActiveVMSS, logCallback));
      }

      return getDefaultVMSSInstanceLimits(logCallback);
    }

    return getWorkflowInputVMSSInstanceLimits(setupTaskParameters, logCallback);
  }

  private AzureVMSSAutoScaleSettingsData getMostRecentActiveVMSSInstanceLimits(
      VirtualMachineScaleSet mostRecentActiveVMSS, ExecutionLogCallback logCallback) {
    int minInstances = mostRecentActiveVMSS.capacity();
    int maxInstances = mostRecentActiveVMSS.capacity();
    int desiredInstances = mostRecentActiveVMSS.capacity();
    String mostRecentActiveVMSSName = mostRecentActiveVMSS.name();

    logCallback.saveExecutionLog(format("Using currently running min: [%d], max: [%d], desired: [%d] from VMSS: [%s]",
                                     minInstances, maxInstances, desiredInstances, mostRecentActiveVMSSName),
        INFO, SUCCESS);

    return AzureVMSSAutoScaleSettingsData.builder()
        .minInstances(minInstances)
        .maxInstances(maxInstances)
        .desiredInstances(desiredInstances)
        .build();
  }

  private AzureVMSSAutoScaleSettingsData getMostRecentActiveVMSSInstanceLimits(AutoscaleProfile defaultAutoScaleProfile,
      VirtualMachineScaleSet mostRecentActiveVMSS, ExecutionLogCallback logCallback) {
    int minInstances = defaultAutoScaleProfile.minInstanceCount();
    int maxInstances = defaultAutoScaleProfile.maxInstanceCount();
    int desiredInstances = defaultAutoScaleProfile.defaultInstanceCount();
    String mostRecentActiveVMSSName = mostRecentActiveVMSS.name();

    logCallback.saveExecutionLog(format("Using currently running min: [%d], max: [%d], desired: [%d] from VMSS: [%s]",
                                     minInstances, maxInstances, desiredInstances, mostRecentActiveVMSSName),
        INFO, SUCCESS);

    return AzureVMSSAutoScaleSettingsData.builder()
        .minInstances(minInstances)
        .maxInstances(maxInstances)
        .desiredInstances(desiredInstances)
        .build();
  }

  private AzureVMSSAutoScaleSettingsData getDefaultVMSSInstanceLimits(ExecutionLogCallback logCallback) {
    int minInstances = DEFAULT_AZURE_VMSS_MIN_INSTANCES;
    int maxInstances = DEFAULT_AZURE_VMSS_MAX_INSTANCES;
    int desiredInstances = DEFAULT_AZURE_VMSS_DESIRED_INSTANCES;

    logCallback.saveExecutionLog(
        format("Using default min: [%d], max: [%d], desired: [%d]", minInstances, maxInstances, desiredInstances), INFO,
        SUCCESS);
    return AzureVMSSAutoScaleSettingsData.builder()
        .minInstances(minInstances)
        .maxInstances(maxInstances)
        .desiredInstances(desiredInstances)
        .build();
  }

  private AzureVMSSAutoScaleSettingsData getWorkflowInputVMSSInstanceLimits(
      AzureVMSSSetupTaskParameters setupTaskParameters, ExecutionLogCallback logCallback) {
    int minInstances = setupTaskParameters.getMinInstances();
    int maxInstances = setupTaskParameters.getMaxInstances();
    int desiredInstances = setupTaskParameters.getDesiredInstances();

    logCallback.saveExecutionLog(
        format("Using user defined input min: [%d], max: [%d] and desired: [%d] for deployment", minInstances,
            maxInstances, desiredInstances),
        INFO, SUCCESS);

    return AzureVMSSAutoScaleSettingsData.builder()
        .minInstances(minInstances)
        .maxInstances(maxInstances)
        .desiredInstances(desiredInstances)
        .build();
  }

  public List<String> getVMSSAutoScaleSettingsJSONs(
      AzureConfig azureConfig, String subscriptionId, VirtualMachineScaleSet mostRecentActiveVMSS) {
    if (mostRecentActiveVMSS == null) {
      return emptyList();
    }

    String resourceGroupName = mostRecentActiveVMSS.resourceGroupName();
    return getAutoScaleSettingsJSONs(azureConfig, subscriptionId, resourceGroupName, mostRecentActiveVMSS);
  }

  public List<String> getVMSSAutoScaleSettingsJSONs(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String virtualMachineScaleSetName) {
    Optional<VirtualMachineScaleSet> virtualMachineScaleSet = azureComputeClient.getVirtualMachineScaleSetByName(
        azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSetName);

    return virtualMachineScaleSet
        .map(vm -> getAutoScaleSettingsJSONs(azureConfig, subscriptionId, resourceGroupName, vm))
        .orElse(emptyList());
  }

  private List<String> getAutoScaleSettingsJSONs(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, VirtualMachineScaleSet virtualMachineScaleSet) {
    return azureAutoScaleSettingsClient
        .getAutoScaleSettingJSONByTargetResourceId(
            azureConfig, subscriptionId, resourceGroupName, virtualMachineScaleSet.id())
        .map(Collections::singletonList)
        .orElse(emptyList());
  }

  public ExecutionLogCallback getLogCallBack(AzureVMSSTaskParameters parameters, String commandUnit) {
    return new ExecutionLogCallback(
        delegateLogService, parameters.getAccountId(), parameters.getAppId(), parameters.getActivityId(), commandUnit);
  }
}

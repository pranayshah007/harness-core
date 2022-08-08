/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.taskhandler;

import static io.harness.azure.model.AzureConstants.DELETE_NEW_VMSS;
import static io.harness.azure.model.AzureConstants.NO_SCALING_POLICY_DURING_DOWN_SIZING;
import static io.harness.azure.model.AzureConstants.REQUEST_DELETE_SCALE_SET;
import static io.harness.azure.model.AzureConstants.SUCCESS_DELETE_SCALE_SET;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.request.AzureVMSSDeployTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.beans.command.ExecutionLogCallback;

import com.google.inject.Singleton;
import com.microsoft.azure.management.compute.VirtualMachineScaleSet;
import java.util.List;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSRollbackTaskHandler extends AzureVMSSDeployTaskHandler {
  @Override
  protected AzureVMSSTaskExecutionResponse executeTaskInternal(
      final AzureVMSSTaskParameters azureVMSSTaskParameters, final AzureConfig azureConfig) {
    AzureVMSSDeployTaskParameters deployTaskParameters = (AzureVMSSDeployTaskParameters) azureVMSSTaskParameters;
    try {
      AzureVMSSTaskExecutionResponse response = performRollBack(azureConfig, deployTaskParameters);
      deleteNewScaleSet(azureConfig, deployTaskParameters, response);
      return response;
    } catch (Exception ex) {
      return rollBackFailureResponse(deployTaskParameters, ExceptionMessageSanitizer.sanitizeException(ex));
    }
  }

  @Override
  protected AzureVMSSResizeDetail getScaleSetDetailsForUpSizing(AzureVMSSDeployTaskParameters deployTaskParameters) {
    String scaleSetNameForUpSize = deployTaskParameters.getOldVirtualMachineScaleSetName();
    int desiredCountForUpSize = deployTaskParameters.getPreDeploymentData().getDesiredCapacity();
    List<String> baseScalingPolicyJSONs = deployTaskParameters.getPreDeploymentData().getScalingPolicyJSON();
    return AzureVMSSResizeDetail.builder()
        .scaleSetName(scaleSetNameForUpSize)
        .desiredCount(desiredCountForUpSize)
        .scalingPolicyJSONs(baseScalingPolicyJSONs)
        .attachScalingPolicy(true)
        .scalingPolicyMessage("Attaching scaling policy for VMSS: [%s]")
        .build();
  }

  @Override
  protected AzureVMSSResizeDetail getScaleSetDetailsForDownSizing(AzureVMSSDeployTaskParameters deployTaskParameters) {
    String scaleSetNameForDownSize = deployTaskParameters.getNewVirtualMachineScaleSetName();
    int desiredCountForDownSize = 0;
    List<String> baseScalingPolicyJSONs = deployTaskParameters.getBaseScalingPolicyJSONs();
    return AzureVMSSResizeDetail.builder()
        .scaleSetName(scaleSetNameForDownSize)
        .desiredCount(desiredCountForDownSize)
        .scalingPolicyJSONs(baseScalingPolicyJSONs)
        .attachScalingPolicy(false)
        .scalingPolicyMessage(NO_SCALING_POLICY_DURING_DOWN_SIZING)
        .build();
  }

  private AzureVMSSTaskExecutionResponse performRollBack(
      AzureConfig azureConfig, AzureVMSSDeployTaskParameters deployTaskParameters) {
    deployTaskParameters.setResizeNewFirst(true);
    return super.executeTaskInternal(deployTaskParameters, azureConfig);
  }

  private void deleteNewScaleSet(AzureConfig azureConfig, AzureVMSSDeployTaskParameters deployTaskParameters,
      AzureVMSSTaskExecutionResponse response) {
    if (rollBackFailed(response)) {
      return;
    }

    Optional<VirtualMachineScaleSet> scaleSet =
        getScaleSet(azureConfig, deployTaskParameters, deployTaskParameters.getNewVirtualMachineScaleSetName());

    if (scaleSet.isPresent()) {
      String subscriptionId = deployTaskParameters.getSubscriptionId();
      VirtualMachineScaleSet virtualMachineScaleSet = scaleSet.get();
      ExecutionLogCallback logCallback = getLogCallBack(deployTaskParameters, DELETE_NEW_VMSS);
      logCallback.saveExecutionLog(format(REQUEST_DELETE_SCALE_SET, virtualMachineScaleSet.name()));
      azureComputeClient.deleteVirtualMachineScaleSetById(azureConfig, subscriptionId, virtualMachineScaleSet.id());
      logCallback.saveExecutionLog(format(SUCCESS_DELETE_SCALE_SET, virtualMachineScaleSet.name()), INFO, SUCCESS);
    }
  }

  private boolean rollBackFailed(AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse) {
    return azureVMSSTaskExecutionResponse.getCommandExecutionStatus() == FAILURE;
  }

  private AzureVMSSTaskExecutionResponse rollBackFailureResponse(
      AzureVMSSDeployTaskParameters deployTaskParameters, Exception ex) {
    ExecutionLogCallback logCallBack = getLogCallBack(deployTaskParameters, deployTaskParameters.getCommandName());
    String errorMessage = ExceptionUtils.getMessage(ex);
    logCallBack.saveExecutionLog(format("Exception: [%s].", errorMessage), ERROR);
    log.error(errorMessage, ex);
    return AzureVMSSTaskExecutionResponse.builder().errorMessage(errorMessage).commandExecutionStatus(FAILURE).build();
  }
}

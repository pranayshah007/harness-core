/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.provision.azure.AzureCommonHelper.DEFAULT_TIMEOUT;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters.AzureARMTaskNGParametersBuilder;
import static io.harness.delegate.task.azure.arm.AzureARMTaskType.ARM_DEPLOYMENT;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureConstants;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.azure.beans.AzureARMConfig;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class AzureARMRollbackStep extends CdTaskExecutable<AzureARMTaskNGResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_ROLLBACK_ARM_RESOURCE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private AzureCommonHelper azureCommonHelper;
  @Inject private AzureARMConfigDAL azureARMConfigDAL;
  @Inject private CDStepHelper cdStepHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    AzureARMRollbackStepParameters azureARMRollbackStepParameters =
        (AzureARMRollbackStepParameters) stepParameters.getSpec();
    if (isEmpty(getParameterFieldValue(azureARMRollbackStepParameters.getProvisionerIdentifier()))) {
      throw new InvalidRequestException("Provisioner Identifier can't be null or empty");
    }
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AzureARMRollbackStepParameters azureARMRollbackStepParameters =
        (AzureARMRollbackStepParameters) stepParameters.getSpec();
    log.info("Starting execution for the Azure Rollback Step");

    String provisionerId = getParameterFieldValue(azureARMRollbackStepParameters.getProvisionerIdentifier());
    AzureARMConfig azureARMConfig = azureARMConfigDAL.getAzureARMConfig(ambiance, provisionerId);
    String errorMessage = validateIfRollbackCanBePerformed(azureARMConfig, provisionerId);
    if (isNotEmpty(errorMessage)) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(errorMessage).build())
          .build();
    }

    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(azureARMConfig.getConnectorRef(), ambiance);
    if (!(connectorDTO.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format(
          "Invalid connector selected in Azure step. The connector type is %s. Please select a valid Azure connector",
          connectorDTO.getConnectorType()));
    }

    AzureARMTaskNGParameters taskNGParameters = getAzureTaskNGParams(ambiance, stepParameters,
        (AzureConnectorDTO) connectorDTO.getConnectorConfig(), azureARMConfig.getAzureARMPreDeploymentData());

    return obtainAzureRollbackTask(ambiance, stepParameters, taskNGParameters);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, ThrowingSupplier<AzureARMTaskNGResponse> responseDataSupplier)
      throws Exception {
    AzureARMTaskNGResponse response;
    String provisionerID =
        getParameterFieldValue(((AzureARMRollbackStepParameters) stepParameters.getSpec()).getProvisionerIdentifier());
    azureARMConfigDAL.clearAzureARMConfig(ambiance, provisionerID);

    try {
      response = responseDataSupplier.get();
    } catch (TaskNGDataException ex) {
      String errorMessage = String.format("Error while processing Azure Rollback Task response: %s", ex.getMessage());
      log.error(errorMessage, ex);
      throw ex;
    }

    if (response.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return azureCommonHelper.getFailureResponse(
          response.getUnitProgressData().getUnitProgresses(), response.getErrorMsg());
    }

    return StepResponse.builder()
        .unitProgressList(response.getUnitProgressData().getUnitProgresses())
        .stepOutcome(
            StepResponse.StepOutcome.builder()
                .name(OutcomeExpressionConstants.OUTPUT)
                .outcome(new AzureCreateARMResourceOutcome(azureCommonHelper.getARMOutputs(response.getOutputs())))
                .build())
        .status(Status.SUCCEEDED)
        .build();
  }

  private String validateIfRollbackCanBePerformed(AzureARMConfig azureARMConfig, String provisionerId) {
    if (azureARMConfig == null) {
      return format("There is no rollback data saved for the provisioner identifier: %s", provisionerId);
    }
    if (!AzureScopeTypesNames.ResourceGroup.equals(azureARMConfig.getScopeType())) {
      return format(
          "The only scope allowed to do rollback is ResourceGroup. %s is not supported", azureARMConfig.getScopeType());
    }
    return null;
  }

  private TaskRequest obtainAzureRollbackTask(
      Ambiance ambiance, StepElementParameters stepElementParameters, AzureARMTaskNGParameters data) {
    TaskData taskData = TaskData.builder()
                            .async(true)
                            .taskType(TaskType.AZURE_NG_ARM.name())
                            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT))
                            .parameters(new Object[] {data})
                            .build();
    return TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
        Arrays.asList(AzureConstants.EXECUTE_ARM_DEPLOYMENT, AzureConstants.ARM_DEPLOYMENT_STEADY_STATE,
            AzureConstants.ARM_DEPLOYMENT_OUTPUTS),
        TaskType.AZURE_NG_ARM.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(
            ((AzureARMRollbackStepParameters) stepElementParameters.getSpec()).getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private AzureARMTaskNGParameters getAzureTaskNGParams(Ambiance ambiance, StepElementParameters stepElementParameters,
      AzureConnectorDTO connectorConfig, AzureARMPreDeploymentData data) {
    AzureARMTaskNGParametersBuilder builder = AzureARMTaskNGParameters.builder();
    return builder.accountId(AmbianceUtils.getAccountId(ambiance))
        .scopeType(ARMScopeType.RESOURCE_GROUP)
        .rollback(true)
        .taskType(ARM_DEPLOYMENT)
        .templateBody(AppSettingsFile.create(data.getResourceGroupTemplateJson()))
        .resourceGroupName(data.getResourceGroup())
        .subscriptionId(data.getSubscriptionId())
        .connectorDTO(connectorConfig)
        .deploymentMode(AzureDeploymentMode.COMPLETE)
        .encryptedDataDetails(azureCommonHelper.getAzureEncryptionDetails(ambiance, connectorConfig))
        .timeoutInMs(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT))
        .build();
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.azure.beans.AzureARMTemplateDataOutput;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;

@OwnedBy(CDP)
public class AzureARMRollbackStep extends TaskExecutableWithRollbackAndRbac<AzureTaskExecutionResponse> {
  private static final String AZURE_TEMPLATE_DATA_FORMAT = "azureARMTemplateDataOutput_%s";

  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  @Inject private AzureCommonHelper azureCommonHelper;

  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AZURE_ROLLBACK_ARM_RESOURCE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.AZURE_ARM_BP_NG)) {
      throw new AccessDeniedException(
          "Azure Rollback NG is not enabled for this account. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<AzureTaskExecutionResponse> responseDataSupplier) throws Exception {
    return null;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return null;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return null;
  }

  private AzureARMTemplateDataOutput getAzureARMTemplateDataOutput(String provisionerIdentifier, Ambiance ambiance) {
    String identifier = azureCommonHelper.generateIdentifier(provisionerIdentifier, ambiance);
    String sweepingOutputKey = format(AZURE_TEMPLATE_DATA_FORMAT, identifier);

    OptionalSweepingOutput output =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(sweepingOutputKey));
    if (!output.isFound()) {
      return null;
    }
    return (AzureARMTemplateDataOutput) output.getOutput();
  }
}

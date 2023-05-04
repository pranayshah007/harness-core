/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plugin.AbstractContainerStepV2;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.util.Map;

public class GitCloneStep extends AbstractContainerStepV2<StepElementParameters> {
  public static final StepType STEP_TYPE = GitCloneStepInfo.STEP_TYPE;

  @Inject CdPluginCompatibleSerializer cdPluginCompatibleSerializer;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public long getTimeout(Ambiance ambiance, StepElementParameters stepElementParameters) {
    return Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
  }

  @Override
  public UnitStep getSerialisedStep(Ambiance ambiance, StepElementParameters stepParameters, String accountId,
      String logKey, long timeout, String parkedTaskId) {
    CIStepInfo ciStepInfo = (CIStepInfo) stepParameters.getSpec();
    return cdPluginCompatibleSerializer.serializeStepWithStepParameters((PluginCompatibleStep) ciStepInfo,
        getPort(ambiance, stepParameters.getIdentifier()), parkedTaskId, logKey, stepParameters.getIdentifier(),
        ParameterField.createValueField(Timeout.fromString(stepParameters.getTimeout().getValue())), accountId,
        stepParameters.getName(), null, ambiance);
  }

  @Override
  public StepResponse.StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // todo: if required to consume any output do it here.
    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // we need to check if rbac check is req or not.
  }
}

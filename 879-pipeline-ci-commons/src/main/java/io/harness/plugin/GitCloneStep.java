/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin;

import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.delegate.TaskSelector;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepExecutable;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.plugin.ContainerStepConstants;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GitCloneStep extends CommonAbstractStepExecutable {
  public static final StepType STEP_TYPE = GitCloneStepInfo.STEP_TYPE;

  public static String DELEGATE_SVC_ENDPOINT = "delegate-service:8080";

  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void resolveGitAppFunctor(Ambiance ambiance, CIStepInfo ciStepInfo) {
    // doNothing
  }

  public String getCompleteStepIdentifier(Ambiance ambiance, String stepIdentifier) {
    StringBuilder identifier = new StringBuilder();
    for (Level level : ambiance.getLevelsList()) {
      if (level.getStepType().getType().equals("STEP_GROUP")) {
        identifier.append(level.getIdentifier());
        identifier.append('_');
      }
    }
    identifier.append(stepIdentifier);
    return identifier.toString();
  }

  @Override
  public AsyncExecutableResponse executeVmAsyncAfterRbac(Ambiance ambiance, String completeStepIdentifier,
      String stepIdentifier, String runtimeId, CIStepInfo ciStepInfo, String accountId, String logKey,
      long timeoutInMillis, String stringTimeout, StageInfraDetails stageInfraDetails, StageDetails stageDetails) {
    // Not Implemented
    return null;
  }

  @Override
  public UnitStep serialiseStep(CIStepInfo ciStepInfo, String taskId, String logKey, String stepIdentifier,
      Integer port, String accountId, String stepName, String timeout, OSType os, Ambiance ambiance,
      StageDetails stageDetails, String podName) {
    // Not Implemented
    return null;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  public OSType getK8OS(Infrastructure infrastructure) {
    return OSType.Linux;
  }

  @Override
  protected String getDelegateSvcEndpoint(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContainerStepConstants.CONTAINER_EXECUTION_CONFIG));
    if (optionalSweepingOutput.isFound()) {
      ContainerExecutionConfig output = (ContainerExecutionConfig) optionalSweepingOutput.getOutput();
      return output.getDelegateServiceEndpointVariableValue();
    }
    return DELEGATE_SVC_ENDPOINT;
  }

  @Override
  public StepResponse handleVmStepResponse(Ambiance ambiance, String stepIdentifier,
      StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // Not Implemented
    return null;
  }

  public List<TaskSelector> fetchDelegateSelector(Ambiance ambiance) {
    return new ArrayList<>();
  }

  public String getUniqueStepIdentifier(Ambiance ambiance, String stepIdentifier) {
    return stepIdentifier;
  }

  @Override
  protected boolean getIsLocal(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContainerStepConstants.CONTAINER_EXECUTION_CONFIG));
    if (optionalSweepingOutput.isFound()) {
      ContainerExecutionConfig output = (ContainerExecutionConfig) optionalSweepingOutput.getOutput();
      return output.isLocal();
    }
    return false;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // we need to check if rbac check is req or not.
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {}
}

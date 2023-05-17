/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.codebase;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CodeBaseStep implements ChildExecutable<CodeBaseStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("CI_CODEBASE").setStepCategory(StepCategory.STEP).build();

  @Inject private ConnectorUtils connectorUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Override
  public Class<CodeBaseStepParameters> getStepParametersClass() {
    return CodeBaseStepParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, CodeBaseStepParameters stepParameters, StepInputPackage inputPackage) {
    String childNodeId = getChildNodeId(ambiance, stepParameters);
    return ChildExecutableResponse.newBuilder().setChildNodeId(childNodeId).build();
  }

  // determines if we are going to execute task to get additional information or we are going to execute sync task just
  // to expose data that we have
  private String getChildNodeId(Ambiance ambiance, CodeBaseStepParameters stepParameters) {
    String childNodeId = stepParameters.getCodeBaseSyncTaskId();
    ExecutionSource executionSource = getExecutionSource(ambiance, stepParameters.getExecutionSource());
    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.MANUAL) {
      ManualExecutionSource manualExecutionSource = (ManualExecutionSource) executionSource;
      if (isNotEmpty(manualExecutionSource.getPrNumber()) || isNotEmpty(manualExecutionSource.getBranch())
          || isNotEmpty(manualExecutionSource.getTag())) {
        String connectorRef = RunTimeInputHandler.resolveStringParameterV2("connectorRef", STEP_TYPE.getType(),
            ambiance.getStageExecutionId(), stepParameters.getConnectorRef(), false);
        ConnectorDetails connectorDetails =
            connectorUtils.getConnectorDetails(AmbianceUtils.getNgAccess(ambiance), connectorRef, true);
        boolean executeOnDelegate =
            connectorDetails.getExecuteOnDelegate() == null || connectorDetails.getExecuteOnDelegate();
        if (connectorUtils.hasApiAccess(connectorDetails) && executeOnDelegate) {
          childNodeId = stepParameters.getCodeBaseDelegateTaskId();
        }
      }
    }
    return childNodeId;
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, CodeBaseStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed execution for codebase node step [{}]", stepParameters);
    return createStepResponseFromChildResponse(responseDataMap);
  }

  private ExecutionSource getExecutionSource(Ambiance ambiance, ExecutionSource executionSource) {
    if (executionSource != null) {
      return executionSource;
    }
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage details sweeping output cannot be empty");
    }
    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();
    return stageDetails.getExecutionSource();
  }
}

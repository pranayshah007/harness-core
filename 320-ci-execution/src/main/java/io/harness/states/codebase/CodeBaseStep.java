/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.states.codebase;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.steps.StepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.stateutils.buildstate.ConnectorUtils;
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

    ExecutionSource executionSource = stepParameters.getExecutionSource();
    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.MANUAL) {
      ManualExecutionSource manualExecutionSource = (ManualExecutionSource) executionSource;
      if (isNotEmpty(manualExecutionSource.getPrNumber()) || isNotEmpty(manualExecutionSource.getBranch())
          || isNotEmpty(manualExecutionSource.getTag())) {
        ConnectorDetails connectorDetails =
            connectorUtils.getConnectorDetails(AmbianceUtils.getNgAccess(ambiance), stepParameters.getConnectorRef());
        if (connectorUtils.hasApiAccess(connectorDetails)) {
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
}

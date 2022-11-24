package io.harness.cdng.gitops.steps;

import io.harness.cdng.gitops.beans.FetchLinkedAppsStepParams;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.SyncExecutableWithRbac;

public class FetchLinkedAppsStep implements SyncExecutableWithRbac {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_FETCH_LINKED_APPS.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public Class getStepParametersClass() {
    return FetchLinkedAppsStepParams.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepParameters stepParameters) {}

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    return null;
  }
}

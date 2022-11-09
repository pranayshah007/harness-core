package io.harness.ci.states;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.ActionStepInfo;
import io.harness.pms.contracts.steps.StepType;

@OwnedBy(HarnessTeam.CI)
public class ActionStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = ActionStepInfo.STEP_TYPE;
}
package io.harness.cdng.chaos;

import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ChaosStepVariableCreator extends GenericStepVariableCreator {
  @Override
  public Set<String> getSupportedStepTypes() {
    return new HashSet<>(Arrays.asList(StepSpecTypeConstants.CHAOS_STEP));
  }
}

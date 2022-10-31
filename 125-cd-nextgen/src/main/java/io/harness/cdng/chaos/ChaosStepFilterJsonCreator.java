package io.harness.cdng.chaos;

import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.GenericStepPMSFilterJsonCreator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ChaosStepFilterJsonCreator extends GenericStepPMSFilterJsonCreator {
  @Override
  public Set<String> getSupportedStepTypes() {
    return new HashSet<>(Arrays.asList(StepSpecTypeConstants.CHAOS_STEP));
  }
}

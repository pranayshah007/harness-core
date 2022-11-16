package io.harness.plancreator.steps.pluginstep;

import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.plugin.PmsPluginStepNode;

import java.util.Collections;
import java.util.Set;

public class PmsPluginStepVariableCreator extends GenericStepVariableCreator<PmsPluginStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.PLUGIN_STEP);
  }
}

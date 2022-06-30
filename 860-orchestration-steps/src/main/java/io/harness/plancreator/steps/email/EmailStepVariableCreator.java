package io.harness.plancreator.steps.email;

import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Set;

public class EmailStepVariableCreator extends GenericStepVariableCreator<EmailStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.EMAIL);
  }

  @Override
  public Class<EmailStepNode> getFieldClass() {
    return EmailStepNode.class;
  }
}

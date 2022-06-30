package io.harness.plancreator.steps.email;

import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

public class EmailStepPlanCreator extends PMSStepPlanCreatorV2<EmailStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.EMAIL);
  }

  @Override
  public Class<EmailStepNode> getFieldClass() {
    return EmailStepNode.class;
  }
}

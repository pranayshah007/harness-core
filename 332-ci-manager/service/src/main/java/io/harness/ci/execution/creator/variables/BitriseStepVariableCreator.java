package io.harness.ci.creator.variables;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.BitriseStepNode;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import com.google.common.collect.Sets;
import java.util.Set;

public class BitriseStepVariableCreator extends GenericStepVariableCreator<BitriseStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.BITRISE.getDisplayName());
  }

  @Override
  public Class<BitriseStepNode> getFieldClass() {
    return BitriseStepNode.class;
  }
}

package io.harness.ci.creator.variables;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.ActionStepNode;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import com.google.common.collect.Sets;
import java.util.Set;

public class ActionStepVariableCreator extends GenericStepVariableCreator<ActionStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.ACTION.getDisplayName());
  }

  @Override
  public Class<ActionStepNode> getFieldClass() {
    return ActionStepNode.class;
  }
}

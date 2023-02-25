package io.harness.cdng.creator.variables;

import io.harness.cdng.gitops.syncstep.SyncStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

public class GitOpsSyncStepVariableCreator extends GenericStepVariableCreator<SyncStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.GITOPS_SYNC);
  }

  @Override
  public Class<SyncStepNode> getFieldClass() {
    return SyncStepNode.class;
  }
}

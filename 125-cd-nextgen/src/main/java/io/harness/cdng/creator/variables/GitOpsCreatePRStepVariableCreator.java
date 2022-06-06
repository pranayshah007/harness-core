package io.harness.cdng.creator.variables;

import io.harness.cdng.gitops.CreatePRStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

public class GitOpsCreatePRStepVariableCreator extends GenericStepVariableCreator<CreatePRStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.GITOPS_CREATE_PR);
  }

  @Override
  public Class<CreatePRStepNode> getFieldClass() {
    return CreatePRStepNode.class;
  }
}

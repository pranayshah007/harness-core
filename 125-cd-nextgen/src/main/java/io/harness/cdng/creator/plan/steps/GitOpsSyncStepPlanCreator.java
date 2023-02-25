package io.harness.cdng.creator.plan.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.syncstep.SyncStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.GITOPS)
public class GitOpsSyncStepPlanCreator extends CDPMSStepPlanCreatorV2<SyncStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.GITOPS_SYNC);
  }

  @Override
  public Class<SyncStepNode> getFieldClass() {
    return SyncStepNode.class;
  }
}

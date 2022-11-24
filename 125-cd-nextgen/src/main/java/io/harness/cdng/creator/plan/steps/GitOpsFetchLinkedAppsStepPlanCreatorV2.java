package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.beans.FetchLinkedAppsStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(GITOPS)
public class GitOpsFetchLinkedAppsStepPlanCreatorV2 extends CDPMSStepPlanCreatorV2<FetchLinkedAppsStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS);
  }

  @Override
  public Class<FetchLinkedAppsStepNode> getFieldClass() {
    return FetchLinkedAppsStepNode.class;
  }
}

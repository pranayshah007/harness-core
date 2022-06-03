package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.CreatePRStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(CDP)
public class GitOpsCreatePRStepPlanCreatorV2 extends CDPMSStepPlanCreatorV2<CreatePRStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.GITOPS_CREATE_PR);
  }

  @Override
  public Class<CreatePRStepNode> getFieldClass() {
    return CreatePRStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, CreatePRStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}

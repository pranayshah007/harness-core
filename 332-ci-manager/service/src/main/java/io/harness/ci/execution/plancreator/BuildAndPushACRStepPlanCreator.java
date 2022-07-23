package io.harness.ci.plancreator;

import com.google.common.collect.Sets;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.BuildAndPushACRNode;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import java.util.Set;

public class BuildAndPushACRStepPlanCreator extends CIPMSStepPlanCreatorV2<BuildAndPushACRNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Sets.newHashSet(CIStepInfoType.ACR.getDisplayName());
    }

    @Override
    public Class<BuildAndPushACRNode> getFieldClass() {
        return BuildAndPushACRNode.class;
    }

    @Override
    public PlanCreationResponse createPlanForField(PlanCreationContext ctx, BuildAndPushACRNode stepElement) {
        return super.createPlanForField(ctx, stepElement);
    }
}

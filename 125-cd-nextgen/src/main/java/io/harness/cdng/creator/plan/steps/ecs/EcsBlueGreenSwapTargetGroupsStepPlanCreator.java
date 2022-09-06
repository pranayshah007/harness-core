package io.harness.cdng.creator.plan.steps.ecs;

import com.google.common.collect.Sets;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepNode;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepNode;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepParameters;
import io.harness.cdng.ecs.EcsCanaryDeleteStepNode;
import io.harness.cdng.ecs.EcsCanaryDeleteStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import java.util.Set;

import static io.harness.cdng.visitor.YamlTypes.ECS_BLUE_GREEN_CREATE_SERVICE;
import static io.harness.cdng.visitor.YamlTypes.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS;

@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenSwapTargetGroupsStepPlanCreator extends CDPMSStepPlanCreatorV2<EcsBlueGreenSwapTargetGroupsStepNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Sets.newHashSet(StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS);
    }

    @Override
    public Class<EcsBlueGreenSwapTargetGroupsStepNode> getFieldClass() {
        return EcsBlueGreenSwapTargetGroupsStepNode.class;
    }

    @Override
    public PlanCreationResponse createPlanForField(PlanCreationContext ctx, EcsBlueGreenSwapTargetGroupsStepNode stepElement) {
        return super.createPlanForField(ctx, stepElement);
    }

    @Override
    protected StepParameters getStepParameters(PlanCreationContext ctx, EcsBlueGreenSwapTargetGroupsStepNode stepElement) {
        final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

        String ecsBlueGreenCreateServiceFnq = getExecutionStepFqn(ctx.getCurrentField(), ECS_BLUE_GREEN_CREATE_SERVICE);
        String ecsBlueGreenSwapTargetGroupsFnq = getExecutionStepFqn(ctx.getCurrentField(), ECS_BLUE_GREEN_SWAP_TARGET_GROUPS);
        EcsBlueGreenSwapTargetGroupsStepParameters ecsBlueGreenSwapTargetGroupsStepParameters =
                (EcsBlueGreenSwapTargetGroupsStepParameters) ((StepElementParameters) stepParameters).getSpec();
        ecsBlueGreenSwapTargetGroupsStepParameters.setEcsBlueGreenCreateServiceFnq(ecsBlueGreenCreateServiceFnq);
        ecsBlueGreenSwapTargetGroupsStepParameters.setEcsBlueGreenSwapTargetGroupsFnq(ecsBlueGreenSwapTargetGroupsFnq);
        return stepParameters;
    }
}

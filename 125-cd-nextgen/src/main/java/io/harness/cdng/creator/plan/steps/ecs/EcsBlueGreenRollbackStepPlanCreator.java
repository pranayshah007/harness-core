package io.harness.cdng.creator.plan.steps.ecs;

import com.google.common.collect.Sets;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStepNode;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStepParameters;
import io.harness.cdng.ecs.EcsRollingRollbackStepNode;
import io.harness.cdng.ecs.EcsRollingRollbackStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import java.util.Set;

import static io.harness.cdng.visitor.YamlTypes.ECS_BLUE_GREEN_CREATE_SERVICE;

@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<EcsBlueGreenRollbackStepNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Sets.newHashSet(StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK);
    }

    @Override
    public Class<EcsBlueGreenRollbackStepNode> getFieldClass() {
        return EcsBlueGreenRollbackStepNode.class;
    }

    @Override
    public PlanCreationResponse createPlanForField(PlanCreationContext ctx, EcsBlueGreenRollbackStepNode stepElement) {
        return super.createPlanForField(ctx, stepElement);
    }

    @Override
    protected StepParameters getStepParameters(PlanCreationContext ctx, EcsBlueGreenRollbackStepNode stepElement) {
        final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

        String ecsBlueGreenCreateServiceFnq = getExecutionStepFqn(ctx.getCurrentField(), ECS_BLUE_GREEN_CREATE_SERVICE);
        ((EcsBlueGreenRollbackStepParameters) ((StepElementParameters) stepParameters).getSpec())
                .setEcsBlueGreenCreateServiceFnq(ecsBlueGreenCreateServiceFnq);
        return stepParameters;
    }
}

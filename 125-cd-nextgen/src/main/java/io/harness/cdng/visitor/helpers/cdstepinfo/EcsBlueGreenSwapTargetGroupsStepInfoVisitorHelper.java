package io.harness.cdng.visitor.helpers.cdstepinfo;

import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepInfo;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class EcsBlueGreenSwapTargetGroupsStepInfoVisitorHelper implements ConfigValidator {
    @Override
    public Object createDummyVisitableElement(Object originalElement) {
        return EcsBlueGreenSwapTargetGroupsStepInfo.infoBuilder().build();
    }

    @Override
    public void validate(Object object, ValidationVisitor visitor) {
       // nothing to validate
    }
}

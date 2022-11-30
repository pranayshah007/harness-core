package io.harness.cdng.visitor.helpers.cdstepinfo;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.k8s.K8sScaleStepInfo;
import io.harness.cdng.tas.TasSwapRollbackStepInfo;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
public class TasSwapRollbackStepInfoVisitorHelper implements ConfigValidator {
    @Override
    public void validate(Object object, ValidationVisitor visitor) {
        // Nothing to validate.
    }

    @Override
    public Object createDummyVisitableElement(Object originalElement) {
        return TasSwapRollbackStepInfo.infoBuilder().build();
    }
}

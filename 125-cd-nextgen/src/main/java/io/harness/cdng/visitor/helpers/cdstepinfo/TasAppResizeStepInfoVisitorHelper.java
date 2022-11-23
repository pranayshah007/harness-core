package io.harness.cdng.visitor.helpers.cdstepinfo;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.k8s.K8sScaleStepInfo;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
public class TasAppResizeStepInfoVisitorHelper  implements ConfigValidator {
    @Override
    public void validate(Object object, ValidationVisitor visitor) {
        // Nothing to validate.
    }

    @Override
    public Object createDummyVisitableElement(Object originalElement) {
        return K8sScaleStepInfo.infoBuilder().build();
    }
}

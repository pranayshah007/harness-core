package io.harness.cdng.visitor.helpers.cdstepinfo.googlefunctions;

import io.harness.cdng.googlefunctions.rollback.GoogleFunctionsRollbackStepInfo;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class GoogleFunctionsRollbackStepInfoVisitorHelper implements ConfigValidator {
    @Override
    public Object createDummyVisitableElement(Object originalElement) {
        return GoogleFunctionsRollbackStepInfo.infoBuilder().build();
    }

    @Override
    public void validate(Object object, ValidationVisitor visitor) {
        // nothing to validate
    }
}
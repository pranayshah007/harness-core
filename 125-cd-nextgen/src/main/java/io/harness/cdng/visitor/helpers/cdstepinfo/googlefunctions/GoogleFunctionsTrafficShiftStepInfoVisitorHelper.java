package io.harness.cdng.visitor.helpers.cdstepinfo.googlefunctions;

import io.harness.cdng.googlefunctions.trafficShift.GoogleFunctionsTrafficShiftStepInfo;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class GoogleFunctionsTrafficShiftStepInfoVisitorHelper implements ConfigValidator {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return GoogleFunctionsTrafficShiftStepInfo.infoBuilder().build();
  }

  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // nothing to validate
  }
}

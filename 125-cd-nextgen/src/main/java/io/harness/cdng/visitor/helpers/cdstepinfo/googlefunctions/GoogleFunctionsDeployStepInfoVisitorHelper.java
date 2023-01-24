package io.harness.cdng.visitor.helpers.cdstepinfo.googlefunctions;

import io.harness.cdng.googlefunctions.deploy.GoogleFunctionsDeployStepInfo;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class GoogleFunctionsDeployStepInfoVisitorHelper implements ConfigValidator {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return GoogleFunctionsDeployStepInfo.infoBuilder().build();
  }

  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // nothing to validate
  }
}

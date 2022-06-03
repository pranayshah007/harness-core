package io.harness.cdng.visitor.helpers.cdstepinfo;

import io.harness.cdng.gitops.CreatePRStepInfo;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class CreatePRStepVisitorHelper implements ConfigValidator {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return CreatePRStepInfo.builder().build();
  }

  @Override
  public void validate(Object object, ValidationVisitor visitor) {}
}

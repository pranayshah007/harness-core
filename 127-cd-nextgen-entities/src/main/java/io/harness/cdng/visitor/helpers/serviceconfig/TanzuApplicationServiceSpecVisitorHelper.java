package io.harness.cdng.visitor.helpers.serviceconfig;

import io.harness.cdng.service.beans.TanzuApplicationServiceSpec;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class TanzuApplicationServiceSpecVisitorHelper implements ConfigValidator {
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return TanzuApplicationServiceSpec.builder().build();
  }
}

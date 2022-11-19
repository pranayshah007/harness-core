package io.harness.cdng.elastigroup;

import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class ElastigroupVisitorHelper implements ConfigValidator {
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // nothing to validate
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return ElastigroupConfiguration.builder().build();
  }
}
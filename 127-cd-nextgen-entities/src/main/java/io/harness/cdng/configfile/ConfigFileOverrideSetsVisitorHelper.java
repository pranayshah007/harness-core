package io.harness.cdng.configfile;

import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class ConfigFileOverrideSetsVisitorHelper implements ConfigValidator {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return ConfigFileOverrideSets.builder().build();
  }

  @Override
  public void validate(Object object, ValidationVisitor visitor) {}
}

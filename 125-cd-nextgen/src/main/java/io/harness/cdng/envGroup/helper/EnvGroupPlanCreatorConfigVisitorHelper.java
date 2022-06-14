package io.harness.cdng.envGroup.helper;

import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class EnvGroupPlanCreatorConfigVisitorHelper implements ConfigValidator {
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return EnvGroupPlanCreatorConfig.builder().build();
  }
}

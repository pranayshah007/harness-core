package io.harness.cdng.visitor.helpers.service;

import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import lombok.Builder;

@Builder
public class ServiceUseFromStageV2VisitorHelper implements ConfigValidator {
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return ServiceUseFromStageV2VisitorHelper.builder().build();
  }
}
package io.harness.cdng.visitor.helpers.manifest;

import io.harness.cdng.manifest.yaml.kinds.EcsScalableTargetDefinitionManifest;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class EcsScalableTargetDefinitionManifestVisitorHelper implements ConfigValidator {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    EcsScalableTargetDefinitionManifest ecsScalableTargetDefinitionManifest =
        (EcsScalableTargetDefinitionManifest) originalElement;
    return EcsScalableTargetDefinitionManifest.builder()
        .identifier(ecsScalableTargetDefinitionManifest.getIdentifier())
        .build();
  }

  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }
}

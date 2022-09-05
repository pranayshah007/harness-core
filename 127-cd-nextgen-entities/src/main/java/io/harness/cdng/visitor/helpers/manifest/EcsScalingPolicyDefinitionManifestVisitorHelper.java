package io.harness.cdng.visitor.helpers.manifest;

import io.harness.cdng.manifest.yaml.kinds.EcsScalingPolicyDefinitionManifest;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class EcsScalingPolicyDefinitionManifestVisitorHelper implements ConfigValidator {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    EcsScalingPolicyDefinitionManifest ecsScalingPolicyDefinitionManifest =
        (EcsScalingPolicyDefinitionManifest) originalElement;
    return EcsScalingPolicyDefinitionManifest.builder()
        .identifier(ecsScalingPolicyDefinitionManifest.getIdentifier())
        .build();
  }

  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }
}

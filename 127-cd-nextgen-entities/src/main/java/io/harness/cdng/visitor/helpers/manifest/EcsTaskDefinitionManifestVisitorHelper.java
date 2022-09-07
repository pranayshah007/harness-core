package io.harness.cdng.visitor.helpers.manifest;

import io.harness.cdng.manifest.yaml.kinds.EcsTaskDefinitionManifest;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class EcsTaskDefinitionManifestVisitorHelper implements ConfigValidator {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    EcsTaskDefinitionManifest ecsTaskDefinitionManifest = (EcsTaskDefinitionManifest) originalElement;
    return EcsTaskDefinitionManifest.builder().identifier(ecsTaskDefinitionManifest.getIdentifier()).build();
  }

  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }
}

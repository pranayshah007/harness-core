package io.harness.cdng.visitor.helpers.manifest;

import io.harness.cdng.manifest.yaml.kinds.EcsServiceDefinitionManifest;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class EcsServiceDefinitionManifestVisitorHelper implements ConfigValidator {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    EcsServiceDefinitionManifest ecsServiceDefinitionManifest = (EcsServiceDefinitionManifest) originalElement;
    return EcsServiceDefinitionManifest.builder().identifier(ecsServiceDefinitionManifest.getIdentifier()).build();
  }

  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate.
  }
}

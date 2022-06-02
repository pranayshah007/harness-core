package io.harness.cdng.visitor.helpers.manifest;

import io.harness.cdng.manifest.yaml.kinds.ReleaseRepoManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

public class ReleaseRepoManifestVisitorHelper implements ConfigValidator {
    @Override
    public void validate(Object object, ValidationVisitor visitor) {
        // Nothing to validate.
    }

    @Override
    public Object createDummyVisitableElement(Object originalElement) {
        ReleaseRepoManifest releaseRepoManifest = (ReleaseRepoManifest) originalElement;
        return ReleaseRepoManifest.builder().identifier(releaseRepoManifest.getIdentifier()).build();
    }
}

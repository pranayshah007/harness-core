/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.artifact;

import io.harness.cdng.artifact.bean.yaml.ArtifactOverrideSets;
import io.harness.walktree.visitor.DummyVisitableElement;

public class ArtifactOverridesVisitorHelper implements DummyVisitableElement {
  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    ArtifactOverrideSets artifactOverrideSets = (ArtifactOverrideSets) originalElement;
    return ArtifactOverrideSets.builder().identifier(artifactOverrideSets.getIdentifier()).build();
  }
}

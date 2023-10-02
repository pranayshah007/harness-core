/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.groupingstrategy;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.yaml.ManifestOutcome;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDP)
public class ManifestTaskTypeGroupImpl implements ManifestTaskTypeGroup {
  private final Set<ManifestGroupingStrategy> manifestsGroupingStrategy;
  @Inject
  public ManifestTaskTypeGroupImpl(Set<ManifestGroupingStrategy> manifestsGroupingStrategy) {
    this.manifestsGroupingStrategy = manifestsGroupingStrategy;
  }
  @Override
  public List<ManifestOutcome> group(List<ManifestOutcome> manifests) {
    List<ManifestOutcome> group = new ArrayList<>();
    for (ManifestOutcome manifest : manifests) {
      if (group.isEmpty() || isManifestInGroup(manifest, group)) {
        group.add(manifest);
      }
    }
    return group;
  }

  private boolean isManifestInGroup(ManifestOutcome manifest, List<ManifestOutcome> group) {
    boolean matches = false;
    for (ManifestGroupingStrategy strategy : manifestsGroupingStrategy) {
      if (strategy.canApply(manifest)) {
        ManifestOutcome toCheck = group.get(0);
        matches = strategy.isSameGroup(manifest, toCheck);
      }
    }
    return matches;
  }
}

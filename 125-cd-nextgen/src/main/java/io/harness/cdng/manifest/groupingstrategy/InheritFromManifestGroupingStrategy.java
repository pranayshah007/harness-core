/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.groupingstrategy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.ManifestOutcome;

import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class InheritFromManifestGroupingStrategy implements ManifestGroupingStrategy {
  private static final Set<String> SUPPORT_INHERIT_FROM_MANIFEST_TYPES =
      Set.of(ManifestType.K8Manifest, ManifestType.Kustomize, ManifestType.OpenshiftTemplate, ManifestType.HelmChart);

  @Override
  public boolean canApply(ManifestOutcome manifest) {
    return ManifestStoreType.InheritFromManifest.equals(manifest.getStore().getKind());
  }

  @Override
  public boolean isSameGroup(ManifestOutcome unprocessedManifest, ManifestOutcome groupManifest) {
    return matchesGroup(unprocessedManifest, groupManifest) || matchesGroup(groupManifest, unprocessedManifest);
  }

  private boolean matchesGroup(ManifestOutcome m1, ManifestOutcome m2) {
    return SUPPORT_INHERIT_FROM_MANIFEST_TYPES.contains(m1.getType())
        && ManifestStoreType.InheritFromManifest.equals(m2.getStore().getKind());
  }
}

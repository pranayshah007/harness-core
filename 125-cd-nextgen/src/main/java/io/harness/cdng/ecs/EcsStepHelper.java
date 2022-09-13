/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestOutcome;

import java.util.Collection;
import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.CDP)
public interface EcsStepHelper {
  List<ManifestOutcome> getEcsManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes);
  ManifestOutcome getEcsTaskDefinitionManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes);
  ManifestOutcome getEcsServiceDefinitionManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes);
  List<ManifestOutcome> getManifestOutcomesByType(
      @NotEmpty Collection<ManifestOutcome> manifestOutcomes, String manifestType);
}

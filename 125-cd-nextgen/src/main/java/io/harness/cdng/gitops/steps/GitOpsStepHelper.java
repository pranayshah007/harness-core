/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(GITOPS)
@Singleton
public class GitOpsStepHelper {
  @Inject private K8sStepHelper k8sStepHelper;

  public ManifestOutcome getReleaseRepoOutcome(Ambiance ambiance) {
    ManifestsOutcome manifestsOutcomes = k8sStepHelper.resolveManifestsOutcome(ambiance);

    List<ManifestOutcome> releaseRepoManifests =
        manifestsOutcomes.values()
            .stream()
            .filter(manifestOutcome -> ManifestType.ReleaseRepo.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());

    if (isEmpty(releaseRepoManifests)) {
      throw new InvalidRequestException("Release Repo Manifests are mandatory for Create PR step. Select one from "
              + String.join(", ", ManifestType.ReleaseRepo),
          USER);
    }

    if (releaseRepoManifests.size() > 1) {
      throw new InvalidRequestException("There can be only a single Release Repo manifest", USER);
    }
    return releaseRepoManifests.get(0);
  }
}

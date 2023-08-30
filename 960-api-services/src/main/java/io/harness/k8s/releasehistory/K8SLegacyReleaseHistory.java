/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Data
@Builder
@OwnedBy(CDP)
public class K8SLegacyReleaseHistory implements IK8sReleaseHistory {
  ReleaseHistory releaseHistory;

  @Override
  public int getAndIncrementLastReleaseNumber() {
    return releaseHistory.getAndIncrementLatestReleaseNumber();
  }

  @Override
  public IK8sRelease getLastSuccessfulRelease(int currentReleaseNumber) {
    return releaseHistory.getPreviousRollbackEligibleRelease(currentReleaseNumber);
  }

  @Override
  public IK8sRelease getLatestRelease() {
    if (isNotEmpty(releaseHistory.getReleases())) {
      return releaseHistory.getLatestRelease();
    }
    return null;
  }

  @Override
  public boolean isEmpty() {
    return releaseHistory != null && EmptyPredicate.isEmpty(releaseHistory.getReleases());
  }

  @Override
  public int size() {
    if (releaseHistory != null && releaseHistory.getReleases() != null) {
      return releaseHistory.getReleases().size();
    }
    return 0;
  }

  @Override
  public IK8sReleaseHistory cloneInternal() {
    return K8SLegacyReleaseHistory.builder().releaseHistory(releaseHistory.cloneInternal()).build();
  }

  @Override
  public List<IK8sRelease> getReleasesMatchingColor(String color, int currentReleaseNumber) {
    return releaseHistory.getReleases()
        .stream()
        .filter(release
            -> currentReleaseNumber != release.getReleaseNumber() && release.getManagedWorkload() != null
                && release.getManagedWorkload().getName().endsWith(color))
        .collect(Collectors.toList());
  }

  @Override
  public IK8sRelease getLatestSuccessfulReleaseMatchingColor(String color) {
    Optional<K8sLegacyRelease> latestSuccessfulRelease =
        releaseHistory.getReleases()
            .stream()
            .filter(release
                -> release.getManagedWorkload() != null && release.getManagedWorkload().getName().endsWith(color))
            .filter(release -> release.getReleaseStatus().equals(IK8sRelease.Status.Succeeded))
            .max(Comparator.comparing(IK8sRelease::getReleaseNumber));

    return latestSuccessfulRelease.orElse(null);
  }

  @Override
  public IK8sRelease getBlueGreenStageRelease() {
    if (isNotEmpty(releaseHistory.getReleases())) {
      return releaseHistory.getBlueGreenStageRelease();
    }
    return null;
  }
}

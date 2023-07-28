/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.WingsException;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.K8sYamlUtils;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease.Status;
import io.harness.k8s.utils.ObjectYamlUtils;

import com.esotericsoftware.yamlbeans.YamlException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(CDP)
public class ReleaseHistory {
  public static final String defaultVersion = "v1";

  private String version;
  private List<K8sLegacyRelease> releases;

  public static ReleaseHistory createNew() {
    ReleaseHistory releaseHistory = new ReleaseHistory();
    releaseHistory.setVersion(defaultVersion);
    releaseHistory.setReleases(new ArrayList<>());
    return releaseHistory;
  }

  public static ReleaseHistory createFromData(String releaseHistory) throws IOException {
    return new K8sYamlUtils().read(releaseHistory, ReleaseHistory.class);
  }

  public K8sLegacyRelease createNewRelease(List<KubernetesResourceId> resources) {
    int releaseNumber = getAndIncrementLatestReleaseNumber();
    this.getReleases().add(0,
        K8sLegacyRelease.builder()
            .number(releaseNumber)
            .status(IK8sRelease.Status.InProgress)
            .resources(resources)
            .build());

    return getLatestRelease();
  }

  public K8sLegacyRelease createNewReleaseWithResourceMap(List<KubernetesResource> resources) {
    int releaseNumber = getAndIncrementLatestReleaseNumber();
    this.getReleases().add(0,
        K8sLegacyRelease.builder()
            .number(releaseNumber)
            .status(IK8sRelease.Status.InProgress)
            .resources(resources.stream().map(KubernetesResource::getResourceId).collect(toList()))
            .resourcesWithSpec(resources)
            .build());

    return getLatestRelease();
  }

  public K8sLegacyRelease addReleaseToReleaseHistory(K8sLegacyRelease release) {
    this.getReleases().add(0, release);
    return release;
  }

  public int getAndIncrementLatestReleaseNumber() {
    int releaseNumber = 1;
    if (!this.getReleases().isEmpty()) {
      releaseNumber = getLatestRelease().getNumber() + 1;
    }
    return releaseNumber;
  }

  public K8sLegacyRelease getLatestRelease() {
    if (this.getReleases().isEmpty()) {
      throw new WingsException("No existing release found.");
    }

    return this.getReleases().get(0);
  }

  public void setReleaseStatus(Status status) {
    this.getLatestRelease().setStatus(status);
  }

  public void setReleaseNumber(int releaseNumber) {
    this.getLatestRelease().setNumber(releaseNumber);
  }

  public K8sLegacyRelease getLastSuccessfulRelease() {
    for (K8sLegacyRelease release : this.getReleases()) {
      if (release.getStatus() == IK8sRelease.Status.Succeeded) {
        return release;
      }
    }
    return null;
  }

  public K8sLegacyRelease getPreviousRollbackEligibleRelease(int currentReleaseNumber) {
    for (K8sLegacyRelease release : this.getReleases()) {
      if (release.getNumber() < currentReleaseNumber && release.getStatus() == IK8sRelease.Status.Succeeded) {
        return release;
      }
    }
    return null;
  }

  @Nullable
  public K8sLegacyRelease getRelease(int releaseNumber) {
    for (K8sLegacyRelease release : this.getReleases()) {
      if (release.getNumber() == releaseNumber) {
        return release;
      }
    }

    return null;
  }

  public String getAsYaml() throws YamlException {
    return ObjectYamlUtils.toYaml(this);
  }

  public void cleanup() {
    K8sLegacyRelease lastSuccessfulRelease = this.getLastSuccessfulRelease();
    int lastSuccessfulReleaseNumber = lastSuccessfulRelease != null ? lastSuccessfulRelease.getNumber() : 0;
    releases.removeIf(release
        -> release.getNumber() < lastSuccessfulReleaseNumber || IK8sRelease.Status.Failed == release.getStatus());
  }

  public ReleaseHistory cloneInternal() {
    return ReleaseHistory.builder().version(this.version).releases(new ArrayList<>(this.releases)).build();
  }

  public K8sLegacyRelease getBlueGreenStageRelease() {
    for (K8sLegacyRelease release : this.getReleases()) {
      if (HarnessLabelValues.bgStageEnv.equals(release.getBgEnvironment())) {
        return release;
      }
    }
    return null;
  }
}

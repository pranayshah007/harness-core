/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.ReleaseMetadata;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;

import java.util.List;
import javax.validation.constraints.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
public interface IK8sRelease {
  enum Status {
    InProgress,
    Succeeded,
    Failed;
  }

  Integer getReleaseNumber();
  Status getReleaseStatus();
  List<KubernetesResource> getResourcesWithSpecs();
  List<KubernetesResourceId> getResourceIds();
  String getReleaseColor();
  IK8sRelease setReleaseData(@NotNull List<KubernetesResource> resources, boolean isPruningEnabled);
  IK8sRelease setReleaseMetadata(ReleaseMetadata releaseMetadata);
  ReleaseMetadata getReleaseMetadata();
  IK8sRelease updateReleaseStatus(@NotNull Status status);
  String getBgEnvironment();
  String getManifestHash();
  void setBgEnvironment(@NotNull String bgEnvironment);
  void setManifestHash(@NotNull String manifestHash);
  void setHelmChartInfo(@NotNull HelmChartInfoDTO helmChartInfo);
  HelmChartInfoDTO getHelmChartInfo();
}

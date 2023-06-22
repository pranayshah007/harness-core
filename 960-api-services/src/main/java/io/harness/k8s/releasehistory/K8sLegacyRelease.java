/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(CDP)
public class K8sLegacyRelease implements IK8sRelease {
  private int number;
  private Status status;
  private List<KubernetesResourceId> resources;
  private KubernetesResourceId managedWorkload;
  private String managedWorkloadRevision;
  private String manifestHash;

  @Builder.Default private List<KubernetesResourceIdRevision> managedWorkloads = new ArrayList();
  @Builder.Default private List<KubernetesResource> customWorkloads = new ArrayList<>();
  @Builder.Default private List<KubernetesResource> resourcesWithSpec = new ArrayList<>();

  private String bgEnvironment;

  @Override
  public Integer getReleaseNumber() {
    return number;
  }

  @Override
  public Status getReleaseStatus() {
    return status;
  }

  @Override
  public List<KubernetesResource> getResourcesWithSpecs() {
    return resourcesWithSpec;
  }

  @Override
  public List<KubernetesResourceId> getResourceIds() {
    return resources;
  }

  @Override
  public String getReleaseColor() {
    return "";
  }

  @Override
  public IK8sRelease setReleaseData(List<KubernetesResource> resources, boolean isPruningEnabled) {
    if (isPruningEnabled) {
      List<KubernetesResource> resourcesWithPruningEnabled =
          resources.stream().filter(resource -> !resource.isSkipPruning()).collect(Collectors.toList());
      this.setResources(
          resourcesWithPruningEnabled.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList()));
      this.setResourcesWithSpec(resourcesWithPruningEnabled);
    } else {
      this.setResources(resources.stream().map(KubernetesResource::getResourceId).collect(Collectors.toList()));
    }
    return this;
  }

  @Override
  public IK8sRelease updateReleaseStatus(Status status) {
    this.status = status;
    return this;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class KubernetesResourceIdRevision {
    private KubernetesResourceId workload;
    private String revision;
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sLegacyRelease.KubernetesResourceIdRevision;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
public class K8sRollingRollbackHandlerConfig {
  private KubernetesConfig kubernetesConfig;
  private Kubectl client;
  private IK8sReleaseHistory releaseHistory;
  private IK8sRelease release;
  private Integer currentReleaseNumber;
  private IK8sRelease previousRollbackEligibleRelease;
  private boolean isNoopRollBack;
  List<KubernetesResourceIdRevision> previousManagedWorkloads = new ArrayList<>();
  List<KubernetesResource> previousCustomManagedWorkloads = new ArrayList<>();
  private boolean useDeclarativeRollback;
  List<KubernetesResource> previousResources = new ArrayList<>();
  private K8sDelegateTaskParams k8sDelegateTaskParams;

  // used when switching from declarative history to imperative history
  private boolean switchToLegacyReleaseHistory;
  private IK8sRelease latestDeclarativeRelease;
}

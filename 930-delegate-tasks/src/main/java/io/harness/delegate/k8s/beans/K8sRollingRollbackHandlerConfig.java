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
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.Release.KubernetesResourceIdRevision;

import io.kubernetes.client.openapi.models.V1Secret;
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
  private V1Secret release;
  private boolean isNoopRollBack;
  List<KubernetesResourceIdRevision> previousManagedWorkloads = new ArrayList<>();
  List<KubernetesResource> previousCustomManagedWorkloads = new ArrayList<>();
  List<V1Secret> releaseHistory = new ArrayList<>();
  List<KubernetesResource> previousResources = new ArrayList<>();
}

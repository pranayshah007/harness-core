/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.k8s.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.Release;
import io.harness.k8s.model.ReleaseHistory;

import io.kubernetes.client.openapi.models.V1Secret;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
public class K8sCanaryHandlerConfig extends K8sHandlerConfig {
  // TODO remove older release history vars once migration is complete
  private ReleaseHistory releaseHistory;
  private Release currentRelease;
  private KubernetesResource canaryWorkload;
  private Integer targetInstances;

  private List<V1Secret> releaseHistoryV2;
  private V1Secret currentReleaseSecret;
  private int currentReleaseNumber;
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.LogCallback;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class K8sReleaseCleanupDTO {
  IK8sReleaseHistory releaseHistory;
  KubernetesConfig kubernetesConfig;
  String releaseName;
  int currentReleaseNumber;
  LogCallback logCallback;

  // Used for legacy implementation, to be removed with corresponding FF
  Kubectl client;
  K8sDelegateTaskParams delegateTaskParams;
}

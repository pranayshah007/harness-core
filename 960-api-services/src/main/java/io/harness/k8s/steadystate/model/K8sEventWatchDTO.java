/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.KubernetesResourceId;

import io.kubernetes.client.openapi.ApiClient;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class K8sEventWatchDTO {
  ApiClient apiClient;
  Kubectl client;
  String eventInfoFormat;
  String eventErrorFormat;
  String releaseName;
  List<KubernetesResourceId> resourceIds;
  String workingDirectory;
  boolean isErrorFrameworkEnabled;
  OffsetDateTime startTime;
}

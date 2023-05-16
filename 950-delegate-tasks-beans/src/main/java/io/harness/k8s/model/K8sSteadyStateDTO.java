/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.logging.LogCallback;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class K8sSteadyStateDTO {
  K8sDeployRequest request;
  K8sDelegateTaskParams k8sDelegateTaskParams;
  List<KubernetesResourceId> resourceIds;
  LogCallback executionLogCallback;
  String namespace;
  boolean denoteOverallSuccess;
  boolean isErrorFrameworkEnabled;
  KubernetesConfig kubernetesConfig;
}

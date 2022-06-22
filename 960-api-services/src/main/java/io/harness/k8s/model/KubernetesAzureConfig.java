/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(exclude = {"aadIdToken"})
@OwnedBy(HarnessTeam.CDP)
public class KubernetesAzureConfig {
  private String clusterName;
  private String clusterUser;
  private String currentContext;
  private String apiServerId;
  private String clientId;
  private String configMode;
  private String tenantId;
  private String environment;
  private String aadIdToken;

  @Builder
  public KubernetesAzureConfig(String clusterName, String clusterUser, String currentContext, String apiServerId,
      String clientId, String configMode, String tenantId, String environment, String aadIdToken) {
    this.clusterName = clusterName;
    this.clusterUser = clusterUser;
    this.currentContext = currentContext;
    this.apiServerId = apiServerId;
    this.clientId = clientId;
    this.configMode = configMode;
    this.tenantId = tenantId;
    this.environment = environment;
    this.aadIdToken = aadIdToken;
  }
}

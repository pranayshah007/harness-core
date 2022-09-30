/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.intf;

import io.harness.ccm.remote.beans.K8sClusterSetupRequest;

import java.io.File;
import java.io.IOException;
import lombok.NonNull;

public interface CEYamlService {
  String DOT_YAML = ".yaml";
  String CLOUD_COST_K8S_CLUSTER_SETUP = "cloudCostK8sClusterSetup";
  String CLOUD_COST_K8S_CLUSTER_SETUP_V2 = "cloudCostK8sClusterSetupV2";

  // use unifiedCloudCostK8sClusterYaml
  @Deprecated
  File downloadCostOptimisationYaml(String accountId, String connectorIdentifier, String harnessHost, String serverName)
      throws IOException;

  @Deprecated
  String unifiedCloudCostK8sClusterYaml(@NonNull String accountId, String harnessHost, String serverName,
      @NonNull K8sClusterSetupRequest k8sClusterSetupRequest) throws IOException;

  String unifiedCloudCostK8sClusterYaml(@NonNull String accountId, String harnessHost, String serverName,
      @NonNull K8sClusterSetupRequest k8sClusterSetupRequest, boolean includeVisibility, boolean includeOptimization)
      throws IOException;
}

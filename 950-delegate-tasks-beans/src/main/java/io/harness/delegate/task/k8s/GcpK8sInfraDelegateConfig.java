/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.K8sConstants.AUTH_PLUGIN_VERSION_COMMAND;
import static io.harness.k8s.K8sConstants.GCP_AUTH_PLUGIN_BINARY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.k8s.model.kubeconfig.KubeConfigAuthPluginHelper;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
public class GcpK8sInfraDelegateConfig implements K8sInfraDelegateConfig {
  String namespace;
  String cluster;
  GcpConnectorDTO gcpConnectorDTO;
  List<EncryptedDataDetail> encryptionDataDetails;

  @Override
  public boolean isAuthPluginBinaryAvailable() {
    return KubeConfigAuthPluginHelper.isAuthPluginBinaryAvailable(GCP_AUTH_PLUGIN_BINARY);
  }
}

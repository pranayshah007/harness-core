/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.delegatetasks.cvng;

import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateTaskType;

import java.util.List;

public interface K8InfoDataService {
  @DelegateTaskType(TaskType.K8_FETCH_NAMESPACES)
  List<String> getNameSpaces(
      DataCollectionConnectorBundle bundle, List<EncryptedDataDetail> encryptedDataDetails, String filter);

  @DelegateTaskType(TaskType.K8_FETCH_WORKLOADS)
  List<String> getWorkloads(String namespace, DataCollectionConnectorBundle bundle,
      List<EncryptedDataDetail> encryptedDataDetails, String filter);

  @DelegateTaskType(TaskType.K8_FETCH_EVENTS)
  List<String> checkCapabilityToGetEvents(
      DataCollectionConnectorBundle bundle, List<EncryptedDataDetail> encryptedDataDetails);

  KubernetesConfig getDecryptedKubernetesConfig(
      KubernetesClusterConfigDTO kubernetesClusterConfig, List<EncryptedDataDetail> encryptedDataDetails);
}

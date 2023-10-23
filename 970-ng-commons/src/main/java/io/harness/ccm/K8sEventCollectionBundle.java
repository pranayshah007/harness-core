/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm;

import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class K8sEventCollectionBundle {
  // this identifier should belong to io.harness.delegate.beans.ConnectorDisconnectHandler.cek8s.CEKubernetesClusterConfigDTO
  @NotNull String connectorIdentifier;
  String orgIdentifier;
  String projectIdentifier;

  String clusterId;
  String cloudProviderId;
  String clusterName;
}

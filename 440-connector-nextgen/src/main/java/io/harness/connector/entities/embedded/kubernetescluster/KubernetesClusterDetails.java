/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.kubernetescluster;

import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.kubernetescluster.KubernetesClusterDetails")
public class KubernetesClusterDetails implements KubernetesCredential {
  String masterUrl;
  KubernetesAuthType authType;
  KubernetesAuth auth;
}

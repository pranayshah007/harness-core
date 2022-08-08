/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import static io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO.builder;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static io.harness.k8s.model.KubernetesClusterAuthType.NONE;

import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO.KubernetesClusterConfigDTOBuilder;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesOpenIdConnectDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesServiceAccountDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.exception.UnsupportedOperationException;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.ngmigration.beans.NgEntityDetail;

import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.Map;
import java.util.Set;

public class KubernetesConnectorImpl implements BaseConnector {
  @Override
  public String getSecretId(SettingAttribute settingAttribute) {
    return ((KubernetesClusterConfig) settingAttribute.getValue()).getEncryptedPassword();
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.KUBERNETES_CLUSTER;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(SettingAttribute settingAttribute, Set<CgEntityId> childEntities,
      Map<CgEntityId, NgEntityDetail> migratedEntities) {
    KubernetesClusterConfig clusterConfig = (KubernetesClusterConfig) settingAttribute.getValue();
    KubernetesClusterAuthType authType = clusterConfig.getAuthType() == null ? NONE : clusterConfig.getAuthType();
    KubernetesClusterConfigDTOBuilder builder = builder().delegateSelectors(clusterConfig.getDelegateSelectors());
    KubernetesCredentialDTO credentialDTO;
    switch (authType) {
      case USER_PASSWORD:
        credentialDTO = KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(MANUAL_CREDENTIALS)
                            .config(KubernetesClusterDetailsDTO.builder()
                                        .masterUrl(clusterConfig.getMasterUrl())
                                        .auth(KubernetesAuthDTO.builder()
                                                  .authType(KubernetesAuthType.USER_PASSWORD)
                                                  .credentials(KubernetesUserNamePasswordDTO.builder()
                                                                   .username(new String(clusterConfig.getUsername()))
                                                                   .passwordRef(null)
                                                                   .build())
                                                  .build())
                                        .build())
                            .build();
        break;
      case SERVICE_ACCOUNT:
        credentialDTO =
            KubernetesCredentialDTO.builder()
                .kubernetesCredentialType(MANUAL_CREDENTIALS)
                .config(KubernetesClusterDetailsDTO.builder()
                            .masterUrl(clusterConfig.getMasterUrl())
                            .auth(KubernetesAuthDTO.builder()
                                      .authType(KubernetesAuthType.SERVICE_ACCOUNT)
                                      .credentials(
                                          KubernetesServiceAccountDTO.builder().serviceAccountTokenRef(null).build())
                                      .build())
                            .build())
                .build();
        break;
      case OIDC:
        credentialDTO = KubernetesCredentialDTO.builder()
                            .kubernetesCredentialType(MANUAL_CREDENTIALS)
                            .config(KubernetesClusterDetailsDTO.builder()
                                        .masterUrl(clusterConfig.getMasterUrl())
                                        .auth(KubernetesAuthDTO.builder()
                                                  .authType(KubernetesAuthType.OPEN_ID_CONNECT)
                                                  .credentials(KubernetesOpenIdConnectDTO.builder().build())
                                                  .build())
                                        .build())
                            .build();
        break;
      case NONE:
        credentialDTO = KubernetesCredentialDTO.builder().kubernetesCredentialType(INHERIT_FROM_DELEGATE).build();
        break;
      default:
        throw new UnsupportedOperationException("K8s Auth type not supported");
    }

    return builder.credential(credentialDTO).build();
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.heartbeat;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.account.AccountClient;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.FeatureName;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.k8Connector.K8sValidationParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class K8sConnectorValidationParamsProvider implements ConnectorValidationParamsProvider {
  @Inject EncryptionHelper encryptionHelper;
  @Inject private AccountClient accountClient;

  @Override
  public ConnectorValidationParams getConnectorValidationParams(ConnectorInfoDTO connectorInfoDTO, String connectorName,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    ConnectorConfigDTO connectorConfigDTO = connectorInfoDTO.getConnectorConfig();
    List<DecryptableEntity> decryptableEntities = connectorConfigDTO.getDecryptableEntities();
    DecryptableEntity decryptableEntity = null;
    if (isNotEmpty(decryptableEntities)) {
      decryptableEntity = decryptableEntities.get(0);
    }
    final List<EncryptedDataDetail> encryptionDetail =
        encryptionHelper.getEncryptionDetail(decryptableEntity, accountIdentifier, orgIdentifier, projectIdentifier);
    return K8sValidationParams.builder()
        .kubernetesClusterConfigDTO((KubernetesClusterConfigDTO) connectorConfigDTO)
        .connectorName(connectorName)
        .encryptedDataDetails(encryptionDetail)
        .useSocketCapability(useSocketCapability(accountIdentifier))
        .build();
  }

  private boolean useSocketCapability(String accountIdentifier) {
    try {
      return getResponse(
          accountClient.isFeatureFlagEnabled(FeatureName.CDS_K8S_SOCKET_CAPABILITY_CHECK_NG.name(), accountIdentifier));
    } catch (Exception e) {
      log.warn("Unable to evaluate FF {} for account {}", FeatureName.CDS_K8S_SOCKET_CAPABILITY_CHECK_NG.name(),
          accountIdentifier);
    }

    return false;
  }
}

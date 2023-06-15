/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.beans.FeatureName.CDS_K8S_SOCKET_CAPABILITY_CHECK_NG;

import static software.wings.beans.TaskType.VALIDATE_KUBERNETES_CONFIG;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.validator.scmValidators.AbstractKubernetesConnectorValidator;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskParams;
import io.harness.delegate.task.TaskParameters;
import io.harness.remote.client.CGRestUtils;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.DX)
public class KubernetesConnectionValidator extends AbstractKubernetesConnectorValidator {
  @Inject private AccountClient accountClient;

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO kubernetesClusterConfig, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    var responseData = super.validateConnector(
        kubernetesClusterConfig, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return responseData.getConnectorValidationResult();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }

  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    KubernetesClusterConfigDTO kubernetesClusterConfig = (KubernetesClusterConfigDTO) connectorConfig;
    List<EncryptedDataDetail> encryptedDataDetailList =
        super.fetchEncryptionDetailsList(kubernetesClusterConfig, accountIdentifier, orgIdentifier, projectIdentifier);

    return KubernetesConnectionTaskParams.builder()
        .kubernetesClusterConfig(kubernetesClusterConfig)
        .encryptionDetails(encryptedDataDetailList)
        .useSocketCapability(useSocketCapabilityCheck(accountIdentifier))
        .build();
  }

  @Override
  public String getTaskType() {
    return VALIDATE_KUBERNETES_CONFIG.name();
  }

  private boolean useSocketCapabilityCheck(String accountIdentifier) {
    try {
      return CGRestUtils.getResponse(
          accountClient.isFeatureFlagEnabled(CDS_K8S_SOCKET_CAPABILITY_CHECK_NG.name(), accountIdentifier));
    } catch (Exception e) {
      log.warn("Unable to retrieve status of FF {} for account {}", CDS_K8S_SOCKET_CAPABILITY_CHECK_NG.name(),
          accountIdentifier, e);
    }

    return false;
  }
}

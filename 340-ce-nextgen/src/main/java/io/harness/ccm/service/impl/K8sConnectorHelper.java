/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.ccm.health.CEError.K8S_PERMISSIONS_MISSING;
import static io.harness.ccm.health.CEError.METRICS_SERVER_NOT_FOUND;
import static io.harness.ccm.health.CEError.NODES_IS_FORBIDDEN;
import static io.harness.ccm.health.CEError.PERPETUAL_TASK_CREATION_FAILURE;
import static io.harness.ccm.health.CEError.PVC_PERMISSION_ERROR;

import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.commons.entities.events.CeExceptionRecord;
import io.harness.ccm.health.CEError;
import io.harness.ccm.health.CeExceptionRecordDao;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class K8sConnectorHelper {
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject private SecretManagerClientService ngSecretService;
  @Inject CeExceptionRecordDao ceExceptionRecordDao;

  public List<EncryptedDataDetail> getEncryptionDetail(KubernetesClusterConfigDTO kubernetesClusterConfigDTO,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (kubernetesClusterConfigDTO.getCredential().getKubernetesCredentialType()
        == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesAuthCredentialDTO kubernetesAuthCredential = getKubernetesAuthCredential(
          (KubernetesClusterDetailsDTO) kubernetesClusterConfigDTO.getCredential().getConfig());

      if (kubernetesAuthCredential == null) {
        return null;
      }
      NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                         .accountIdentifier(accountIdentifier)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .build();

      return ngSecretService.getEncryptionDetails(basicNGAccessObject, kubernetesAuthCredential);
    }

    return null;
  }

  private KubernetesAuthCredentialDTO getKubernetesAuthCredential(
      KubernetesClusterDetailsDTO kubernetesClusterConfigDTO) {
    return kubernetesClusterConfigDTO.getAuth().getCredentials();
  }

  public ConnectorConfigDTO getConnectorConfig(
      String connectorIdentifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Optional<ConnectorDTO> response = NGRestUtils.getResponse(
        connectorResourceClient.get(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier));

    if (response.isPresent()) {
      return response.get().getConnectorInfo().getConnectorConfig();
    }

    throw new InvalidArgumentsException(String.format("connectorIdentifier=[%s] in org=[%s], project=[%s] doesnt exist",
        connectorIdentifier, orgIdentifier, projectIdentifier));
  }

  public List<String> getErrors(ClusterRecord clusterRecord) {
    List<CEError> errors = new ArrayList<>();
    if (null == clusterRecord.getPerpetualTaskId()) {
      log.warn("The cluster id={} encounters the error {}.", clusterRecord.getUuid(),
          PERPETUAL_TASK_CREATION_FAILURE.getMessage());
      return new ArrayList<>();
    }

    long recentTimestamp = Instant.now().minus(3, ChronoUnit.MINUTES).toEpochMilli();
    CeExceptionRecord ceExceptionRecord =
        ceExceptionRecordDao.getRecentException(clusterRecord.getAccountId(), clusterRecord.getUuid(), recentTimestamp);

    if (ceExceptionRecord != null) {
      String exceptionMessage = ceExceptionRecord.getMessage();
      metricsServerErrorCheck(exceptionMessage, errors);
    }
    return getMessages(clusterRecord, errors);
  }

  private void metricsServerErrorCheck(final String exceptionMessage, final List<CEError> errors) {
    CEError ceError = null;

    // on first level check by code=[4xx], then in inner block check for specific message related to resource
    if (exceptionMessage.startsWith("code=[401]")
        || (exceptionMessage.contains("\\\"message\\\":\\\"Unauthorized\\\""))) {
      ceError = K8S_PERMISSIONS_MISSING;
    } else if (exceptionMessage.startsWith("code=[403]")) {
      // generaly the 403 is due to 'nodes is forbidden: User \\\"system:anonymous\\\" cannot list resource
      // \\\"nodes\\\" in API group \\\"\\\" at the cluster scope\"'
      ceError = K8S_PERMISSIONS_MISSING;
      if (exceptionMessage.contains("persistentvolumeclaims")) {
        ceError = PVC_PERMISSION_ERROR;
      } else if (exceptionMessage.contains("nodes is forbidden")
          || exceptionMessage.contains("nodes.metrics.k8s.io is forbidden")) {
        ceError = NODES_IS_FORBIDDEN;
      }
    } else if (exceptionMessage.startsWith("code=[404]")) {
      ceError = METRICS_SERVER_NOT_FOUND;
    }

    if (ceError != null) {
      errors.add(ceError);
    }
  }

  private List<String> getMessages(ClusterRecord clusterRecord, List<CEError> errors) {
    List<String> messages = new ArrayList<>();
    log.info("errors list {}", errors);
    for (CEError error : errors) {
      switch (error) {
        default:
          messages.add(error.getMessage());
          log.warn("The cluster id={} encounters an unexpected error {}.", clusterRecord.getUuid(), error.getMessage());
          break;
      }
    }
    return messages;
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.k8s.cluster.resources.rancher;

import static io.harness.cdng.artifact.utils.ArtifactUtils.getTaskSetupAbstractions;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.TaskType.RANCHER_LIST_CLUSTERS_TASK_NG;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.rancher.RancherConnectorDTO;
import io.harness.delegate.beans.connector.rancher.RancherListClustersTaskResponse;
import io.harness.delegate.beans.connector.rancher.RancherTaskParams;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class RancherClusterHelper {
  @Inject @Named(DEFAULT_CONNECTOR_SERVICE) private ConnectorService connectorService;
  @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private ExceptionManager exceptionManager;

  private static final String CONNECTOR_NOT_FOUND_MESSAGE = "Connector not found for identifier [%s], scope: [%s]";
  private static final String LIST_CLUSTERS_ERROR_MESSAGE = "Failed to list rancher clusters. Error: ";

  DelegateResponseData executeListClustersDelegateTask(RancherTaskParams taskParams, BaseNGAccess baseNGAccess) {
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(baseNGAccess.getAccountIdentifier())
            .taskType(RANCHER_LIST_CLUSTERS_TASK_NG.name())
            .taskParameters(taskParams)
            .executionTimeout(Duration.ofMinutes(1))
            .taskSetupAbstractions(getTaskSetupAbstractions(baseNGAccess))
            .taskSelectors(taskParams.getRancherConnectorDTO().getDelegateSelectors())
            .build();

    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  List<EncryptedDataDetail> getEncryptionDetails(RancherConnectorDTO rancherConnectorDTO, BaseNGAccess baseNGAccess) {
    List<DecryptableEntity> decryptableEntities = rancherConnectorDTO.getDecryptableEntities();
    if (isEmpty(decryptableEntities)) {
      return Collections.emptyList();
    }
    return secretManagerClientService.getEncryptionDetails(baseNGAccess, decryptableEntities.get(0));
  }

  RancherConnectorDTO getRancherConnector(IdentifierRef connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTOOptional = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

    if (connectorDTOOptional.isEmpty() || !isRancherConnector(connectorDTOOptional.get())) {
      String errorMessage = format(CONNECTOR_NOT_FOUND_MESSAGE, connectorRef.getIdentifier(), connectorRef.getScope());
      throw new InvalidRequestException(errorMessage);
    }
    ConnectorInfoDTO connectors = connectorDTOOptional.get().getConnector();
    return (RancherConnectorDTO) connectors.getConnectorConfig();
  }

  static void throwExceptionIfTaskFailed(DelegateResponseData delegateTaskResponse) {
    if (delegateTaskResponse instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) delegateTaskResponse;
      throw new InvalidRequestException(LIST_CLUSTERS_ERROR_MESSAGE + errorNotifyResponseData.getErrorMessage());
    }
    RancherListClustersTaskResponse taskResponse = (RancherListClustersTaskResponse) delegateTaskResponse;
    if (taskResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      throw new InvalidRequestException(LIST_CLUSTERS_ERROR_MESSAGE + taskResponse.getErrorMessage());
    }
  }

  private static boolean isRancherConnector(ConnectorResponseDTO connectorResponse) {
    return ConnectorType.RANCHER == connectorResponse.getConnector().getConnectorType();
  }
}

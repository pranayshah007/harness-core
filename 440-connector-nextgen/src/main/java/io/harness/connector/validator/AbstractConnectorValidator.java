/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.NGConstants.CONNECTOR_STRING;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.ConnectorValidationResponseData;
import io.harness.delegate.beans.connector.ConnectorValidationResponseTaskIdDecorator;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.ngexception.ConnectorValidationException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.DX)
@Slf4j
public abstract class AbstractConnectorValidator implements ConnectionValidator {
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private EncryptionHelper encryptionHelper;
  @Inject @Named("connectorDecoratorService") private ConnectorService connectorService;
  @Inject private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;
  @Inject Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Inject ExceptionManager exceptionManager;

  public <T extends ConnectorConfigDTO> ConnectorValidationResponseData validateConnector(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    var responseDataPair =
        validateConnectorReturnPair(connectorConfig, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    ConnectorValidationResponseData validationResponse = (ConnectorValidationResponseData) responseDataPair.getValue();
    String taskId = responseDataPair.getKey();
    return ConnectorValidationResponseTaskIdDecorator.builder().response(validationResponse).taskId(taskId).build();
  }

  public <T extends ConnectorConfigDTO> Pair<String, DelegateResponseData> validateConnectorReturnPair(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    TaskParameters taskParameters =
        getTaskParameters(connectorConfig, accountIdentifier, orgIdentifier, projectIdentifier);

    DelegateTaskRequest delegateTaskRequest = DelegateTaskHelper.buildDelegateTask(
        taskParameters, connectorConfig, getTaskType(), accountIdentifier, orgIdentifier, projectIdentifier);

    DelegateResponseData responseData;
    String taskId;
    try {
      var responseEntry = delegateGrpcClientWrapper.executeSyncTaskV2ReturnTaskId(delegateTaskRequest);
      responseData = responseEntry.getValue();
      taskId = responseEntry.getKey();
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }

    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      log.info("Error in validation task for connector : [{}] with failure types [{}]",
          errorNotifyResponseData.getErrorMessage(), errorNotifyResponseData.getFailureTypes());
      throw new ConnectorValidationException(errorNotifyResponseData.getErrorMessage());
    } else if (responseData instanceof RemoteMethodReturnValueData
        && (((RemoteMethodReturnValueData) responseData).getException() instanceof InvalidRequestException)) {
      String errorMessage =
          ((InvalidRequestException) ((RemoteMethodReturnValueData) responseData).getException()).getMessage();
      throw new ConnectorValidationException(errorMessage);
    }
    return Pair.of(taskId, responseData);
  }

  public ConnectorValidationResult validateConnectorViaManager(ConnectorConfigDTO connectorConfigDTO,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    AtomicReference<ConnectorValidationHandler> connectorValidationHandler = new AtomicReference<>();

    final Optional<ConnectorResponseDTO> connectorResponseDTO =
        connectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    final ConnectorValidationParams connectorValidationParams =
        connectorResponseDTO
            .map(connectorResponse -> {
              ConnectorInfoDTO connectorInfoDTO = connectorResponse.getConnector();
              connectorInfoDTO.setConnectorConfig(connectorConfigDTO);
              ConnectorType connectorType = connectorInfoDTO.getConnectorType();
              connectorValidationHandler.set(
                  connectorTypeToConnectorValidationHandlerMap.get(connectorType.getDisplayName()));
              return connectorValidationParamsProviderMap.get(connectorType.getDisplayName())
                  .getConnectorValidationParams(connectorInfoDTO, connectorInfoDTO.getName(), accountIdentifier,
                      orgIdentifier, projectIdentifier);
            })
            .orElseThrow(()
                             -> new InvalidRequestException(String.format(
                                 CONNECTOR_STRING, identifier, accountIdentifier, orgIdentifier, projectIdentifier)));

    return connectorValidationHandler.get().validate(connectorValidationParams, accountIdentifier);
  }

  public List<EncryptedDataDetail> getEncryptionDetail(
      DecryptableEntity decryptableEntity, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return encryptionHelper.getEncryptionDetail(decryptableEntity, accountIdentifier, orgIdentifier, projectIdentifier);
  }

  public abstract <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier);

  public abstract String getTaskType();
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.TaskType.CVNG_CONNECTOR_VALIDATE_TASK;

import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorTaskParams;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.ng.core.dto.ErrorDetail;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class CVConnectorValidator extends AbstractConnectorValidator {
  @Inject private final SecretManagerClientService ngSecretService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private NGErrorHelper ngErrorHelper;

  private String getDelegateId(DelegateTaskNotifyResponseData cvConnectorTaskResponse) {
    if (cvConnectorTaskResponse == null || cvConnectorTaskResponse.getDelegateMetaInfo() == null) {
      return null;
    }
    return cvConnectorTaskResponse.getDelegateMetaInfo().getId();
  }

  private List<ErrorDetail> getErrorDetail(String errorMessage) {
    return Collections.singletonList(
        ErrorDetail.builder().message(errorMessage).code(450).reason("Invalid Credentials").build());
  }

  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<DecryptableEntity> decryptableEntities = connectorConfig.getDecryptableEntities();
    List<List<EncryptedDataDetail>> encryptedDetails = new ArrayList<>();
    if (!isEmpty(decryptableEntities)) {
      decryptableEntities.forEach(decryptableEntity
          -> encryptedDetails.add(
              super.getEncryptionDetail(decryptableEntity, accountIdentifier, orgIdentifier, projectIdentifier)));
    }

    return CVConnectorTaskParams.builder()
        .connectorConfigDTO(connectorConfig)
        .encryptionDetails(encryptedDetails)
        .build();
  }

  @Override
  public String getTaskType() {
    return CVNG_CONNECTOR_VALIDATE_TASK.name();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO connectorDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    var responseEntry = super.validateConnectorReturnPair(
        connectorDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    final DelegateResponseData delegateResponseData = responseEntry.getValue();
    String taskId = responseEntry.getKey();
    CVConnectorTaskResponse cvConnectorTaskResponse = (CVConnectorTaskResponse) delegateResponseData;
    return ConnectorValidationResult.builder()
        .status(cvConnectorTaskResponse.isValid() ? ConnectivityStatus.SUCCESS : ConnectivityStatus.FAILURE)
        .errorSummary(cvConnectorTaskResponse.getErrorMessage())
        .delegateId(getDelegateId(cvConnectorTaskResponse))
        .taskId(taskId)
        .build();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}

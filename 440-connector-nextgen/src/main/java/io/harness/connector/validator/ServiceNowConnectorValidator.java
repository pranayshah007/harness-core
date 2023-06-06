/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectionTaskParams;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.connection.ServiceNowTestConnectionTaskNGResponse;
import io.harness.delegate.task.TaskParameters;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
@Singleton
public class ServiceNowConnectorValidator extends AbstractConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = (ServiceNowConnectorDTO) connectorConfig;
    DecryptableEntity decryptableEntity =
        (isNull(serviceNowConnectorDTO.getAuth()) || isNull(serviceNowConnectorDTO.getAuth().getCredentials()))
        ? serviceNowConnectorDTO
        : serviceNowConnectorDTO.getAuth().getCredentials();
    return ServiceNowConnectionTaskParams.builder()
        .serviceNowConnectorDTO(serviceNowConnectorDTO)
        .encryptionDetails(
            super.getEncryptionDetail(decryptableEntity, accountIdentifier, orgIdentifier, projectIdentifier))
        .build();
  }

  @Override
  public String getTaskType() {
    return "SERVICENOW_CONNECTIVITY_TASK_NG";
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO jiraConnectorDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    var responseEntry = super.validateConnectorReturnPair(
        jiraConnectorDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    ServiceNowTestConnectionTaskNGResponse delegateResponseData =
        (ServiceNowTestConnectionTaskNGResponse) responseEntry.getValue();
    String taskId = responseEntry.getKey();
    return ConnectorValidationResult.builder()
        .delegateId(delegateResponseData.getDelegateMetaInfo() == null
                ? null
                : delegateResponseData.getDelegateMetaInfo().getId())
        .status(delegateResponseData.getCanConnect() ? ConnectivityStatus.SUCCESS : ConnectivityStatus.FAILURE)
        .errorSummary(delegateResponseData.getErrorMessage())
        .taskId(taskId)
        .build();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}

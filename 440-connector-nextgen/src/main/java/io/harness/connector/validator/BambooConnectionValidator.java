/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static software.wings.beans.TaskType.BAMBOO_CONNECTIVITY_TEST_TASK;

import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.bamboo.BambooAuthCredentialsDTO;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.beans.connector.bamboo.BambooTestConnectionTaskParams;
import io.harness.delegate.task.TaskParameters;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BambooConnectionValidator extends AbstractConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    BambooConnectorDTO bambooConnectorDTO = (BambooConnectorDTO) connectorConfig;
    BambooAuthCredentialsDTO bambooAuthCredentialsDTO =
        bambooConnectorDTO.getAuth() != null ? bambooConnectorDTO.getAuth().getCredentials() : null;
    return BambooTestConnectionTaskParams.builder()
        .bambooConnectorDTO(bambooConnectorDTO)
        .encryptionDetails(
            super.getEncryptionDetail(bambooAuthCredentialsDTO, accountIdentifier, orgIdentifier, projectIdentifier))
        .build();
  }

  @Override
  public String getTaskType() {
    return BAMBOO_CONNECTIVITY_TEST_TASK.name();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO bambooConnector, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    var responseData =
        super.validateConnector(bambooConnector, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return responseData.getConnectorValidationResult();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}

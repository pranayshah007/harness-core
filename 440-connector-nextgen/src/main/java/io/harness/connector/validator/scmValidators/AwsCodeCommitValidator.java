/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator.scmValidators;

import static software.wings.beans.TaskType.NG_AWS_CODE_COMMIT_TASK;

import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.validator.AbstractConnectorValidator;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.awscodecommitconnector.AwsCodeCommitTaskParams;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.task.TaskParameters;

import java.util.List;

public class AwsCodeCommitValidator extends AbstractConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    AwsCodeCommitConnectorDTO connectorDTO = (AwsCodeCommitConnectorDTO) connectorConfig;
    final List<DecryptableEntity> decryptableEntities = connectorDTO.getDecryptableEntities();
    return AwsCodeCommitTaskParams.builder()
        .awsConnector(connectorDTO)
        .encryptionDetails(
            super.getEncryptionDetail(decryptableEntities.get(0), accountIdentifier, orgIdentifier, projectIdentifier))
        .build();
  }

  @Override
  public String getTaskType() {
    return NG_AWS_CODE_COMMIT_TASK.name();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO connectorDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    var responseData =
        super.validateConnector(connectorDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return responseData.getConnectorValidationResult();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}

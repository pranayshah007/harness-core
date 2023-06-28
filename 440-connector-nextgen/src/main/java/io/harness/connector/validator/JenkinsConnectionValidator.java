/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static software.wings.beans.TaskType.JENKINS_CONNECTIVITY_TEST_TASK;

import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsTestConnectionTaskParams;
import io.harness.delegate.task.TaskParameters;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class JenkinsConnectionValidator extends AbstractConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    JenkinsConnectorDTO jenkinsConnector = (JenkinsConnectorDTO) connectorConfig;
    JenkinsAuthCredentialsDTO jenkinsAuthCredentialsDTO =
        jenkinsConnector.getAuth() != null ? jenkinsConnector.getAuth().getCredentials() : null;
    return JenkinsTestConnectionTaskParams.builder()
        .jenkinsConnector(jenkinsConnector)
        .encryptionDetails(
            super.getEncryptionDetail(jenkinsAuthCredentialsDTO, accountIdentifier, orgIdentifier, projectIdentifier))
        .build();
  }

  @Override
  public String getTaskType() {
    return JENKINS_CONNECTIVITY_TEST_TASK.name();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO jenkinsConnector, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    var responseData =
        super.validateConnector(jenkinsConnector, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return responseData.getConnectorValidationResult();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}

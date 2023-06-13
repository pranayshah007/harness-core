/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.exception.InvalidRequestException;

public abstract class AbstractArtifactConnectorValidator extends AbstractConnectorValidator {
  public boolean shouldExecuteOnDelegate(ConnectorConfigDTO connectorConfigDTO) {
    Boolean executeOnDelegate = Boolean.TRUE;
    if (connectorConfigDTO instanceof ManagerExecutable) {
      executeOnDelegate = ((ManagerExecutable) connectorConfigDTO).getExecuteOnDelegate();
    }
    return executeOnDelegate;
  }

  public ConnectorValidationResult validate(ConnectorConfigDTO connectorConfigDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    boolean executeOnDelegate = shouldExecuteOnDelegate(connectorConfigDTO);
    if (!executeOnDelegate) {
      return super.validateConnectorViaManager(
          connectorConfigDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    } else {
      var responseData =
          super.validateConnector(connectorConfigDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      if (connectorConfigDTO instanceof ArtifactoryConnectorDTO || connectorConfigDTO instanceof DockerConnectorDTO) {
        return responseData.getConnectorValidationResult();
      } else {
        throw new InvalidRequestException("Invalid connector type found during connection test");
      }
    }
  }
}

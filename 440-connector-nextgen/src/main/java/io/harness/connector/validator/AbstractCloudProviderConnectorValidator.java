/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ManagerExecutable;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.azure.response.AzureValidateTaskResponse;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsValidateTaskResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.gcp.response.GcpValidationTaskResponse;
import io.harness.exception.InvalidRequestException;

@OwnedBy(HarnessTeam.CI)
public abstract class AbstractCloudProviderConnectorValidator extends AbstractConnectorValidator {
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
      DelegateResponseData responseData =
          super.validateConnector(connectorConfigDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      return getValidationResult(connectorConfigDTO, responseData);
    }
  }

  private ConnectorValidationResult getValidationResult(
      ConnectorConfigDTO connectorConfigDTO, DelegateResponseData delegateResponseData) {
    if (connectorConfigDTO instanceof AwsConnectorDTO) {
      return ((AwsValidateTaskResponse) delegateResponseData).getConnectorValidationResult();
    } else if (connectorConfigDTO instanceof GcpConnectorDTO) {
      return ((GcpValidationTaskResponse) delegateResponseData).getConnectorValidationResult();
    } else if (connectorConfigDTO instanceof AzureConnectorDTO) {
      return ((AzureValidateTaskResponse) delegateResponseData).getConnectorValidationResult();
    }
    throw new InvalidRequestException("Invalid connector type found during connection test");
  }
}

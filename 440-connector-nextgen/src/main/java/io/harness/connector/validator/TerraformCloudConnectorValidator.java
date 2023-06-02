/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator;

import static software.wings.beans.TaskType.TERRAFORM_CLOUD_TASK_NG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialSpecDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudValidationTaskParams;

@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudConnectorValidator extends AbstractCloudProviderConnectorValidator {
  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    TerraformCloudConnectorDTO connectorDTO = (TerraformCloudConnectorDTO) connectorConfig;
    TerraformCloudCredentialSpecDTO terraformCloudCredentialSpecDTO = connectorDTO.getCredential().getSpec();

    return TerraformCloudValidationTaskParams.builder()
        .terraformCloudConnectorDTO(connectorDTO)
        .encryptionDetails(super.getEncryptionDetail(
            terraformCloudCredentialSpecDTO, accountIdentifier, orgIdentifier, projectIdentifier))
        .build();
  }

  @Override
  public String getTaskType() {
    return TERRAFORM_CLOUD_TASK_NG.name();
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO connectorDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return super.validate(connectorDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.terraformcloud;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorValidationResult.ConnectorValidationResultBuilder;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.terraformcloud.TerraformCloudValidationParams;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.terraformcloud.TerraformCloudApiTokenCredentials;
import io.harness.terraformcloud.TerraformCloudClient;
import io.harness.terraformcloud.TerraformCloudConfig;
import io.harness.terraformcloud.model.OrganizationData;
import io.harness.terraformcloud.model.TerraformCloudResponse;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudValidationHandler implements ConnectorValidationHandler {
  @Inject TerraformCloudConfigMapper terraformCloudConfigMapper;
  @Inject TerraformCloudClient terraformCloudClient;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    TerraformCloudValidationParams terraformCloudValidationParams =
        (TerraformCloudValidationParams) connectorValidationParams;
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        terraformCloudValidationParams.getTerraformCloudConnectorDTO();
    return validate(terraformCloudConfigMapper.mapTerraformCloudConfigWithDecryption(
        terraformCloudConnectorDTO, terraformCloudValidationParams.getEncryptedDataDetails()));
  }

  public ConnectorValidationResult validate(TerraformCloudConfig terraformCloudConfig) {
    TerraformCloudApiTokenCredentials terraformCloudApiTokenCredentials =
        (TerraformCloudApiTokenCredentials) terraformCloudConfig.getTerraformCloudCredentials();
    ConnectorValidationResultBuilder result = ConnectorValidationResult.builder();
    try {
      TerraformCloudResponse<List<OrganizationData>> organizationsResponse = terraformCloudClient.listOrganizations(
          terraformCloudApiTokenCredentials.getUrl(), terraformCloudApiTokenCredentials.getToken(), 1);
      if (isNotEmpty(organizationsResponse.getData())) {
        result.status(ConnectivityStatus.SUCCESS);
      } else {
        handleFailedValidation("Failed to get organizations");
      }
    } catch (Exception e) {
      log.error("Failed to get organizations: {}", e.getMessage());
      handleFailedValidation(e.getMessage());
    }
    return result.testedAt(System.currentTimeMillis()).build();
  }

  private void handleFailedValidation(String message) {
    throw new HintException("Check if your ConnectorDisconnectHandler credentials are correct",
        NestedExceptionUtils.hintWithExplanationException("Check api token has permissions to get organizations",
            "Failed to get organizations, Please check you Terraform cloud ConnectorDisconnectHandler configuration.",
            new InvalidRequestException(message)));
  }
}

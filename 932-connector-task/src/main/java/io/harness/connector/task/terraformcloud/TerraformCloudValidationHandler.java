/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.terraformcloud;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.terraformcloud.TerraformCloudValidationParams;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.terraformcloud.TerraformCloudConfig;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudValidationHandler implements ConnectorValidationHandler {
  @Inject TerraformCloudConfigMapper terraformCloudConfigMapper;

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
    // ToDo implementation of validation, for now always true
    return ConnectorValidationResult.builder()
        .status(ConnectivityStatus.SUCCESS)
        .testedAt(System.currentTimeMillis())
        .build();
  }
}

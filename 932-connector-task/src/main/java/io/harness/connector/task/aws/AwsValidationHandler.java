/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.aws;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsValidationParams;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class AwsValidationHandler implements ConnectorValidationHandler {
  @Inject AwsNgConfigMapper ngConfigMapper;
  @Inject private AwsClient awsClient;
  @Inject ExceptionManager exceptionManager;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    try {
      final AwsValidationParams awsValidationParams = (AwsValidationParams) connectorValidationParams;
      final AwsConnectorDTO connectorDTO = awsValidationParams.getAwsConnectorDTO();
      final List<EncryptedDataDetail> encryptedDataDetails = awsValidationParams.getEncryptedDataDetails();
      return validateInternal(connectorDTO, encryptedDataDetails);
    } catch (Exception e) {
      throw exceptionManager.processException(e, MANAGER, log);
    }
  }

  public ConnectorValidationResult validate(AwsTaskParams awsTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    final AwsConnectorDTO awsConnector = awsTaskParams.getAwsConnector();
    return validateInternal(awsConnector, encryptionDetails);
  }

  private ConnectorValidationResult validateInternal(
      AwsConnectorDTO awsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    AwsConfig awsConfig = ngConfigMapper.mapAwsConfigWithDecryption(awsConnectorDTO, encryptedDataDetails);
    return handleValidateTask(awsConfig);
  }

  private ConnectorValidationResult handleValidateTask(AwsConfig awsConfig) {
    awsClient.validateAwsAccountCredential(awsConfig);
    return ConnectorValidationResult.builder()
        .status(ConnectivityStatus.SUCCESS)
        .testedAt(System.currentTimeMillis())
        .build();
  }
}

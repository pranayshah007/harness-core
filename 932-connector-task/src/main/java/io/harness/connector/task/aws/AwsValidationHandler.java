/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.aws;

import static io.harness.aws.AwsExceptionHandler.handleAmazonClientException;
import static io.harness.aws.AwsExceptionHandler.handleAmazonServiceException;

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
import io.harness.errorhandling.NGErrorHelper;
import io.harness.security.encryption.EncryptedDataDetail;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;

@OwnedBy(HarnessTeam.CI)
public class AwsValidationHandler implements ConnectorValidationHandler {
  @Inject AwsNgConfigMapper ngConfigMapper;
  @Inject private AwsClient awsClient;
  @Inject private NGErrorHelper ngErrorHelper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final AwsValidationParams awsValidationParams = (AwsValidationParams) connectorValidationParams;
    final AwsConnectorDTO connectorDTO = awsValidationParams.getAwsConnectorDTO();
    final List<EncryptedDataDetail> encryptedDataDetails = awsValidationParams.getEncryptedDataDetails();
    return validateInternal(connectorDTO, encryptedDataDetails);
  }

  public ConnectorValidationResult validate(AwsTaskParams awsTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    final AwsConnectorDTO awsConnector = awsTaskParams.getAwsConnector();
    return validateInternal(awsConnector, encryptionDetails);
  }

  private ConnectorValidationResult validateInternal(
      AwsConnectorDTO awsConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    ConnectorValidationResult connectorValidationResult;

    try {
      AwsConfig awsConfig = ngConfigMapper.mapAwsConfigWithDecryption(awsConnectorDTO, encryptedDataDetails);
      connectorValidationResult = handleValidateTask(awsConfig);
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      connectorValidationResult = ConnectorValidationResult.builder()
                                      .status(ConnectivityStatus.FAILURE)
                                      .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage)))
                                      .errorSummary(ngErrorHelper.getErrorSummary(errorMessage))
                                      .testedAt(System.currentTimeMillis())
                                      .build();
    }
    return connectorValidationResult;
  }

  private ConnectorValidationResult handleValidateTask(AwsConfig awsConfig) {
    ConnectorValidationResult result = null;
    try {
      awsClient.validateAwsAccountCredential(awsConfig);
      result = ConnectorValidationResult.builder()
                   .status(ConnectivityStatus.SUCCESS)
                   .testedAt(System.currentTimeMillis())
                   .build();
    } catch (AmazonEC2Exception amazonEC2Exception) {
      handleAmazonServiceException(amazonEC2Exception);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return result;
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.aws.AwsExceptionHandler.handleAmazonClientException;
import static io.harness.aws.AwsExceptionHandler.handleAmazonServiceException;

import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsValidationParams;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.amazonaws.AmazonClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;

public class AwsValidationHandler implements ConnectorValidationHandler {
  @Inject SecretDecryptionService decryptionService;
  @Inject AwsNgConfigMapper ngConfigMapper;
  @Inject private AwsClient awsClient;
  @Inject private NGErrorHelper ngErrorHelper;

  private static final Regions DEFAULT_REGION = Regions.US_EAST_1;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final AwsValidationParams awsValidationParams = (AwsValidationParams) connectorValidationParams;
    final AwsConnectorDTO connectorDTO = awsValidationParams.getAwsConnectorDTO();
    final List<EncryptedDataDetail> encryptedDataDetails = awsValidationParams.getEncryptedDataDetails();
    final List<DecryptableEntity> decryptableEntityList = connectorDTO.getDecryptableEntities();

    if (decryptableEntityList != null) {
      for (DecryptableEntity entity : decryptableEntityList) {
        decryptionService.decrypt(entity, encryptedDataDetails);
      }
    }

    AwsConfig awsConfig = ngConfigMapper.mapAwsConfigWithDecryption(
        connectorDTO.getCredential(), connectorDTO.getCredential().getAwsCredentialType(), encryptedDataDetails);

    ConnectorValidationResult connectorValidationResult;
    String region = DEFAULT_REGION.getName();
    if (EmptyPredicate.isNotEmpty(connectorDTO.getCredential().getTestRegion())) {
      region = connectorDTO.getCredential().getTestRegion();
    }
    try {
      connectorValidationResult = handleValidateTask(awsConfig, region);
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

  private ConnectorValidationResult handleValidateTask(AwsConfig awsConfig, String region) {
    ConnectorValidationResult result = null;
    try {
      awsClient.validateAwsAccountCredential(awsConfig, region);
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

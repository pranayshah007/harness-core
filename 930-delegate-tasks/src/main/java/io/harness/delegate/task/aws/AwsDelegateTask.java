/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsConfig;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.connector.awsconnector.AwsValidateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private AwsClient awsClient;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private NGErrorHelper ngErrorHelper;
  @Inject private AwsS3DelegateTaskHelper awsS3DelegateTaskHelper;
  @Inject private AwsIAMDelegateTaskHelper awsIAMDelegateTaskHelper;
  @Inject private AwsCFDelegateTaskHelper awsCFDelegateTaskHelper;

  public AwsDelegateTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    final AwsTaskParams awsTaskParams = (AwsTaskParams) parameters;
    final AwsTaskType awsTaskType = awsTaskParams.getAwsTaskType();
    if (Objects.isNull(awsTaskType)) {
      throw new InvalidRequestException("Task type not provided");
    }

    final List<EncryptedDataDetail> encryptionDetails = awsTaskParams.getEncryptionDetails();
    switch (awsTaskType) {
      // TODO: we can move this to factory method using guice mapbinder later
      case VALIDATE:
        return handleValidateTask(awsTaskParams, encryptionDetails);
      case LIST_S3_BUCKETS:
        return awsS3DelegateTaskHelper.getS3Buckets(awsTaskParams);
      case LIST_IAM_ROLES:
        return awsIAMDelegateTaskHelper.getIAMRoleList(awsTaskParams);
      case CF_LIST_PARAMS:
        return awsCFDelegateTaskHelper.getCFParamsList((AwsCFTaskParamsRequest) parameters);
      default:
        throw new InvalidRequestException("Task type not identified");
    }
  }

  public DelegateResponseData handleValidateTask(
      AwsTaskParams awsTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    final AwsConnectorDTO awsConnector = awsTaskParams.getAwsConnector();
    final AwsCredentialDTO credential = awsConnector.getCredential();
    final AwsCredentialType awsCredentialType = credential.getAwsCredentialType();
    final AwsConfig awsConfig =
        awsNgConfigMapper.mapAwsConfigWithDecryption(credential, awsCredentialType, encryptionDetails);
    awsClient.validateAwsAccountCredential(awsConfig);
    ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                              .status(ConnectivityStatus.SUCCESS)
                                                              .delegateId(getDelegateId())
                                                              .testedAt(System.currentTimeMillis())
                                                              .build();
    return AwsValidateTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }
}

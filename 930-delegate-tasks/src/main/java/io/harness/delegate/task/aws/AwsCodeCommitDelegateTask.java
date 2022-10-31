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
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.awscodecommitconnector.AwsCodeCommitTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsValidateTaskResponse;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class AwsCodeCommitDelegateTask extends AbstractDelegateRunnableTask {
  private static final String CODE_COMMIT_URL_PATTERN =
      "^https://git-codecommit\\.([^/.]*)\\.amazonaws\\.com/v1/repos(?:/?|/([^/.]*))$";
  @Inject private AwsClient awsClient;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private NGErrorHelper ngErrorHelper;

  public AwsCodeCommitDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    final AwsCodeCommitTaskParams awsCodeCommitTaskParams = (AwsCodeCommitTaskParams) parameters;
    final List<EncryptedDataDetail> encryptionDetails = awsCodeCommitTaskParams.getEncryptionDetails();
    try {
      return handleValidateTask(awsCodeCommitTaskParams, encryptionDetails);

    } catch (Exception e) {
      String errorMessage = e.getMessage();
      ConnectorValidationResult connectorValidationResult =
          ConnectorValidationResult.builder()
              .status(ConnectivityStatus.FAILURE)
              .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage)))
              .errorSummary(ngErrorHelper.getErrorSummary(errorMessage))
              .testedAt(System.currentTimeMillis())
              .delegateId(getDelegateId())
              .build();
      return AwsValidateTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
    }
  }

  public DelegateResponseData handleValidateTask(
      AwsCodeCommitTaskParams awsCodeCommitTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    final AwsCodeCommitConnectorDTO awsConnector = awsCodeCommitTaskParams.getAwsConnector();
    final String url = awsConnector.getUrl();
    final String region = fetchRegion(url);
    final String repo = fetchRepo(url);
    final AwsCodeCommitAuthenticationDTO authenticationDTO = awsConnector.getAuthentication();
    final AwsConfig awsConfig = awsNgConfigMapper.mapAwsCodeCommit(authenticationDTO, encryptionDetails);
    try {
      awsClient.validateAwsCodeCommitCredential(awsConfig, region, repo);
      ConnectorValidationResult connectorValidationResult = ConnectorValidationResult.builder()
                                                                .status(ConnectivityStatus.SUCCESS)
                                                                .delegateId(getDelegateId())
                                                                .testedAt(System.currentTimeMillis())
                                                                .build();
      return AwsValidateTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    throw new InvalidRequestException("Unsuccessful validation");
  }

  private String fetchRegion(String url) {
    Pattern r = Pattern.compile(CODE_COMMIT_URL_PATTERN);
    Matcher m = r.matcher(url);

    if (m.find()) {
      return m.group(1);
    } else {
      throw new InvalidRequestException("Url format is not matching regex pattern");
    }
  }

  private String fetchRepo(String url) {
    Pattern r = Pattern.compile(CODE_COMMIT_URL_PATTERN);
    Matcher m = r.matcher(url);

    if (m.find()) {
      if (m.groupCount() == 2) {
        return m.group(2);
      } else {
        return null;
      }
    } else {
      throw new InvalidRequestException("Url format is not matching regex pattern");
    }
  }
}

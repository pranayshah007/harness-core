/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.buckets.resources.s3;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.common.resources.AwsResourceServiceHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsS3BucketResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.exception.BucketServerException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
@OwnedBy(CDP)
public class S3ResourceServiceImpl implements S3ResourceService {
  private final AwsResourceServiceHelper serviceHelper;

  @Inject
  public S3ResourceServiceImpl(AwsResourceServiceHelper serviceHelper) {
    this.serviceHelper = serviceHelper;
  }

  @Override
  public Map<String, String> getBuckets(
      IdentifierRef awsConnectorRef, String region, String orgIdentifier, String projectIdentifier) {

    if(EmptyPredicate.isEmpty(region)){
      region = AWS_DEFAULT_REGION;
    }

    AwsConnectorDTO connector = serviceHelper.getAwsConnector(awsConnectorRef);
    BaseNGAccess baseNGAccess =
        serviceHelper.getBaseNGAccess(awsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = serviceHelper.getAwsEncryptionDetails(connector, baseNGAccess);
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsTaskType(AwsTaskType.LIST_S3_BUCKETS)
                                      .awsConnector(connector)
                                      .encryptionDetails(encryptionDetails)
                                      .region(region)
                                      .build();

    AwsS3BucketResponse awsS3BucketResponse =
        executeSyncTask(awsTaskParams, baseNGAccess, "AWS S3 Get Buckets task failure due to error");
    return awsS3BucketResponse.getBuckets();
  }

  private AwsS3BucketResponse executeSyncTask(
      AwsTaskParams awsTaskParams, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData =
        serviceHelper.getResponseData(ngAccess, awsTaskParams, TaskType.NG_AWS_TASK.name());
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private AwsS3BucketResponse getTaskExecutionResponse(DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new BucketServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    }

    AwsS3BucketResponse awsS3BucketResponse = (AwsS3BucketResponse) responseData;
    if (awsS3BucketResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new BucketServerException(ifFailedMessage);
    }

    return awsS3BucketResponse;
  }
}

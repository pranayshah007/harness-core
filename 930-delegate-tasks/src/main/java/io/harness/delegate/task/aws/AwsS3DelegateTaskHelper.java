/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.streaming.dtos.PutObjectResultResponse;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsPutAuditBatchToBucketTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsPutAuditBatchToBucketTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsS3BucketResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.S3BuildResponse;
import io.harness.delegate.beans.connector.awsconnector.S3BuildsResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.AwsApiHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsS3DelegateTaskHelper {
  private final SecretDecryptionService secretDecryptionService;
  private final AwsApiHelperService awsApiHelperService;
  @Inject private final AwsNgConfigMapper awsNgConfigMapper;

  public DelegateResponseData getS3Buckets(AwsTaskParams awsTaskParams) {
    decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsTaskParams);
    List<String> buckets = awsApiHelperService.listS3Buckets(awsInternalConfig, awsTaskParams.getRegion());
    return AwsS3BucketResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .buckets(buckets != null ? buckets.stream().collect(Collectors.toMap(s -> s, s -> s)) : Collections.emptyMap())
        .build();
  }

  private AwsInternalConfig getAwsInternalConfig(AwsTaskParams awsTaskParams) {
    AwsConnectorDTO awsConnectorDTO = awsTaskParams.getAwsConnector();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(awsTaskParams.getRegion());
    return awsInternalConfig;
  }

  public void decryptRequestDTOs(AwsConnectorDTO awsConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
      secretDecryptionService.decrypt(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), encryptionDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), encryptionDetails);
    }
  }

  public DelegateResponseData getBuilds(AwsTaskParams awsTaskParams) {
    decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());

    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsTaskParams);

    List<BuildDetails> builds = awsApiHelperService.listBuilds(awsInternalConfig, awsTaskParams.getRegion(),
        awsTaskParams.getBucketName(), awsTaskParams.getFilePathRegex(), awsTaskParams.isShouldFetchObjectMetadata());

    return S3BuildsResponse.builder()
        .commandExecutionStatus(SUCCESS)
        .builds(builds != null ? builds : new ArrayList<>())
        .build();
  }

  public DelegateResponseData getBuild(AwsTaskParams awsTaskParams) {
    decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());

    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsTaskParams);

    BuildDetails buildDetails = awsApiHelperService.getBuild(
        awsInternalConfig, awsTaskParams.getRegion(), awsTaskParams.getBucketName(), awsTaskParams.getFilePath());

    String filePath = "";
    if (buildDetails == null) {
      S3BuildResponse.builder().commandExecutionStatus(FAILURE).build();
    } else {
      filePath = awsTaskParams.getFilePath();
    }

    return S3BuildResponse.builder().commandExecutionStatus(SUCCESS).filePath(filePath).build();
  }

  public DelegateResponseData getLastSuccessfulBuild(AwsTaskParams awsTaskParams) {
    decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());

    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsTaskParams);

    List<BuildDetails> builds = awsApiHelperService.listBuilds(awsInternalConfig, awsTaskParams.getRegion(),
        awsTaskParams.getBucketName(), awsTaskParams.getFilePathRegex(), true);

    if (builds.isEmpty()) {
      return S3BuildResponse.builder().commandExecutionStatus(FAILURE).build();
    }

    BuildDetails buildDetails = builds.get(builds.size() - 1);
    String filePath;
    if (buildDetails == new BuildDetails()) {
      return S3BuildResponse.builder().commandExecutionStatus(FAILURE).build();
    } else {
      filePath = buildDetails.getArtifactPath();
    }

    return S3BuildResponse.builder().commandExecutionStatus(SUCCESS).filePath(filePath).build();
  }

  public DelegateResponseData putAuditBatchToBucket(AwsPutAuditBatchToBucketTaskParamsRequest awsTaskParams) {
    decryptRequestDTOs(awsTaskParams.getAwsConnector(), awsTaskParams.getEncryptionDetails());
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsTaskParams);
    try {
      PutObjectResultResponse putObjectResultResponse = awsApiHelperService.putAuditBatchToBucket(
          awsInternalConfig, awsTaskParams.getRegion(), awsTaskParams.getBucketName(), awsTaskParams.getAuditBatch());

      return AwsPutAuditBatchToBucketTaskResponse.builder()
          .commandExecutionStatus(SUCCESS)
          .putObjectResultResponse(putObjectResultResponse)
          .build();
    } catch (Exception ex) {
      log.error("Exception while writing to S3 bucket: ", ex);
      return AwsPutAuditBatchToBucketTaskResponse.builder()
          .commandExecutionStatus(FAILURE)
          .errorMessage(
              ex.getMessage() != null ? ex.getMessage() : "Failed to write to S3 bucket. Unknown error occurred.")
          .build();
    }
  }
}

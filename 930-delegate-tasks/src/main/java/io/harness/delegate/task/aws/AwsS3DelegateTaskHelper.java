/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsS3BucketResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.impl.AwsApiHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
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
}

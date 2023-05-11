/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.awsmapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.awsconnector.AwsAccessKeyCredential;
import io.harness.connector.entities.embedded.awsconnector.AwsConfig;
import io.harness.connector.entities.embedded.awsconnector.AwsConfig.AwsConfigBuilder;
import io.harness.connector.entities.embedded.awsconnector.AwsEqualJitterBackoffStrategy;
import io.harness.connector.entities.embedded.awsconnector.AwsFixedDelayBackoffStrategy;
import io.harness.connector.entities.embedded.awsconnector.AwsFullJitterBackoffStrategy;
import io.harness.connector.entities.embedded.awsconnector.AwsSdkClientBackoffStrategy;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsEqualJitterBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsFixedDelayBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsFullJitterBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyType;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.DX)
@Singleton
public class AwsDTOToEntity implements ConnectorDTOToEntityMapper<AwsConnectorDTO, AwsConfig> {
  @Override
  public AwsConfig toConnectorEntity(AwsConnectorDTO connectorDTO) {
    final AwsCredentialDTO credential = connectorDTO.getCredential();
    final AwsCredentialType credentialType = credential.getAwsCredentialType();
    AwsConfigBuilder awsConfigBuilder;
    switch (credentialType) {
      case INHERIT_FROM_DELEGATE:
        awsConfigBuilder = buildInheritFromDelegate(credential);
        break;
      case MANUAL_CREDENTIALS:
        awsConfigBuilder = buildManualCredential(credential);
        break;
      case IRSA:
        awsConfigBuilder = buildIRSA(credential);
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }

    AwsSdkClientBackoffStrategyDTO awsSdkClientBackoffStrategyDTO =
        connectorDTO.getAwsSdkClientBackOffStrategyOverride();
    if (awsSdkClientBackoffStrategyDTO != null) {
      awsConfigBuilder.awsSdkClientBackoffStrategy(buildAwsSdkClientBackoffStrategy(awsSdkClientBackoffStrategyDTO));
    }

    return awsConfigBuilder.crossAccountAccess(credential.getCrossAccountAccess())
        .testRegion(credential.getTestRegion())
        .build();
  }

  private AwsConfigBuilder buildInheritFromDelegate(AwsCredentialDTO connector) {
    return AwsConfig.builder().credentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).credential(null);
  }

  private AwsConfigBuilder buildManualCredential(AwsCredentialDTO connector) {
    final AwsManualConfigSpecDTO config = (AwsManualConfigSpecDTO) connector.getConfig();
    final String secretKeyRef = SecretRefHelper.getSecretConfigString(config.getSecretKeyRef());
    final String accessKeyRef = SecretRefHelper.getSecretConfigString(config.getAccessKeyRef());
    AwsAccessKeyCredential accessKeyCredential = AwsAccessKeyCredential.builder()
                                                     .accessKey(config.getAccessKey())
                                                     .accessKeyRef(accessKeyRef)
                                                     .secretKeyRef(secretKeyRef)
                                                     .build();
    return AwsConfig.builder().credentialType(AwsCredentialType.MANUAL_CREDENTIALS).credential(accessKeyCredential);
  }

  private AwsConfigBuilder buildIRSA(AwsCredentialDTO connector) {
    return AwsConfig.builder().credentialType(AwsCredentialType.IRSA).credential(null);
  }

  private AwsSdkClientBackoffStrategy buildAwsSdkClientBackoffStrategy(
      AwsSdkClientBackoffStrategyDTO awsSdkClientBackoffStrategyDTO) {
    AwsSdkClientBackoffStrategyType awsSdkClientBackoffStrategyType =
        awsSdkClientBackoffStrategyDTO.getAwsSdkClientBackoffStrategyType();
    AwsSdkClientBackoffStrategy awsSdkClientBackoffStrategy;
    switch (awsSdkClientBackoffStrategyType) {
      case FIXED_DELAY_BACKOFF_STRATEGY:
        AwsFixedDelayBackoffStrategySpecDTO awsFixedDelayBackoffStrategySpecDTO =
            (AwsFixedDelayBackoffStrategySpecDTO) awsSdkClientBackoffStrategyDTO.getBackoffStrategyConfig();
        AwsFixedDelayBackoffStrategy awsFixedDelayBackoffStrategy =
            AwsFixedDelayBackoffStrategy.builder()
                .fixedBackoff(awsFixedDelayBackoffStrategySpecDTO.getFixedBackoff())
                .retryCount(awsFixedDelayBackoffStrategySpecDTO.getRetryCount())
                .build();
        awsSdkClientBackoffStrategy = awsFixedDelayBackoffStrategy;
        break;
      case EQUAL_JITTER_BACKOFF_STRATEGY:
        AwsEqualJitterBackoffStrategySpecDTO awsEqualJitterBackoffStrategySpecDTO =
            (AwsEqualJitterBackoffStrategySpecDTO) awsSdkClientBackoffStrategyDTO.getBackoffStrategyConfig();
        AwsEqualJitterBackoffStrategy awsEqualJitterBackoffStrategy =
            AwsEqualJitterBackoffStrategy.builder()
                .baseDelay(awsEqualJitterBackoffStrategySpecDTO.getBaseDelay())
                .maxBackoffTime(awsEqualJitterBackoffStrategySpecDTO.getMaxBackoffTime())
                .retryCount(awsEqualJitterBackoffStrategySpecDTO.getRetryCount())
                .build();
        awsSdkClientBackoffStrategy = awsEqualJitterBackoffStrategy;
        break;
      case FULL_JITTER_BACKOFF_STRATEGY:
        AwsFullJitterBackoffStrategySpecDTO awsFullJitterBackoffStrategySpecDTO =
            (AwsFullJitterBackoffStrategySpecDTO) awsSdkClientBackoffStrategyDTO.getBackoffStrategyConfig();
        AwsFullJitterBackoffStrategy awsFullJitterBackoffStrategy =
            AwsFullJitterBackoffStrategy.builder()
                .baseDelay(awsFullJitterBackoffStrategySpecDTO.getBaseDelay())
                .maxBackoffTime(awsFullJitterBackoffStrategySpecDTO.getMaxBackoffTime())
                .retryCount(awsFullJitterBackoffStrategySpecDTO.getRetryCount())
                .build();
        awsSdkClientBackoffStrategy = awsFullJitterBackoffStrategy;
        break;
      default:
        throw new InvalidRequestException("Invalid Aws Backoff Strategy type.");
    }
    return awsSdkClientBackoffStrategy;
  }
}

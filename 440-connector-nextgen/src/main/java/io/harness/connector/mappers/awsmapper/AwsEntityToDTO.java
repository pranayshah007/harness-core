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
import io.harness.connector.entities.embedded.awsconnector.AwsEqualJitterBackoffStrategy;
import io.harness.connector.entities.embedded.awsconnector.AwsFixedDelayBackoffStrategy;
import io.harness.connector.entities.embedded.awsconnector.AwsFullJitterBackoffStrategy;
import io.harness.connector.entities.embedded.awsconnector.AwsIRSACredential;
import io.harness.connector.entities.embedded.awsconnector.AwsIamCredential;
import io.harness.connector.entities.embedded.awsconnector.AwsSdkClientBackoffStrategy;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO.AwsCredentialDTOBuilder;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsEqualJitterBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsFixedDelayBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsFullJitterBackoffStrategySpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyType;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.DX)
@Singleton
public class AwsEntityToDTO implements ConnectorEntityToDTOMapper<AwsConnectorDTO, AwsConfig> {
  @Override
  public AwsConnectorDTO createConnectorDTO(AwsConfig connector) {
    final AwsCredentialType credentialType = connector.getCredentialType();
    AwsCredentialDTOBuilder awsCredentialDTOBuilder;
    switch (credentialType) {
      case INHERIT_FROM_DELEGATE:
        awsCredentialDTOBuilder = buildInheritFromDelegate((AwsIamCredential) connector.getCredential());
        break;
      case MANUAL_CREDENTIALS:
        awsCredentialDTOBuilder = buildManualCredential((AwsAccessKeyCredential) connector.getCredential());
        break;
      case IRSA:
        awsCredentialDTOBuilder = buildIRSA((AwsIRSACredential) connector.getCredential());
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
    AwsSdkClientBackoffStrategy awsSdkClientBackoffStrategy = connector.getAwsSdkClientBackoffStrategy();
    AwsSdkClientBackoffStrategyDTO awsSdkClientBackoffStrategyDTO = null;
    if (awsSdkClientBackoffStrategy != null) {
      awsSdkClientBackoffStrategyDTO = buildFixedDelayBackoffStrategyDTO(awsSdkClientBackoffStrategy);
    }

    return AwsConnectorDTO.builder()
        .credential(awsCredentialDTOBuilder.crossAccountAccess(connector.getCrossAccountAccess())
                        .testRegion(connector.getTestRegion())
                        .build())
        .awsSdkClientBackOffStrategyOverride(awsSdkClientBackoffStrategyDTO)
        .delegateSelectors(connector.getDelegateSelectors())
        .build();
  }

  private AwsCredentialDTOBuilder buildManualCredential(AwsAccessKeyCredential credential) {
    final SecretRefData secretRef = SecretRefHelper.createSecretRef(credential.getSecretKeyRef());
    final SecretRefData accessKeyRef = SecretRefHelper.createSecretRef(credential.getAccessKeyRef());
    final AwsManualConfigSpecDTO awsManualConfigSpecDTO = AwsManualConfigSpecDTO.builder()
                                                              .accessKey(credential.getAccessKey())
                                                              .secretKeyRef(secretRef)
                                                              .accessKeyRef(accessKeyRef)
                                                              .build();
    return AwsCredentialDTO.builder()
        .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
        .config(awsManualConfigSpecDTO);
  }

  private AwsCredentialDTOBuilder buildInheritFromDelegate(AwsIamCredential credential) {
    return AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).config(null);
  }

  private AwsCredentialDTOBuilder buildIRSA(AwsIRSACredential credential) {
    return AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.IRSA).config(null);
  }

  private AwsSdkClientBackoffStrategyDTO buildFixedDelayBackoffStrategyDTO(
      AwsSdkClientBackoffStrategy awsSdkClientBackoffStrategy) {
    AwsSdkClientBackoffStrategyType awsSdkClientBackoffStrategyType =
        awsSdkClientBackoffStrategy.getBackoffStrategyType();
    switch (awsSdkClientBackoffStrategyType) {
      case FIXED_DELAY_BACKOFF_STRATEGY:
        AwsFixedDelayBackoffStrategy awsFixedDelayBackoffStrategy =
            (AwsFixedDelayBackoffStrategy) awsSdkClientBackoffStrategy;
        AwsFixedDelayBackoffStrategySpecDTO awsFixedDelayBackoffStrategySpecDTO =
            AwsFixedDelayBackoffStrategySpecDTO.builder()
                .fixedBackoff(awsFixedDelayBackoffStrategy.getFixedBackoff())
                .retryCount(awsFixedDelayBackoffStrategy.getRetryCount())
                .build();
        return AwsSdkClientBackoffStrategyDTO.builder()
            .awsSdkClientBackoffStrategyType(AwsSdkClientBackoffStrategyType.FIXED_DELAY_BACKOFF_STRATEGY)
            .backoffStrategyConfig(awsFixedDelayBackoffStrategySpecDTO)
            .build();
      case EQUAL_JITTER_BACKOFF_STRATEGY:
        AwsEqualJitterBackoffStrategy awsEqualJitterBackoffStrategy =
            (AwsEqualJitterBackoffStrategy) awsSdkClientBackoffStrategy;
        AwsEqualJitterBackoffStrategySpecDTO awsEqualJitterBackoffStrategySpecDTO =
            AwsEqualJitterBackoffStrategySpecDTO.builder()
                .baseDelay(awsEqualJitterBackoffStrategy.getBaseDelay())
                .maxBackoffTime(awsEqualJitterBackoffStrategy.getMaxBackoffTime())
                .retryCount(awsEqualJitterBackoffStrategy.getRetryCount())
                .build();
        return AwsSdkClientBackoffStrategyDTO.builder()
            .awsSdkClientBackoffStrategyType(AwsSdkClientBackoffStrategyType.EQUAL_JITTER_BACKOFF_STRATEGY)
            .backoffStrategyConfig(awsEqualJitterBackoffStrategySpecDTO)
            .build();
      case FULL_JITTER_BACKOFF_STRATEGY:
        AwsFullJitterBackoffStrategy awsFullJitterBackoffStrategy =
            (AwsFullJitterBackoffStrategy) awsSdkClientBackoffStrategy;
        AwsFullJitterBackoffStrategySpecDTO awsFullJitterBackoffStrategySpecDTO =
            AwsFullJitterBackoffStrategySpecDTO.builder()
                .baseDelay(awsFullJitterBackoffStrategy.getBaseDelay())
                .maxBackoffTime(awsFullJitterBackoffStrategy.getMaxBackoffTime())
                .retryCount(awsFullJitterBackoffStrategy.getRetryCount())
                .build();
        return AwsSdkClientBackoffStrategyDTO.builder()
            .awsSdkClientBackoffStrategyType(AwsSdkClientBackoffStrategyType.FULL_JITTER_BACKOFF_STRATEGY)
            .backoffStrategyConfig(awsFullJitterBackoffStrategySpecDTO)
            .build();
      default:
        throw new InvalidRequestException("Invalid AWS Backoff Strategy.");
    }
  }
}

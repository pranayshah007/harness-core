/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.v2;

import static io.harness.eraro.ErrorCode.AWS_APPLICATION_AUTO_SCALING;
import static io.harness.eraro.ErrorCode.AWS_STS_ERROR;
import static io.harness.exception.WingsException.USER;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_BACKOFF_MAX_ERROR_RETRIES;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static software.amazon.awssdk.core.retry.backoff.BackoffStrategy.defaultStrategy;
import static software.amazon.awssdk.core.retry.backoff.BackoffStrategy.defaultThrottlingStrategy;
import static software.amazon.awssdk.core.retry.conditions.RetryCondition.defaultRetryCondition;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsSdkClientBackoffStrategyOverride;
import io.harness.aws.AwsSdkClientBackoffStrategyOverrideType;
import io.harness.aws.AwsSdkClientEqualJitterBackoffStrategy;
import io.harness.aws.AwsSdkClientFixedDelayBackoffStrategy;
import io.harness.aws.AwsSdkClientFullJitterBackoffStrategy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.AmazonClientSDKDefaultBackoffStrategy;

import com.google.inject.Singleton;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.FixedDelayBackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.applicationautoscaling.model.ApplicationAutoScalingException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.StsException;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public abstract class AwsClientHelper {
  public abstract SdkClient getClient(AwsInternalConfig awsConfig, String region);

  public abstract String client();

  public abstract void handleClientServiceException(AwsServiceException awsServiceException);

  public void logCall(String client, String method) {
    log.info("AWS Call: client: {}, method: {}", client, method);
  }

  public void logError(String client, String method, String errorMessage) {
    log.error("AWS Call: client: {}, method: {}, error: {}", client, method, errorMessage);
  }

  public void handleException(Exception exception) {
    if (exception instanceof AwsServiceException) {
      AwsServiceException awsServiceException = (AwsServiceException) exception;
      handleServiceException(awsServiceException);
    } else if (exception instanceof SdkClientException) {
      throw new InvalidRequestException(exception.getMessage(), AWS_STS_ERROR, USER);
    }
    throw new InvalidRequestException(exception.getMessage(), exception, USER);
  }
  public AwsCredentialsProvider getAwsCredentialsProvider(AwsInternalConfig awsConfig) {
    AwsCredentialsProvider credentialsProvider;
    if (awsConfig.isUseEc2IamCredentials()) {
      log.info("Instantiating EC2ContainerCredentialsProviderWrapper");
      credentialsProvider = getIamRoleAwsCredentialsProvider();
    } else if (awsConfig.isUseIRSA()) {
      credentialsProvider = getIrsaAwsCredentialsProvider(awsConfig);
    } else {
      credentialsProvider = getStaticAwsCredentialsProvider(awsConfig);
    }
    if (awsConfig.isAssumeCrossAccountRole()) {
      return getStsAssumeRoleAwsCredentialsProvider(awsConfig, credentialsProvider);
    }
    return credentialsProvider;
  }

  public void handleServiceException(AwsServiceException awsServiceException) {
    if (awsServiceException instanceof ApplicationAutoScalingException) {
      throw new InvalidRequestException(awsServiceException.getMessage(), AWS_APPLICATION_AUTO_SCALING, USER);
    }
    if (awsServiceException instanceof StsException) {
      throw new InvalidRequestException(awsServiceException.getMessage(), AWS_STS_ERROR, USER);
    }
    handleClientServiceException(awsServiceException);
    throw new InvalidRequestException(awsServiceException.getMessage(), awsServiceException, USER);
  }

  public ClientOverrideConfiguration getClientOverrideConfiguration(AwsInternalConfig awsConfig) {
    AmazonClientSDKDefaultBackoffStrategy defaultBackoffStrategy = awsConfig.getAmazonClientSDKDefaultBackoffStrategy();
    RetryPolicy retryPolicy;
    if (defaultBackoffStrategy != null) {
      retryPolicy =
          RetryPolicy.builder()
              .retryCondition(defaultRetryCondition())
              .numRetries(defaultBackoffStrategy.getMaxErrorRetry())
              .backoffStrategy(FullJitterBackoffStrategy.builder()
                                   .baseDelay(Duration.ofMillis(defaultBackoffStrategy.getBaseDelayInMs()))
                                   .maxBackoffTime(Duration.ofMillis(defaultBackoffStrategy.getMaxBackoffInMs()))
                                   .build())
              .throttlingBackoffStrategy(
                  EqualJitterBackoffStrategy.builder()
                      .baseDelay(Duration.ofMillis(defaultBackoffStrategy.getThrottledBaseDelayInMs()))
                      .maxBackoffTime(Duration.ofMillis(defaultBackoffStrategy.getMaxBackoffInMs()))
                      .build())
              .build();
    } else {
      retryPolicy = RetryPolicy.builder()
                        .retryCondition(defaultRetryCondition())
                        .numRetries(DEFAULT_BACKOFF_MAX_ERROR_RETRIES)
                        .backoffStrategy(defaultStrategy())
                        .throttlingBackoffStrategy(defaultThrottlingStrategy())
                        .build();
    }
    return ClientOverrideConfiguration.builder().retryPolicy(retryPolicy).build();

    // todo: good review needed
  }

  public ClientOverrideConfiguration getClientOverrideFromBackoffOverride(AwsInternalConfig awsConfig) {
    AwsSdkClientBackoffStrategyOverride awsSdkClientBackoffStrategyOverride =
        awsConfig.getAwsSdkClientBackoffStrategyOverride();
    RetryPolicy retryPolicy;
    if (awsSdkClientBackoffStrategyOverride != null) {
      retryPolicy = RetryPolicy.builder()
                        .retryCondition(defaultRetryCondition())
                        .numRetries(awsSdkClientBackoffStrategyOverride.getRetryCount())
                        .backoffStrategy(getBackoffStrategy(awsConfig))
                        .throttlingBackoffStrategy(getBackoffStrategy(awsConfig))
                        .build();
    } else {
      retryPolicy = RetryPolicy.builder()
                        .retryCondition(defaultRetryCondition())
                        .numRetries(DEFAULT_BACKOFF_MAX_ERROR_RETRIES)
                        .backoffStrategy(defaultStrategy())
                        .throttlingBackoffStrategy(defaultThrottlingStrategy())
                        .build();
    }
    return ClientOverrideConfiguration.builder().retryPolicy(retryPolicy).build();
  }

  private BackoffStrategy getBackoffStrategy(AwsInternalConfig awsInternalConfig) {
    AwsSdkClientBackoffStrategyOverride awsSdkClientBackoffStrategyOverride =
        awsInternalConfig.getAwsSdkClientBackoffStrategyOverride();
    if (awsSdkClientBackoffStrategyOverride.getAwsBackoffStrategyOverrideType()
        == AwsSdkClientBackoffStrategyOverrideType.FIXED_DELAY_BACKOFF_STRATEGY) {
      AwsSdkClientFixedDelayBackoffStrategy awsSdkClientFixedDelayBackoffStrategy =
          (AwsSdkClientFixedDelayBackoffStrategy) awsSdkClientBackoffStrategyOverride;
      return FixedDelayBackoffStrategy.create(
          Duration.ofMillis(awsSdkClientFixedDelayBackoffStrategy.getFixedBackoff()));
    } else if (awsSdkClientBackoffStrategyOverride.getAwsBackoffStrategyOverrideType()
        == AwsSdkClientBackoffStrategyOverrideType.EQUAL_JITTER_BACKOFF_STRATEGY) {
      AwsSdkClientEqualJitterBackoffStrategy awsSdkClientEqualJitterBackoffStrategy =
          (AwsSdkClientEqualJitterBackoffStrategy) awsSdkClientBackoffStrategyOverride;
      return EqualJitterBackoffStrategy.builder()
          .baseDelay(Duration.ofMillis(awsSdkClientEqualJitterBackoffStrategy.getBaseDelay()))
          .maxBackoffTime(Duration.ofMillis(awsSdkClientEqualJitterBackoffStrategy.getMaxBackoffTime()))
          .build();
    } else if (awsSdkClientBackoffStrategyOverride.getAwsBackoffStrategyOverrideType()
        == AwsSdkClientBackoffStrategyOverrideType.FULL_JITTER_BACKOFF_STRATEGY) {
      AwsSdkClientFullJitterBackoffStrategy awsSdkClientFullJitterBackoffStrategy =
          (AwsSdkClientFullJitterBackoffStrategy) awsSdkClientBackoffStrategyOverride;
      return FullJitterBackoffStrategy.builder()
          .baseDelay(Duration.ofMillis(awsSdkClientFullJitterBackoffStrategy.getBaseDelay()))
          .maxBackoffTime(Duration.ofMillis(awsSdkClientFullJitterBackoffStrategy.getMaxBackoffTime()))
          .build();
    }
    throw new InvalidRequestException("Invalid AWS backoff Strategy");
  }

  private AwsCredentialsProvider getIamRoleAwsCredentialsProvider() {
    try {
      if (System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") != null
          || System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI") != null) {
        return ContainerCredentialsProvider.builder().build();
      } else {
        return InstanceProfileCredentialsProvider.create();
      }
    } catch (SecurityException var2) {
      if (log.isDebugEnabled()) {
        log.debug("Security manager did not allow access to the ECS credentials environment variable"
            + " AWS_CONTAINER_CREDENTIALS_RELATIVE_URI or the container full URI environment variable"
            + " AWS_CONTAINER_CREDENTIALS_FULL_URI. Please provide access to this environment variable "
            + "if you want to load credentials from ECS Container.");
      }
      return InstanceProfileCredentialsProvider.create();
    }
  }

  private AwsCredentialsProvider getIrsaAwsCredentialsProvider(AwsInternalConfig awsConfig) {
    WebIdentityTokenFileCredentialsProvider.Builder providerBuilder = WebIdentityTokenFileCredentialsProvider.builder();
    providerBuilder.roleSessionName(awsConfig.getAccountId() + UUIDGenerator.generateUuid());
    return providerBuilder.build();
  }

  private AwsCredentialsProvider getStaticAwsCredentialsProvider(AwsInternalConfig awsConfig) {
    AwsBasicCredentials awsBasicCredentials =
        AwsBasicCredentials.create(new String(awsConfig.getAccessKey()), new String(awsConfig.getSecretKey()));
    return StaticCredentialsProvider.create(awsBasicCredentials);
  }

  private AwsCredentialsProvider getStsAssumeRoleAwsCredentialsProvider(
      AwsInternalConfig awsConfig, AwsCredentialsProvider primaryCredentialProvider) {
    AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                                              .roleArn(awsConfig.getCrossAccountAttributes().getCrossAccountRoleArn())
                                              .roleSessionName(UUID.randomUUID().toString())
                                              .externalId(awsConfig.getCrossAccountAttributes().getExternalId())
                                              .build();

    StsClient stsClient = StsClient.builder()
                              .credentialsProvider(primaryCredentialProvider)
                              .region(isNotBlank(awsConfig.getDefaultRegion()) ? Region.of(awsConfig.getDefaultRegion())
                                                                               : Region.of(AWS_DEFAULT_REGION))
                              .build();

    return StsAssumeRoleCredentialsProvider.builder().stsClient(stsClient).refreshRequest(assumeRoleRequest).build();
    // todo: close sts client
  }
}

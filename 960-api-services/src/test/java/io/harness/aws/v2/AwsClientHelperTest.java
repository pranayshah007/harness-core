/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.v2;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.core.retry.conditions.RetryCondition.defaultRetryCondition;

import io.harness.aws.AwsSdkClientBackoffStrategyOverride;
import io.harness.aws.AwsSdkClientBackoffStrategyOverrideType;
import io.harness.aws.AwsSdkClientEqualJitterBackoffStrategy;
import io.harness.aws.AwsSdkClientFixedDelayBackoffStrategy;
import io.harness.aws.AwsSdkClientFullJitterBackoffStrategy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.EcsV2ClientImpl;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.FixedDelayBackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;

public class AwsClientHelperTest {
  @InjectMocks EcsV2ClientImpl ecsV2ClientImpl;
  final int RETRY_COUNT = 3;
  final long FIXED_BACKOFF = 100;
  final long BASE_DELAY = 100;
  final long MAX_BACKOFF_TIME = 100;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getClientOverrideFromBackoffOverrideFixedDelayTest() {
    AwsSdkClientFixedDelayBackoffStrategy awsSdkClientFixedDelayBackoffStrategy =
        AwsSdkClientFixedDelayBackoffStrategy.builder().fixedBackoff(FIXED_BACKOFF).retryCount(RETRY_COUNT).build();
    AwsInternalConfig awsConfig =
        AwsInternalConfig.builder().awsSdkClientBackoffStrategyOverride(awsSdkClientFixedDelayBackoffStrategy).build();
    ClientOverrideConfiguration clientOverrideConfiguration =
        ecsV2ClientImpl.getClientOverrideFromBackoffOverride(awsConfig);
    RetryPolicy retryPolicy = RetryPolicy.builder()
                                  .retryCondition(defaultRetryCondition())
                                  .numRetries(awsSdkClientFixedDelayBackoffStrategy.getRetryCount())
                                  .backoffStrategy(getBackoffStrategy(awsConfig))
                                  .throttlingBackoffStrategy(getBackoffStrategy(awsConfig))
                                  .build();
    assertThat(clientOverrideConfiguration.retryPolicy().get()).isEqualTo(retryPolicy);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getClientOverrideFromBackoffOverrideEqualJitterTest() {
    AwsSdkClientEqualJitterBackoffStrategy awsSdkClientEqualJitterBackoffStrategy =
        AwsSdkClientEqualJitterBackoffStrategy.builder()
            .baseDelay(BASE_DELAY)
            .maxBackoffTime(MAX_BACKOFF_TIME)
            .retryCount(RETRY_COUNT)
            .build();
    AwsInternalConfig awsConfig =
        AwsInternalConfig.builder().awsSdkClientBackoffStrategyOverride(awsSdkClientEqualJitterBackoffStrategy).build();
    ClientOverrideConfiguration clientOverrideConfiguration =
        ecsV2ClientImpl.getClientOverrideFromBackoffOverride(awsConfig);
    RetryPolicy retryPolicy = RetryPolicy.builder()
                                  .retryCondition(defaultRetryCondition())
                                  .numRetries(awsSdkClientEqualJitterBackoffStrategy.getRetryCount())
                                  .backoffStrategy(getBackoffStrategy(awsConfig))
                                  .throttlingBackoffStrategy(getBackoffStrategy(awsConfig))
                                  .build();
    assertThat(clientOverrideConfiguration.retryPolicy().get()).isEqualTo(retryPolicy);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getClientOverrideFromBackoffOverrideFullJitterTest() {
    AwsSdkClientFullJitterBackoffStrategy awsSdkClientFullJitterBackoffStrategy =
        AwsSdkClientFullJitterBackoffStrategy.builder()
            .baseDelay(BASE_DELAY)
            .maxBackoffTime(MAX_BACKOFF_TIME)
            .retryCount(RETRY_COUNT)
            .build();
    AwsInternalConfig awsConfig =
        AwsInternalConfig.builder().awsSdkClientBackoffStrategyOverride(awsSdkClientFullJitterBackoffStrategy).build();
    ClientOverrideConfiguration clientOverrideConfiguration =
        ecsV2ClientImpl.getClientOverrideFromBackoffOverride(awsConfig);
    RetryPolicy retryPolicy = RetryPolicy.builder()
                                  .retryCondition(defaultRetryCondition())
                                  .numRetries(awsSdkClientFullJitterBackoffStrategy.getRetryCount())
                                  .backoffStrategy(getBackoffStrategy(awsConfig))
                                  .throttlingBackoffStrategy(getBackoffStrategy(awsConfig))
                                  .build();
    assertThat(clientOverrideConfiguration.retryPolicy().get()).isEqualTo(retryPolicy);
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
}

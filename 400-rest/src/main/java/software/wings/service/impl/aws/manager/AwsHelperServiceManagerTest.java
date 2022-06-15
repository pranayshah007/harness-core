/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.manager;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import software.wings.beans.AmazonClientSDKDefaultBackoffStrategy;
import software.wings.beans.AwsConfig;

import static io.harness.rule.OwnerRule.IVAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static software.wings.service.impl.aws.model.AwsConstants.BASE_DELAY_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_BACKOFF_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_ERROR_RETRY_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE;


import software.wings.sm.ExecutionContextImpl;



public class AwsHelperServiceManagerTest {
    @Mock private ExecutionContextImpl executionContext;

    @Test
    @Owner(developers = IVAN)
    @Category(UnitTests.class)
    public void testSetAmazonClientSDKDefaultBackoffStrategyIfExists() {
        when(executionContext.renderExpression(BASE_DELAY_ACCOUNT_VARIABLE)).thenReturn("100");
        when(executionContext.renderExpression(THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE)).thenReturn("500");
        when(executionContext.renderExpression(MAX_BACKOFF_ACCOUNT_VARIABLE)).thenReturn("20000");
        when(executionContext.renderExpression(MAX_ERROR_RETRY_ACCOUNT_VARIABLE)).thenReturn("5");

        AwsConfig awsConfig = AwsConfig.builder().build();
        AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(executionContext, awsConfig);

        AmazonClientSDKDefaultBackoffStrategy amazonClientSDKDefaultBackoffStrategy =
                awsConfig.getAmazonClientSDKDefaultBackoffStrategy();
        assertThat(amazonClientSDKDefaultBackoffStrategy.getBaseDelayInMs()).isEqualTo(100);
        assertThat(amazonClientSDKDefaultBackoffStrategy.getThrottledBaseDelayInMs()).isEqualTo(500);
        assertThat(amazonClientSDKDefaultBackoffStrategy.getMaxBackoffInMs()).isEqualTo(20000);
        assertThat(amazonClientSDKDefaultBackoffStrategy.getMaxErrorRetry()).isEqualTo(5);
    }

    @Test
    @Owner(developers = IVAN)
    @Category(UnitTests.class)
    public void testSetAmazonClientSDKDefaultBackoffStrategyIfExistsWithInvalidValues() {
        when(executionContext.renderExpression(BASE_DELAY_ACCOUNT_VARIABLE)).thenReturn("not_valid");
        when(executionContext.renderExpression(THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE)).thenReturn("not_valid");
        when(executionContext.renderExpression(MAX_BACKOFF_ACCOUNT_VARIABLE)).thenReturn("not_valid");
        when(executionContext.renderExpression(MAX_ERROR_RETRY_ACCOUNT_VARIABLE)).thenReturn("not_valid");

        AwsConfig awsConfig = AwsConfig.builder().build();
        AwsHelperServiceManager.setAmazonClientSDKDefaultBackoffStrategyIfExists(executionContext, awsConfig);

        AmazonClientSDKDefaultBackoffStrategy amazonClientSDKDefaultBackoffStrategy =
                awsConfig.getAmazonClientSDKDefaultBackoffStrategy();
        assertThat(amazonClientSDKDefaultBackoffStrategy).isNull();
    }
}

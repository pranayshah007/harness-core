/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegateBase;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.codedeploy.model.AmazonCodeDeployException;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ecs.model.AmazonECSException;
import com.amazonaws.services.ecs.model.ClusterNotFoundException;
import com.amazonaws.services.ecs.model.ServiceNotFoundException;
import com.amazonaws.services.lambda.model.AWSLambdaException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@OwnedBy(CDP)
public class AwsHelperServiceDelegateBaseTest extends WingsBaseTest {
  @InjectMocks @Spy private AwsEcrApiHelperServiceDelegateBase awsEcrApiHelperServiceDelegateBase;

  @Mock private AwsApiHelperService awsApiHelperService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAmazonClientException() {
    AwsHelperServiceDelegateBase delegateBase = spy(AwsHelperServiceDelegateBase.class);
    AmazonClientException exception = new AmazonClientException("Error Message");
    on(delegateBase).set("awsEcrApiHelperServiceDelegateBase", awsEcrApiHelperServiceDelegateBase);
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonClientException(any());
    assertThatThrownBy(() -> delegateBase.handleAmazonClientException(exception))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAmazonServiceException() {
    AwsHelperServiceDelegateBase delegateBase = spy(AwsHelperServiceDelegateBase.class);
    on(delegateBase).set("awsEcrApiHelperServiceDelegateBase", awsEcrApiHelperServiceDelegateBase);
    doCallRealMethod().when(awsEcrApiHelperServiceDelegateBase).handleAmazonServiceException(any());
    AmazonServiceException exception1 = new AmazonCodeDeployException("Error Message");
    assertThatThrownBy(() -> delegateBase.handleAmazonServiceException(exception1))
        .isInstanceOf(InvalidRequestException.class);
    AmazonServiceException exception2 = new AmazonEC2Exception("Error Message");
    assertThatThrownBy(() -> delegateBase.handleAmazonServiceException(exception2))
        .isInstanceOf(InvalidRequestException.class);
    AmazonServiceException exception3 = new ClusterNotFoundException("Error Message");
    assertThatThrownBy(() -> delegateBase.handleAmazonServiceException(exception3))
        .isInstanceOf(InvalidRequestException.class);
    AmazonServiceException exception4 = new ServiceNotFoundException("Error Message");
    assertThatThrownBy(() -> delegateBase.handleAmazonServiceException(exception4))
        .isInstanceOf(InvalidRequestException.class);
    AmazonServiceException exception5 = new AmazonECSException("Error Message");
    assertThatThrownBy(() -> delegateBase.handleAmazonServiceException(exception5))
        .isInstanceOf(InvalidRequestException.class);
    AmazonServiceException exception6 = new AWSLambdaException("Error Message");
    assertThatThrownBy(() -> delegateBase.handleAmazonServiceException(exception6))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetRegion() {
    AwsHelperServiceDelegateBase delegateBase = spy(AwsHelperServiceDelegateBase.class);
    AwsConfig config = AwsConfig.builder().build();
    assertThat(delegateBase.getRegion(config)).isEqualTo(AWS_DEFAULT_REGION);
    config.setDefaultRegion(Regions.US_GOV_EAST_1.getName());
    assertThat(delegateBase.getRegion(config)).isEqualTo(Regions.US_GOV_EAST_1.getName());
  }

  @Test(expected = com.amazonaws.SdkClientException.class)
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testAttachCredentialsAndBackoffPolicyWithIRSA() throws Exception {
    Map<String, String> envVarsMap = Map.ofEntries(Map.entry(SDKGlobalConfiguration.AWS_ROLE_ARN_ENV_VAR, "abcd"),
        Map.entry(SDKGlobalConfiguration.AWS_WEB_IDENTITY_ENV_VAR, "/jkj"));
    new EnvironmentVariables(envVarsMap).execute(() -> {
      AwsInternalConfig awsInternalConfig = mock(AwsInternalConfig.class);
      when(awsInternalConfig.isUseEc2IamCredentials()).thenReturn(false);
      when(awsInternalConfig.isUseIRSA()).thenReturn(true);
      when(awsInternalConfig.isAssumeCrossAccountRole()).thenReturn(false);
      AwsClientBuilder awsClientBuilder = AmazonEC2ClientBuilder.standard().withRegion("us-east-1");
      doCallRealMethod()
          .when(awsEcrApiHelperServiceDelegateBase)
          .attachCredentialsAndBackoffPolicy(eq(awsClientBuilder), eq(awsInternalConfig));

      awsEcrApiHelperServiceDelegateBase.attachCredentialsAndBackoffPolicy(awsClientBuilder, awsInternalConfig);

      assertThat(awsClientBuilder.getCredentials()).isInstanceOf(WebIdentityTokenCredentialsProvider.class);
      awsClientBuilder.getCredentials().getCredentials().getAWSSecretKey();
    });
  }
}

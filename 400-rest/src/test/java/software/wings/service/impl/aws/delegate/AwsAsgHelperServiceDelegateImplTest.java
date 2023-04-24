/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.service.impl.aws.delegate.AwsAmiHelperServiceDelegateImpl.BG_BLUE;
import static software.wings.service.impl.aws.delegate.AwsAmiHelperServiceDelegateImpl.BG_VERSION;
import static software.wings.service.impl.aws.delegate.AwsAmiHelperServiceDelegateImpl.HARNESS_AUTOSCALING_GROUP_TAG;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.exception.UnexpectedException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountData;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationResult;
import com.amazonaws.services.autoscaling.model.CreateOrUpdateTagsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult;
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.amazonaws.services.autoscaling.model.SetDesiredCapacityResult;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.autoscaling.model.TagDescription;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.google.common.util.concurrent.TimeLimiter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class AwsAsgHelperServiceDelegateImplTest extends CategoryTest {
  @Mock private AwsEc2HelperServiceDelegate mockAwsEc2HelperServiceDelegate;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private TimeLimiter mockTimeLimiter;
  @Mock private AwsCallTracker mockTracker;
  @Spy @InjectMocks private AwsAsgHelperServiceDelegateImpl awsAsgHelperServiceDelegate;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListAutoScalingGroupNames() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    List<AutoScalingGroup> groups = asList(new AutoScalingGroup().withAutoScalingGroupName("name1"),
        new AutoScalingGroup().withAutoScalingGroupName("name2"));
    doReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(groups))
        .when(mockClient)
        .describeAutoScalingGroups(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    List<String> result = awsAsgHelperServiceDelegate.listAutoScalingGroupNames(
        AwsConfig.builder().build(), Collections.emptyList(), "us-east-1");
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0)).isEqualTo("name1");
    assertThat(result.get(1)).isEqualTo("name2");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testAddUpdateTagAutoScalingGroup() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    ExecutionLogCallback mockExecutionLogCallback = mock(ExecutionLogCallback.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockTracker).trackASGCall(anyString());
    String asgName = "asg_name";
    String tagKey = BG_VERSION;
    String tagValue = BG_BLUE;
    awsAsgHelperServiceDelegate.addUpdateTagAutoScalingGroup(
        AwsConfig.builder().build(), emptyList(), asgName, "us-east-1", tagKey, tagValue, mockExecutionLogCallback);
    CreateOrUpdateTagsRequest createOrUpdateTagsRequest = new CreateOrUpdateTagsRequest();
    Tag blueVersionTag = new Tag();
    blueVersionTag.withKey(tagKey)
        .withValue(tagValue)
        .withPropagateAtLaunch(true)
        .withResourceId(asgName)
        .withResourceType("auto-scaling-group");
    createOrUpdateTagsRequest.withTags(blueVersionTag);
    verify(mockClient).createOrUpdateTags(createOrUpdateTagsRequest);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListAutoScalingGroupInstances() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    AutoScalingGroup autoScalingGroup = new AutoScalingGroup().withInstances(
        new com.amazonaws.services.autoscaling.model.Instance().withInstanceId("id1"));
    DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
        new DescribeAutoScalingGroupsResult().withAutoScalingGroups(autoScalingGroup);
    doReturn(describeAutoScalingGroupsResult).when(mockClient).describeAutoScalingGroups(any());
    doReturn(singletonList(new com.amazonaws.services.ec2.model.Instance().withInstanceId("id1")))
        .when(mockAwsEc2HelperServiceDelegate)
        .listEc2Instances(any(), anyList(), anyList(), anyString(), eq(false));
    doNothing().when(mockTracker).trackASGCall(anyString());
    List<com.amazonaws.services.ec2.model.Instance> instanceList =
        awsAsgHelperServiceDelegate.listAutoScalingGroupInstances(
            AwsConfig.builder().build(), emptyList(), "us-east-1", "name", false);
    assertThat(instanceList).isNotNull();
    assertThat(instanceList.size()).isEqualTo(1);
    assertThat(instanceList.get(0).getInstanceId()).isEqualTo("id1");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetAutoScalingGroup() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
                 new AutoScalingGroup().withAutoScalingGroupName("asgName")))
        .when(mockClient)
        .describeAutoScalingGroups(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    AutoScalingGroup autoScalingGroup = awsAsgHelperServiceDelegate.getAutoScalingGroup(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "asgName");
    assertThat(autoScalingGroup).isNotNull();
    assertThat(autoScalingGroup.getAutoScalingGroupName()).isEqualTo("asgName");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetLaunchConfiguration() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeLaunchConfigurationsResult().withLaunchConfigurations(
                 new LaunchConfiguration().withLaunchConfigurationName("lcName")))
        .when(mockClient)
        .describeLaunchConfigurations(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    LaunchConfiguration launchConfiguration = awsAsgHelperServiceDelegate.getLaunchConfiguration(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "lcName");
    assertThat(launchConfiguration).isNotNull();
    assertThat(launchConfiguration.getLaunchConfigurationName()).isEqualTo("lcName");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDeleteLaunchConfig() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockTracker).trackASGCall(anyString());
    awsAsgHelperServiceDelegate.deleteLaunchConfig(AwsConfig.builder().build(), emptyList(), "us-east-1", "asgName");
    verify(mockClient).deleteLaunchConfiguration(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateLaunchConfiguration() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new CreateLaunchConfigurationResult()).when(mockClient).createLaunchConfiguration(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    CreateLaunchConfigurationResult result = awsAsgHelperServiceDelegate.createLaunchConfiguration(
        AwsConfig.builder().build(), emptyList(), "us-east-1", new CreateLaunchConfigurationRequest());
    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateAutoScalingGroup() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new CreateAutoScalingGroupResult()).when(mockClient).createAutoScalingGroup(any());
    LogCallback mockCallback = mock(LogCallback.class);
    doNothing().when(mockTracker).trackASGCall(anyString());
    CreateAutoScalingGroupResult result = awsAsgHelperServiceDelegate.createAutoScalingGroup(
        AwsConfig.builder().build(), emptyList(), "us-east-1", new CreateAutoScalingGroupRequest(), mockCallback);
    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDeleteAutoScalingGroups() {
    try {
      final Mocks mocks = prepareMocksForDeleteAutoScalingGroups();
      AmazonAutoScalingClient mockClient = mocks.amazonAutoScalingClient;
      LogCallback mockCallback = mocks.mockCallback;

      awsAsgHelperServiceDelegate.deleteAutoScalingGroups(AwsConfig.builder().build(), emptyList(), "us-east-1",
          singletonList(new AutoScalingGroup().withLaunchConfigurationName("launch_config")), mockCallback);
      verify(mockClient).deleteAutoScalingGroup(any());
      verify(mockClient).deleteLaunchConfiguration(any());
      HTimeLimiterMocker.verifyTimeLimiterCalled(mockTimeLimiter);
    } catch (Exception ex) {
      fail(format("Test threw an exception: [%s]", ex.getMessage()));
    }
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testDeleteAutoScalingGroups_withLT() {
    try {
      final Mocks mocks = prepareMocksForDeleteAutoScalingGroups();
      AmazonAutoScalingClient mockClient = mocks.amazonAutoScalingClient;
      LogCallback mockCallback = mocks.mockCallback;

      awsAsgHelperServiceDelegate.deleteAutoScalingGroups(AwsConfig.builder().build(), emptyList(), "us-east-1",
          singletonList(new AutoScalingGroup().withLaunchTemplate(new LaunchTemplateSpecification())), mockCallback);
      verify(mockClient).deleteAutoScalingGroup(any());
      verify(mockClient, times(0)).deleteLaunchConfiguration(any());
      HTimeLimiterMocker.verifyTimeLimiterCalled(mockTimeLimiter);
    } catch (Exception ex) {
      fail(format("Test threw an exception: [%s]", ex.getMessage()));
    }
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testAllInstanceInReadyStateWithRetryIfFirstCallSuccess() {
    AwsConfig awsConfig = AwsConfig.builder().build();
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    awsAsgHelperServiceDelegate.allInstanceInReadyStateWithRetry(
        awsConfig, emptyList(), "us-east-1", asList("abc"), mockCallBack);
    verify(awsAsgHelperServiceDelegate, times(1))
        .allInstanceInReadyState(awsConfig, emptyList(), "us-east-1", asList("abc"), mockCallBack);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testAllInstanceInReadyStateWithRetryIfFirstCallThrows() {
    AwsConfig awsConfig = AwsConfig.builder().build();
    ExecutionLogCallback mockCallBack = mock(ExecutionLogCallback.class);
    doThrow(new UnexpectedException())
        .doReturn(true)
        .when(awsAsgHelperServiceDelegate)
        .allInstanceInReadyState(awsConfig, emptyList(), "us-east-1", asList("abc"), mockCallBack);
    awsAsgHelperServiceDelegate.allInstanceInReadyStateWithRetry(
        awsConfig, emptyList(), "us-east-1", asList("abc"), mockCallBack);
    verify(awsAsgHelperServiceDelegate, times(2))
        .allInstanceInReadyState(awsConfig, emptyList(), "us-east-1", asList("abc"), mockCallBack);
  }

  @Builder
  private static class Mocks {
    AmazonAutoScalingClient amazonAutoScalingClient;
    LogCallback mockCallback;
    TimeLimiter mockTimeLimiter;
    AwsCallTracker mockTracker;
  }

  private Mocks prepareMocksForDeleteAutoScalingGroups() throws Exception {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    LogCallback mockCallback = mock(LogCallback.class);
    doNothing().when(mockTracker).trackASGCall(anyString());
    HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenReturn(true);

    return Mocks.builder()
        .amazonAutoScalingClient(mockClient)
        .mockCallback(mockCallback)
        .mockTimeLimiter(mockTimeLimiter)
        .mockTracker(mockTracker)
        .build();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetDesiredCapacitiesOfAsgs() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));

    doReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
                 new AutoScalingGroup().withAutoScalingGroupName("name1").withDesiredCapacity(1),
                 new AutoScalingGroup().withAutoScalingGroupName("name2").withDesiredCapacity(2)))
        .when(mockClient)
        .describeAutoScalingGroups(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    Map<String, Integer> result = awsAsgHelperServiceDelegate.getDesiredCapacitiesOfAsgs(
        AwsConfig.builder().build(), emptyList(), "us-east-1", asList("name1", "name2"));
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get("name1")).isEqualTo(1);
    assertThat(result.get("name2")).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testSetAutoScalingGroupLimits() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(new AutoScalingGroup().withMinSize(2)))
        .when(mockClient)
        .describeAutoScalingGroups(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    awsAsgHelperServiceDelegate.setAutoScalingGroupLimits(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "foo", 1, mockCallback);
    verify(mockClient).updateAutoScalingGroup(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testSetMinInstancesForAsg() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeAutoScalingGroupsResult().withAutoScalingGroups(
                 new AutoScalingGroup().withAutoScalingGroupName("foo")))
        .when(mockClient)
        .describeAutoScalingGroups(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    awsAsgHelperServiceDelegate.setMinInstancesForAsg(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "foo", 1, mockCallback);
    verify(mockClient).updateAutoScalingGroup(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testSetAutoScalingGroupCapacityAndWaitForInstancesReadyState() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new SetDesiredCapacityResult()).when(mockClient).setDesiredCapacity(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    try {
      HTimeLimiterMocker.mockCallInterruptible(mockTimeLimiter).thenReturn(true);
      awsAsgHelperServiceDelegate.setAutoScalingGroupCapacityAndWaitForInstancesReadyState(
          AwsConfig.builder().build(), emptyList(), "us-east-1", "asgName", 1, mockCallback, 10, false);
      verify(mockClient).setDesiredCapacity(any());
      HTimeLimiterMocker.verifyTimeLimiterCalled(mockTimeLimiter);
    } catch (Exception ex) {
      fail(format("Test threw an exception: [%s]", ex.getMessage()));
    }
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAllInstanceInReadyState() {
    doReturn(singletonList(new Instance().withState(new InstanceState().withName("warming"))))
        .doReturn(singletonList(new Instance().withState(new InstanceState().withName("running"))))
        .when(mockAwsEc2HelperServiceDelegate)
        .listEc2Instances(any(), anyList(), anyList(), anyString(), anyBoolean());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockTracker).trackASGCall(anyString());
    boolean result = awsAsgHelperServiceDelegate.allInstanceInReadyState(
        AwsConfig.builder().build(), emptyList(), "us-east-1", singletonList("id"), mockCallback);
    assertThat(result).isFalse();
    result = awsAsgHelperServiceDelegate.allInstanceInReadyState(
        AwsConfig.builder().build(), emptyList(), "us-east-1", singletonList("id"), mockCallback);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRegisterAsgWithClassicLBs() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockTracker).trackASGCall(anyString());
    awsAsgHelperServiceDelegate.registerAsgWithClassicLBs(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "Asg", singletonList("classicLbs"), mockCallback);
    verify(mockClient).attachLoadBalancers(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRegisterAsgWithTargetGroups() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockTracker).trackASGCall(anyString());
    awsAsgHelperServiceDelegate.registerAsgWithTargetGroups(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "Asg", singletonList("targetGroups"), mockCallback);
    verify(mockClient).attachLoadBalancerTargetGroups(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDeRegisterAsgWithTargetGroups() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockTracker).trackASGCall(anyString());
    awsAsgHelperServiceDelegate.deRegisterAsgWithTargetGroups(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "Asg", singletonList("targetGroups"), mockCallback);
    verify(mockClient).detachLoadBalancerTargetGroups(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDeRegisterAsgWithClassicLBs() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockTracker).trackASGCall(anyString());
    awsAsgHelperServiceDelegate.deRegisterAsgWithClassicLBs(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "Asg", singletonList("classicLBs"), mockCallback);
    verify(mockClient).detachLoadBalancers(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetCurrentlyRunningInstanceCount() {
    doReturn(asList(new AutoScalingGroup()
                        .withAutoScalingGroupName("Name_1")
                        .withCreatedTime(new Date(10))
                        .withMaxSize(2)
                        .withMinSize(0)
                        .withDesiredCapacity(1)
                        .withTags(new TagDescription()
                                      .withKey(HARNESS_AUTOSCALING_GROUP_TAG)
                                      .withValue(format("%s__1", INFRA_MAPPING_ID))),
                 new AutoScalingGroup()
                     .withAutoScalingGroupName("Name_2")
                     .withCreatedTime(new Date(20))
                     .withMaxSize(3)
                     .withMinSize(0)
                     .withDesiredCapacity(1)
                     .withTags(new TagDescription()
                                   .withKey(HARNESS_AUTOSCALING_GROUP_TAG)
                                   .withValue(format("%s__2", INFRA_MAPPING_ID)))))
        .when(awsAsgHelperServiceDelegate)
        .listAllAsgs(any(), anyList(), anyString());
    doNothing().when(mockTracker).trackASGCall(anyString());
    AwsAsgGetRunningCountData data = awsAsgHelperServiceDelegate.getCurrentlyRunningInstanceCount(
        AwsConfig.builder().build(), emptyList(), "us-east-1", INFRA_MAPPING_ID);
    assertThat(data.getAsgName()).isEqualTo("Name_2");
    assertThat(data.getAsgMax()).isEqualTo(3);
    assertThat(data.getAsgMin()).isEqualTo(0);
    assertThat(data.getAsgDesired()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetScalingPolicyJSONs() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new DescribePoliciesResult().withScalingPolicies(new ScalingPolicy().withPolicyName("policy")))
        .when(mockClient)
        .describePolicies(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    List<String> jSONs = awsAsgHelperServiceDelegate.getScalingPolicyJSONs(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "asg", mockCallback);
    assertThat(jSONs).isNotNull();
    assertThat(jSONs.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testClearAllScalingPoliciesForAsg() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new DescribePoliciesResult().withScalingPolicies(new ScalingPolicy().withPolicyName("policy")))
        .when(mockClient)
        .describePolicies(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    awsAsgHelperServiceDelegate.clearAllScalingPoliciesForAsg(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "asg", mockCallback);
    verify(mockClient).deletePolicy(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAttachScalingPoliciesToAsg() {
    AmazonAutoScalingClient mockClient = mock(AmazonAutoScalingClient.class);
    doReturn(mockClient).when(awsAsgHelperServiceDelegate).getAmazonAutoScalingClient(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new PutScalingPolicyResult().withPolicyARN("arn")).when(mockClient).putScalingPolicy(any());
    doNothing().when(mockTracker).trackASGCall(anyString());
    awsAsgHelperServiceDelegate.attachScalingPoliciesToAsg(AwsConfig.builder().build(), emptyList(), "us-east-1", "asg",
        singletonList(
            "{\"autoScalingGroupName\":null,\"policyName\":\"policy\",\"policyARN\":null,\"policyType\":null,\"adjustmentType\":null,\"minAdjustmentStep\":null,\"minAdjustmentMagnitude\":null,\"scalingAdjustment\":null,\"cooldown\":null,\"stepAdjustments\":[],\"metricAggregationType\":null,\"estimatedInstanceWarmup\":null,\"alarms\":[],\"targetTrackingConfiguration\":null}"),
        mockCallback);
    verify(mockClient).putScalingPolicy(any());
  }
}

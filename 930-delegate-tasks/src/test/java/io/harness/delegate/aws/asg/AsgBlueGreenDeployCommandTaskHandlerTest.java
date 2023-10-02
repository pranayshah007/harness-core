/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.request.AsgInstanceCapacity;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployResult;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsUtils;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import java.util.Arrays;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class AsgBlueGreenDeployCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  static final String ASG_NAME = "testAsg";
  static final String LISTENER_RULE_ARN = "listenerRuleArn";
  static final String STAGE_LISTENER_ARN = "stageListenerArn";
  static final String PROD_LISTENER_ARN = "prodListenerArn";

  static final String LAUNCH_TEMPLATE_CONTENT = "{\"LaunchTemplateData\": { \"InstanceType\": \"t2.micro\" }}";
  static final String CONFIG_CONTENT = format("{\"autoScalingGroupName\": \"%s\"}", ASG_NAME);

  @Mock AsgTaskHelper asgTaskHelper;
  @Mock ElbV2Client elbV2Client;
  @Mock AwsUtils awsUtils;

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock LogCallback prepareRollbackLogCallback;
  @Mock AsgSdkManager asgSdkManager;

  @Spy @InjectMocks private AsgBlueGreenDeployCommandTaskHandler taskHandler;

  CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  AsgInfraConfig asgInfraConfig =
      AsgInfraConfig.builder().region("us-east-1").awsConnectorDTO(AwsConnectorDTO.builder().build()).build();

  @Before
  public void setup() {
    doReturn(prepareRollbackLogCallback).when(asgTaskHelper).getLogCallback(any(), anyString(), anyBoolean(), any());
    doReturn(asgSdkManager).when(asgTaskHelper).getAsgSdkManager(any(), any(), any(ElbV2Client.class));
    doReturn(AwsInternalConfig.builder().build()).when(awsUtils).getAwsInternalConfig(any(), anyString());
    doReturn(LAUNCH_TEMPLATE_CONTENT).when(asgTaskHelper).getAsgLaunchTemplateContent(any());
    doReturn(CONFIG_CONTENT).when(asgTaskHelper).getAsgConfigurationContent(any());
    doReturn(new LaunchTemplate().withLatestVersionNumber(1L))
        .when(asgSdkManager)
        .createLaunchTemplate(anyString(), any());
    doReturn(
        new AutoScalingGroup().withAutoScalingGroupName(ASG_NAME).withDesiredCapacity(1).withMinSize(1).withMaxSize(3))
        .when(asgSdkManager)
        .getASG(anyString());
    doReturn(AutoScalingGroupContainer.builder().autoScalingGroupName(ASG_NAME).build())
        .when(asgTaskHelper)
        .mapToAutoScalingGroupContainer(any());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalTestIsFirstDeployment() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenDeployRequest request = createRequest(lbConfig, true, false);

    AsgBlueGreenDeployResponse response = (AsgBlueGreenDeployResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    AsgBlueGreenDeployResult result = response.getAsgBlueGreenDeployResult();

    assertThat(result.getStageAutoScalingGroupContainer().getAutoScalingGroupName()).isEqualTo(ASG_NAME);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalTestIsNotFirstDeployment() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenDeployRequest request = createRequest(lbConfig, false, false);

    AsgBlueGreenDeployResponse response = (AsgBlueGreenDeployResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    AsgBlueGreenDeployResult result = response.getAsgBlueGreenDeployResult();

    assertThat(result.getProdAutoScalingGroupContainer().getAutoScalingGroupName()).isEqualTo(ASG_NAME);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalUseAlreadyRunningInstances() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenDeployRequest request = createRequest(lbConfig, false, true);

    AsgBlueGreenDeployResponse response = (AsgBlueGreenDeployResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    AsgBlueGreenDeployResult result = response.getAsgBlueGreenDeployResult();

    assertThat(result.getProdAutoScalingGroupContainer().getAutoScalingGroupName()).isEqualTo(ASG_NAME);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getNrOfAlreadyRunningInstancesTest() {
    AsgSdkManager asgSdkManager = mock(AsgSdkManager.class);
    doReturn(
        new AutoScalingGroup().withAutoScalingGroupName(ASG_NAME).withDesiredCapacity(2).withMinSize(1).withMaxSize(3))
        .when(asgSdkManager)
        .getASG(anyString());

    AsgInstanceCapacity ret = taskHandler.getRunningInstanceCapacity(asgSdkManager, true, false, "test");
    assertThat(ret.getMinCapacity()).isEqualTo(1);
    assertThat(ret.getDesiredCapacity()).isEqualTo(2);
    assertThat(ret.getMaxCapacity()).isEqualTo(3);

    ret = taskHandler.getRunningInstanceCapacity(asgSdkManager, false, false, "test");
    assertThat(ret.getDesiredCapacity()).isNull();

    ret = taskHandler.getRunningInstanceCapacity(asgSdkManager, true, true, "test");
    assertThat(ret.getDesiredCapacity()).isNull();

    asgSdkManager = mock(AsgSdkManager.class);
    doReturn(null).when(asgSdkManager).getASG(anyString());
    ret = taskHandler.getRunningInstanceCapacity(asgSdkManager, true, false, "test");
    assertThat(ret.getDesiredCapacity()).isNull();
  }

  private AsgLoadBalancerConfig getAsgLoadBalancerConfig() {
    return AsgLoadBalancerConfig.builder()
        .loadBalancer("lb")
        .stageListenerArn(STAGE_LISTENER_ARN)
        .stageListenerRuleArn(LISTENER_RULE_ARN)
        .prodListenerArn(PROD_LISTENER_ARN)
        .prodListenerRuleArn(LISTENER_RULE_ARN)
        .stageTargetGroupArnsList(Arrays.asList("tg1"))
        .prodTargetGroupArnsList(Arrays.asList("tg2"))
        .build();
  }

  private AsgBlueGreenDeployRequest createRequest(
      AsgLoadBalancerConfig asgLoadBalancerConfig, boolean isFirstDeployment, boolean useAlreadyRunningInstances) {
    return AsgBlueGreenDeployRequest.builder()
        .commandUnitsProgress(commandUnitsProgress)
        .timeoutIntervalInMin(1)
        .asgLoadBalancerConfig(asgLoadBalancerConfig)
        .asgStoreManifestsContent(new HashMap<>())
        .asgInfraConfig(asgInfraConfig)
        .asgName(ASG_NAME)
        .amiImageId("ami-1234")
        .firstDeployment(isFirstDeployment)
        .useAlreadyRunningInstances(useAlreadyRunningInstances)
        .build();
  }
}

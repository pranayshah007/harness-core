/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.asg.AsgBlueGreenSwapServiceRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenSwapServiceResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenSwapServiceResult;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsUtils;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AsgBlueGreenSwapServiceCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  static final String ASG_NAME_PROD = "testAsg__1";
  static final String ASG_NAME_STAGE = "testAsg__2";
  static final String LISTENER_RULE_ARN = "listenerRuleArn";
  static final String STAGE_LISTENER_ARN = "stageListenerArn";
  static final String PROD_LISTENER_ARN = "prodListenerArn";

  @Mock AsgTaskHelper asgTaskHelper;
  @Mock ElbV2Client elbV2Client;
  @Mock AwsUtils awsUtils;

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock LogCallback swapServiceLogCallback;
  @Mock AsgSdkManager asgSdkManager;
  @Mock AwsNgConfigMapper awsNgConfigMapper;

  @Spy @InjectMocks private AsgBlueGreenSwapServiceCommandTaskHandler taskHandler;

  CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  AsgInfraConfig asgInfraConfig =
      AsgInfraConfig.builder().region("us-east-1").awsConnectorDTO(AwsConnectorDTO.builder().build()).build();

  AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

  @Before
  public void setup() {
    doReturn(swapServiceLogCallback).when(asgTaskHelper).getLogCallback(any(), anyString(), anyBoolean(), any());
    doReturn(asgSdkManager).when(asgTaskHelper).getAsgSdkManager(any(), any(), any(ElbV2Client.class));
    doReturn(AwsInternalConfig.builder().build()).when(awsUtils).getAwsInternalConfig(any(), anyString());
    AutoScalingGroup prodAutoScalingGroup = new AutoScalingGroup().withAutoScalingGroupName(ASG_NAME_PROD);
    doReturn(prodAutoScalingGroup).when(asgSdkManager).getASG(ASG_NAME_PROD);
    doReturn(AutoScalingGroupContainer.builder().autoScalingGroupName(ASG_NAME_PROD).build())
        .when(asgTaskHelper)
        .mapToAutoScalingGroupContainer(prodAutoScalingGroup);
    AutoScalingGroup stageAutoScalingGroup = new AutoScalingGroup().withAutoScalingGroupName(ASG_NAME_STAGE);
    doReturn(stageAutoScalingGroup).when(asgSdkManager).getASG(ASG_NAME_STAGE);
    doReturn(AutoScalingGroupContainer.builder().autoScalingGroupName(ASG_NAME_STAGE).build())
        .when(asgTaskHelper)
        .mapToAutoScalingGroupContainer(stageAutoScalingGroup);
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(any());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenSwapServiceRequest request = createRequest(lbConfig, false);

    AsgBlueGreenSwapServiceResponse response = (AsgBlueGreenSwapServiceResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    AsgBlueGreenSwapServiceResult result = response.getAsgBlueGreenSwapServiceResult();

    assertThat(result.getProdAutoScalingGroupContainer().getAutoScalingGroupName()).isEqualTo(ASG_NAME_STAGE);
    assertThat(result.getStageAutoScalingGroupContainer().getAutoScalingGroupName()).isEqualTo(ASG_NAME_PROD);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalWithDownsizeTest() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenSwapServiceRequest request = createRequest(lbConfig, true);

    AsgBlueGreenSwapServiceResponse response = (AsgBlueGreenSwapServiceResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    AsgBlueGreenSwapServiceResult result = response.getAsgBlueGreenSwapServiceResult();

    assertThat(result.getProdAutoScalingGroupContainer().getAutoScalingGroupName()).isEqualTo(ASG_NAME_STAGE);
    assertThat(result.getStageAutoScalingGroupContainer().getAutoScalingGroupName()).isEqualTo(ASG_NAME_PROD);
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

  private AsgBlueGreenSwapServiceRequest createRequest(
      AsgLoadBalancerConfig asgLoadBalancerConfig, boolean downsizeOldAsg) {
    return AsgBlueGreenSwapServiceRequest.builder()
        .commandUnitsProgress(commandUnitsProgress)
        .timeoutIntervalInMin(1)
        .asgLoadBalancerConfig(asgLoadBalancerConfig)
        .asgInfraConfig(asgInfraConfig)
        .prodAsgName(ASG_NAME_PROD)
        .stageAsgName(ASG_NAME_STAGE)
        .downsizeOldAsg(downsizeOldAsg)
        .build();
  }
}

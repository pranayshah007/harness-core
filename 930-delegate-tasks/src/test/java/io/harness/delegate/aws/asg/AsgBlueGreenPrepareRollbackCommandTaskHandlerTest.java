/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.aws.asg.AsgSdkManager.BG_BLUE;
import static io.harness.aws.asg.AsgSdkManager.BG_GREEN;
import static io.harness.aws.asg.AsgSdkManager.BG_VERSION;
import static io.harness.delegate.aws.asg.AsgBlueGreenPrepareRollbackCommandTaskHandler.VERSION_DELIMITER;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataResult;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsUtils;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.autoscaling.model.TagDescription;
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

public class AsgBlueGreenPrepareRollbackCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  static final String ASG_NAME = "testAsg";
  static final String LISTENER_RULE_ARN = "listenerRuleArn";
  static final String STAGE_LISTENER_ARN = "stageListenerArn";
  static final String PROD_LISTENER_ARN = "prodListenerArn";

  @Mock AsgTaskHelper asgTaskHelper;
  @Mock ElbV2Client elbV2Client;
  @Mock AwsUtils awsUtils;

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock LogCallback prepareRollbackLogCallback;
  @Mock AsgSdkManager asgSdkManager;

  @Spy @InjectMocks private AsgBlueGreenPrepareRollbackCommandTaskHandler taskHandler;

  CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  AsgInfraConfig asgInfraConfig =
      AsgInfraConfig.builder().region("us-east-1").awsConnectorDTO(AwsConnectorDTO.builder().build()).build();

  @Before
  public void setup() {
    doReturn(prepareRollbackLogCallback).when(asgTaskHelper).getLogCallback(any(), anyString(), anyBoolean(), any());
    doReturn(asgSdkManager).when(asgTaskHelper).getAsgSdkManager(any(), any(), any(ElbV2Client.class));
    doReturn(AwsInternalConfig.builder().build()).when(awsUtils).getAwsInternalConfig(any(), anyString());
    doReturn(format("{\"autoScalingGroupName\": \"%s\"}", ASG_NAME))
        .when(asgTaskHelper)
        .getAsgConfigurationContent(any());
    doReturn(Arrays.asList(software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule.builder()
                               .ruleArn(LISTENER_RULE_ARN)
                               .build()))
        .when(asgSdkManager)
        .getListenerRulesForListener(any(), anyString(), anyString());
    doReturn(Arrays.asList("tg1"))
        .when(asgSdkManager)
        .getTargetGroupArnsFromLoadBalancer(anyString(), anyString(), anyString(), anyString(), any());
    doReturn(ASG_NAME).when(asgTaskHelper).getAsgName(any(), any());
    doReturn(new HashMap<>()).when(asgTaskHelper).getAsgStoreManifestsContent(any(), any(), any());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalTestIsFirstDeployment() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenPrepareRollbackDataRequest request = createRequest(lbConfig);

    doReturn(null).when(asgSdkManager).getASG(anyString());
    doReturn(null).when(asgSdkManager).getASG(anyString());

    AsgBlueGreenPrepareRollbackDataResponse response =
        (AsgBlueGreenPrepareRollbackDataResponse) taskHandler.executeTaskInternal(
            request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    AsgBlueGreenPrepareRollbackDataResult result = response.getAsgBlueGreenPrepareRollbackDataResult();

    assertThat(result.getProdAsgName()).isNull();
    assertThat(result.getAsgName()).isEqualTo(ASG_NAME + VERSION_DELIMITER + 1);

    assertThat(lbConfig.getProdTargetGroupArnsList()).isNotEmpty();
    assertThat(lbConfig.getStageTargetGroupArnsList()).isNotEmpty();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalTestIsNotFirstDeployment() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenPrepareRollbackDataRequest request = createRequest(lbConfig);

    String prodAsgName = ASG_NAME + VERSION_DELIMITER + 1;
    String stageAsgName = ASG_NAME + VERSION_DELIMITER + 2;

    AutoScalingGroup prodAutoScalingGroup = new AutoScalingGroup()
                                                .withAutoScalingGroupName(prodAsgName)
                                                .withLaunchTemplate(new LaunchTemplateSpecification().withVersion("1"))
                                                .withTags(new TagDescription().withKey(BG_VERSION).withValue(BG_BLUE));

    AutoScalingGroup stageAutoScalingGroup =
        new AutoScalingGroup()
            .withLaunchTemplate(new LaunchTemplateSpecification().withVersion("1"))
            .withAutoScalingGroupName(stageAsgName)
            .withTags(new TagDescription().withKey(BG_VERSION).withValue(BG_GREEN));

    doReturn(prodAutoScalingGroup).when(asgSdkManager).getASG(eq(prodAsgName));
    doReturn(stageAutoScalingGroup).when(asgSdkManager).getASG(eq(stageAsgName));

    AsgBlueGreenPrepareRollbackDataResponse response =
        (AsgBlueGreenPrepareRollbackDataResponse) taskHandler.executeTaskInternal(
            request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    AsgBlueGreenPrepareRollbackDataResult result = response.getAsgBlueGreenPrepareRollbackDataResult();

    assertThat(result.getProdAsgName()).isEqualTo(prodAsgName);
    assertThat(result.getAsgName()).isEqualTo(stageAsgName);
    assertThat(result.getProdAsgManifestsDataForRollback()).isNotEmpty();
    assertThat(result.getStageAsgManifestsDataForRollback()).isNotEmpty();

    assertThat(lbConfig.getProdTargetGroupArnsList()).isNotEmpty();
    assertThat(lbConfig.getStageTargetGroupArnsList()).isNotEmpty();
  }

  private AsgLoadBalancerConfig getAsgLoadBalancerConfig() {
    return AsgLoadBalancerConfig.builder()
        .loadBalancer("lb")
        .stageListenerArn(STAGE_LISTENER_ARN)
        .stageListenerRuleArn(LISTENER_RULE_ARN)
        .prodListenerArn(PROD_LISTENER_ARN)
        .prodListenerRuleArn(LISTENER_RULE_ARN)
        .build();
  }

  private AsgBlueGreenPrepareRollbackDataRequest createRequest(AsgLoadBalancerConfig asgLoadBalancerConfig) {
    return AsgBlueGreenPrepareRollbackDataRequest.builder()
        .commandUnitsProgress(commandUnitsProgress)
        .timeoutIntervalInMin(1)
        .asgLoadBalancerConfig(asgLoadBalancerConfig)
        .asgStoreManifestsContent(new HashMap<>())
        .asgInfraConfig(asgInfraConfig)
        .build();
  }
}

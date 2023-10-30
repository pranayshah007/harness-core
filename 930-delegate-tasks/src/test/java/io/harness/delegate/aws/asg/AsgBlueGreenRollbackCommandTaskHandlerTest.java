/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenRollbackResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsUtils;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgBlueGreenRollbackCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  static final String PROD_ASG_NAME = "testAsg__1";
  static final String STAGE_ASG_NAME = "testAsg__2";
  static final String LISTENER_RULE_ARN = "listenerRuleArn";
  static final String STAGE_LISTENER_ARN = "stageListenerArn";
  static final String PROD_LISTENER_ARN = "prodListenerArn";

  static final String LAUNCH_TEMPLATE_CONTENT = "{\"LaunchTemplateData\": { \"InstanceType\": \"t2.micro\" }}";
  static final String CONFIG_CONTENT =
      "{\"minSize\": 1,\"maxSize\": 1,\"desiredCapacity\": 1,\"launchTemplate\": {\"version\": \"1\", \"launchTemplateName\": \"asg\"}}";

  @Mock AsgTaskHelper asgTaskHelper;
  @Mock ElbV2Client elbV2Client;
  @Mock AwsUtils awsUtils;

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock LogCallback prepareRollbackLogCallback;
  @Mock AsgSdkManager asgSdkManager;

  @Spy @InjectMocks private AsgBlueGreenRollbackCommandTaskHandler taskHandler;

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
    doReturn(new LaunchTemplate().withLatestVersionNumber(1L).withLaunchTemplateName(PROD_ASG_NAME))
        .when(asgSdkManager)
        .createLaunchTemplate(anyString(), any());
    doReturn(new AutoScalingGroup().withAutoScalingGroupName(PROD_ASG_NAME)).when(asgSdkManager).getASG(anyString());
    doReturn(AutoScalingGroupContainer.builder().autoScalingGroupName(PROD_ASG_NAME).build())
        .when(asgTaskHelper)
        .mapToAutoScalingGroupContainer(any());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalTestIsFirstDeployment() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenRollbackRequest request = createRequest(lbConfig, null, null, null, false);

    AsgBlueGreenRollbackResponse response = (AsgBlueGreenRollbackResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    ArgumentCaptor<String> asgNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(asgSdkManager, times(1)).deleteAsg(asgNameCaptor.capture());
    assertThat(asgNameCaptor.getValue()).isEqualTo(STAGE_ASG_NAME);

    verify(asgSdkManager, times(0)).updateASG(anyString(), anyString(), anyString(), any());
    verify(asgSdkManager, times(0)).modifySpecificListenerRule(anyString(), anyString(), any(), any());
    verify(asgSdkManager, times(0)).updateBGTags(anyString(), anyString());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalTestIsSecondDeployment() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenRollbackRequest request =
        createRequest(lbConfig, PROD_ASG_NAME, null, Collections.singletonMap("key", new ArrayList<>()), false);

    AsgBlueGreenRollbackResponse response = (AsgBlueGreenRollbackResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    ArgumentCaptor<String> asgNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(asgSdkManager, times(1)).deleteAsg(asgNameCaptor.capture());
    assertThat(asgNameCaptor.getValue()).isEqualTo(STAGE_ASG_NAME);

    verify(asgSdkManager, times(1)).updateASG(anyString(), anyString(), anyString(), any());
    verify(asgSdkManager, times(0)).modifySpecificListenerRule(anyString(), anyString(), any(), any());
    verify(asgSdkManager, times(0)).updateBGTags(anyString(), anyString());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalTestServicesSwapped() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenRollbackRequest request = createRequest(lbConfig, PROD_ASG_NAME,
        Collections.singletonMap("key", new ArrayList<>()), Collections.singletonMap("key2", new ArrayList<>()), true);

    AsgBlueGreenRollbackResponse response = (AsgBlueGreenRollbackResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(asgSdkManager, times(0)).deleteAsg(anyString());
    verify(asgSdkManager, times(2)).updateASG(anyString(), anyString(), anyString(), any());
    verify(asgSdkManager, times(2)).modifySpecificListenerRule(anyString(), anyString(), any(), any());
    verify(asgSdkManager, times(2)).updateBGTags(anyString(), anyString());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void executeTaskInternalTestServicesNotSwapped() {
    AsgLoadBalancerConfig lbConfig = getAsgLoadBalancerConfig();
    AsgBlueGreenRollbackRequest request = createRequest(lbConfig, PROD_ASG_NAME,
        Collections.singletonMap("key", new ArrayList<>()), Collections.singletonMap("key2", new ArrayList<>()), false);

    AsgBlueGreenRollbackResponse response = (AsgBlueGreenRollbackResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(asgSdkManager, times(0)).deleteAsg(anyString());
    verify(asgSdkManager, times(2)).updateASG(anyString(), anyString(), anyString(), any());
    verify(asgSdkManager, times(0)).modifySpecificListenerRule(anyString(), anyString(), any(), any());
    verify(asgSdkManager, times(0)).updateBGTags(anyString(), anyString());
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

  private AsgBlueGreenRollbackRequest createRequest(AsgLoadBalancerConfig asgLoadBalancerConfig, String prodAsgName,
      Map<String, List<String>> stageAsgManifestsDataForRollback,
      Map<String, List<String>> prodAsgManifestsDataForRollback, boolean servicesSwapped) {
    return AsgBlueGreenRollbackRequest.builder()
        .commandUnitsProgress(commandUnitsProgress)
        .timeoutIntervalInMin(1)
        .asgLoadBalancerConfig(asgLoadBalancerConfig)
        .asgInfraConfig(asgInfraConfig)
        .prodAsgName(prodAsgName)
        .stageAsgName(STAGE_ASG_NAME)
        .prodAsgManifestsDataForRollback(prodAsgManifestsDataForRollback)
        .stageAsgManifestsDataForRollback(stageAsgManifestsDataForRollback)
        .servicesSwapped(servicesSwapped)
        .build();
  }
}

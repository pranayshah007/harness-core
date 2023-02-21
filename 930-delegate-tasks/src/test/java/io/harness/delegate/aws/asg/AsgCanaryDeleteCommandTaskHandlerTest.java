/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResponse;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResult;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.service.impl.AwsUtils;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgCanaryDeleteCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  static final String ASG_NAME_CANARY = "testAsg__Canary";

  @Mock AsgTaskHelper asgTaskHelper;
  @Mock AwsUtils awsUtils;

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock LogCallback canaryDeleteLogCallback;
  @Mock AsgSdkManager asgSdkManager;

  @Spy @InjectMocks private AsgCanaryDeleteCommandTaskHandler taskHandler;

  CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

  AsgInfraConfig asgInfraConfig =
      AsgInfraConfig.builder().region("us-east-1").awsConnectorDTO(AwsConnectorDTO.builder().build()).build();

  @Before
  public void setup() {
    doReturn(canaryDeleteLogCallback).when(asgTaskHelper).getLogCallback(any(), anyString(), anyBoolean(), any());
    doReturn(asgSdkManager).when(asgTaskHelper).getAsgSdkManager(any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsUtils).getAwsInternalConfig(any(), anyString());
    doReturn(new AutoScalingGroup().withAutoScalingGroupName(ASG_NAME_CANARY)).when(asgSdkManager).getASG(anyString());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() {
    AsgCanaryDeleteRequest request = createRequest();

    AsgCanaryDeleteResponse response = (AsgCanaryDeleteResponse) taskHandler.executeTaskInternal(
        request, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    AsgCanaryDeleteResult result = response.getAsgCanaryDeleteResult();

    assertThat(result.isCanaryDeleted()).isEqualTo(true);
  }

  private AsgCanaryDeleteRequest createRequest() {
    return AsgCanaryDeleteRequest.builder()
        .commandUnitsProgress(commandUnitsProgress)
        .timeoutIntervalInMin(1)
        .asgInfraConfig(asgInfraConfig)
        .canaryAsgName(ASG_NAME_CANARY)
        .build();
  }
}

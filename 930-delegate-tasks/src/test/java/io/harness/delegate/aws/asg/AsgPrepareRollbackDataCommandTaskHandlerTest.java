/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static software.wings.beans.LogHelper.color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployRequest;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgPrepareRollbackDataResponse;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgPrepareRollbackDataCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock AsgTaskHelper asgTaskHelper;
  @Mock LogCallback deployLogCallback;
  @Mock AsgSdkManager asgSdkManager;
  @Mock CreateAutoScalingGroupRequest createAutoScalingGroupRequest;
  @Spy @InjectMocks private AsgPrepareRollbackDataCommandTaskHandler asgPrepareRollbackDataCommandTaskHandler;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().region("us-east-1").build();

    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put("AsgLaunchTemplate", Collections.singletonList("asgLaunchTemplate"));
    asgStoreManifestsContent.put("AsgConfiguration", Collections.singletonList("asgConfiguration"));
    asgStoreManifestsContent.put("AsgScalingPolicy", Collections.singletonList("asgScalingPolicy"));

    AsgPrepareRollbackDataRequest asgPrepareRollbackDataRequest =
            AsgPrepareRollbackDataRequest.builder()
                    .timeoutIntervalInMin(10)
                    .commandUnitsProgress(commandUnitsProgress)
                    .asgStoreManifestsContent(asgStoreManifestsContent)
                    .asgInfraConfig(asgInfraConfig)
                    .build();

    doReturn(deployLogCallback)
            .when(asgTaskHelper)
            .getLogCallback(iLogStreamingTaskClient, AsgCommandUnitConstants.prepareRollbackData.toString(), true,
                    commandUnitsProgress);

    doReturn("configurationContent").when(asgTaskHelper).getAsgConfigurationContent(asgStoreManifestsContent);

    doReturn("asg").when(createAutoScalingGroupRequest).getAutoScalingGroupName();

    AsgPrepareRollbackDataResponse asgPrepareRollbackDataResponse =
            (AsgPrepareRollbackDataResponse) asgPrepareRollbackDataCommandTaskHandler.executeTaskInternal(
                    asgPrepareRollbackDataRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(asgPrepareRollbackDataResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(deployLogCallback)
            .saveExecutionLog(
                    color("Prepare Rollback Data Operation Finished Successfully", LogColor.Green, LogWeight.Bold),
                    LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeTaskInternalAsgCanaryDeployRequestTest() {
    AsgCanaryDeployRequest asgCanaryDeployRequest = AsgCanaryDeployRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    asgPrepareRollbackDataCommandTaskHandler.executeTaskInternal(
            asgCanaryDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}

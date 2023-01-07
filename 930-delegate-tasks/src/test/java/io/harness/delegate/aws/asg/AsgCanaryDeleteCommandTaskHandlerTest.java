/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.asg.AsgCommandUnitConstants;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.category.element.UnitTests;
import io.harness.cdng.aws.asg.AsgCanaryDeleteStepParameters;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeleteResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgRollingDeployRequest;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

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
  private final AsgCanaryDeleteStepParameters asgSpecParameters =
          AsgCanaryDeleteStepParameters.infoBuilder().asgCanaryDeployFqn("deploy").asgCanaryDeleteFqn("delete").build();

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock AsgTaskHelper asgTaskHelper;
  @Mock LogCallback deployLogCallback;
  @Mock AsgSdkManager asgSdkManager;
  @Spy @InjectMocks private AsgCanaryDeleteCommandTaskHandler asgCanaryDeleteCommandTaskHandler;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().region("us-east-1").build();

    AsgCanaryDeleteRequest asgCanaryDeleteRequest = AsgCanaryDeleteRequest.builder()
            .timeoutIntervalInMin(10)
            .commandUnitsProgress(commandUnitsProgress)
            .canaryAsgName("canaryasg")
            .asgInfraConfig(asgInfraConfig)
            .build();

    doReturn(deployLogCallback)
            .when(asgTaskHelper)
            .getLogCallback(
                    iLogStreamingTaskClient, AsgCommandUnitConstants.deleteService.toString(), true, commandUnitsProgress);

    AsgCanaryDeleteResponse asgCanaryDeleteResponse =
            (AsgCanaryDeleteResponse) asgCanaryDeleteCommandTaskHandler.executeTaskInternal(
                    asgCanaryDeleteRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(asgCanaryDeleteResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(asgCanaryDeleteResponse.getAsgCanaryDeleteResult().getCanaryAsgName()).isEqualTo("canaryasg");

    verify(deployLogCallback)
            .saveExecutionLog(color(format("Deletion Finished Successfully"), LogColor.Green, LogWeight.Bold),
                    LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeTaskInternalAsgRollingDeployRequestTest() throws Exception {
    AsgRollingDeployRequest asgRollingDeployRequest = AsgRollingDeployRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    asgCanaryDeleteCommandTaskHandler.executeTaskInternal(
            asgRollingDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}

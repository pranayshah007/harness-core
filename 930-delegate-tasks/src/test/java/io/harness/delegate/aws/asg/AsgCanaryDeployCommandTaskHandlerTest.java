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
import io.harness.cdng.aws.asg.AsgCanaryDeployStepParameters;
import io.harness.cdng.common.capacity.Capacity;
import io.harness.cdng.common.capacity.CapacitySpec;
import io.harness.cdng.common.capacity.CountCapacitySpec;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployRequest;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgRollingDeployRequest;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.pms.yaml.ParameterField;
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

public class AsgCanaryDeployCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private final ParameterField<Integer> count = ParameterField.createValueField(1);
  private final CapacitySpec spec = CountCapacitySpec.builder().count(count).build();
  private final Capacity instanceSelection = Capacity.builder().spec(spec).build();
  private final AsgCanaryDeployStepParameters asgSpecParameters =
          AsgCanaryDeployStepParameters.infoBuilder().instanceSelection(instanceSelection).build();

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock AsgTaskHelper asgTaskHelper;
  @Mock LogCallback deployLogCallback;
  @Mock AsgSdkManager asgSdkManager;
  @Mock CreateAutoScalingGroupRequest createAutoScalingGroupRequest;
  @Spy @InjectMocks private AsgCanaryDeployCommandTaskHandler asgCanaryDeployCommandTaskHandler;

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().region("us-east-1").build();

    Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
    asgStoreManifestsContent.put("AsgLaunchTemplate", Collections.singletonList("asgLaunchTemplate"));
    asgStoreManifestsContent.put("AsgConfiguration", Collections.singletonList("asgConfiguration"));

    AsgCanaryDeployRequest asgCanaryDeployRequest =
            AsgCanaryDeployRequest.builder()
                    .timeoutIntervalInMin(10)
                    .commandUnitsProgress(commandUnitsProgress)
                    .asgStoreManifestsContent(asgStoreManifestsContent)
                    .unitValue(asgSpecParameters.getInstanceSelection().getSpec().getInstances())
                    .unitType(asgSpecParameters.getInstanceSelection().getSpec().getType())
                    .serviceNameSuffix("canary")
                    .asgInfraConfig(asgInfraConfig)
                    .build();

    doReturn(deployLogCallback)
            .when(asgTaskHelper)
            .getLogCallback(iLogStreamingTaskClient, AsgCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    doReturn("launchTemplateContent").when(asgTaskHelper).getAsgLaunchTemplateContent(asgStoreManifestsContent);

    doReturn("configurationContent").when(asgTaskHelper).getAsgConfigurationContent(asgStoreManifestsContent);

    doReturn("asg").when(createAutoScalingGroupRequest).getAutoScalingGroupName();

    AsgCanaryDeployResponse asgCanaryDeployResponse =
            (AsgCanaryDeployResponse) asgCanaryDeployCommandTaskHandler.executeTaskInternal(
                    asgCanaryDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(asgCanaryDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(deployLogCallback)
            .saveExecutionLog(color("Deployment Finished Successfully", LogColor.Green, LogWeight.Bold), LogLevel.INFO,
                    CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeTaskInternalAsgRollingDeployRequestTest() {
    AsgRollingDeployRequest asgRollingDeployRequest = AsgRollingDeployRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    asgCanaryDeployCommandTaskHandler.executeTaskInternal(
            asgRollingDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}

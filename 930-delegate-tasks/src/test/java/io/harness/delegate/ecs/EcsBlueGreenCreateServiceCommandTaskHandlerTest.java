/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraType;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.api.client.util.Lists;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsBlueGreenCreateServiceCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String prodListenerArn = "prodListenerArn";
  private final String prodListenerRuleArn = "prodListenerRuleArn";
  private final String stageListenerArn = "stageListenerArn";
  private final String stageListenerRuleArn = "stageListenerArn";
  private final String loadBalancer = "loadBalancer";

  private final EcsLoadBalancerConfig ecsLoadBalancerConfig = EcsLoadBalancerConfig.builder()
                                                                  .loadBalancer(loadBalancer)
                                                                  .prodListenerArn(prodListenerArn)
                                                                  .prodListenerRuleArn(prodListenerRuleArn)
                                                                  .stageListenerArn(stageListenerArn)
                                                                  .stageListenerRuleArn(stageListenerRuleArn)
                                                                  .build();

  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private EcsTaskHelperBase ecsTaskHelperBase;
  @Mock private LogCallback createServiceLogCallback;
  @Mock private EcsDeploymentHelper ecsDeploymentHelper;

  @Spy @InjectMocks private EcsBlueGreenCreateServiceCommandTaskHandler ecsBlueGreenCreateServiceCommandTaskHandler;

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalNotEcsBlueGreenCreateServiceRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsBlueGreenCreateServiceCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalEcsBlueGreenCreateServiceRequestTest() throws Exception {
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder()
                                        .region("us-east-1")
                                        .ecsInfraType(EcsInfraType.ECS)
                                        .cluster("cluster")
                                        .awsConnectorDTO(AwsConnectorDTO.builder().build())
                                        .build();
    EcsCommandRequest ecsCommandRequest = EcsBlueGreenCreateServiceRequest.builder()
                                              .ecsInfraConfig(ecsInfraConfig)
                                              .timeoutIntervalInMin(10)
                                              .ecsTaskDefinitionManifestContent("taskDef")
                                              .ecsServiceDefinitionManifestContent("serviceDef")
                                              .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
                                              .ecsCommandType(EcsCommandTypeNG.ECS_TASK_ARN_BLUE_GREEN_CREATE_SERVICE)
                                              .ecsScalableTargetManifestContentList(Lists.newArrayList())
                                              .ecsScalingPolicyManifestContentList(Lists.newArrayList())
                                              .targetGroupArnKey("testarn")
                                              .build();
    EcsBlueGreenCreateServiceResponse ecsBlueGreenCreateServiceResponse =
        EcsBlueGreenCreateServiceResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .ecsBlueGreenCreateServiceResult(EcsBlueGreenCreateServiceResult.builder()
                                                 .loadBalancer(ecsLoadBalancerConfig.getLoadBalancer())
                                                 .listenerRuleArn(ecsLoadBalancerConfig.getStageListenerRuleArn())
                                                 .listenerArn(ecsLoadBalancerConfig.getStageListenerArn())
                                                 .region("us-east-1")
                                                 .build())
            .build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(createServiceLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    doReturn(ecsBlueGreenCreateServiceResponse)
        .when(ecsDeploymentHelper)
        .deployStageService(eq(createServiceLogCallback), eq(ecsCommandRequest), anyList(), anyList(), anyString(),
            anyString(), eq(null), eq(ecsLoadBalancerConfig), anyString(), anyBoolean(), anyBoolean());

    EcsBlueGreenCreateServiceResponse actualEcsBlueGreenCreateServiceResponse =
        (EcsBlueGreenCreateServiceResponse) ecsBlueGreenCreateServiceCommandTaskHandler.executeTaskInternal(
            ecsCommandRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(actualEcsBlueGreenCreateServiceResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(actualEcsBlueGreenCreateServiceResponse.getEcsBlueGreenCreateServiceResult().getRegion())
        .isEqualTo(ecsInfraConfig.getRegion());
    assertThat(actualEcsBlueGreenCreateServiceResponse.getEcsBlueGreenCreateServiceResult().getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(actualEcsBlueGreenCreateServiceResponse.getEcsBlueGreenCreateServiceResult().getListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerArn());
    assertThat(actualEcsBlueGreenCreateServiceResponse.getEcsBlueGreenCreateServiceResult().getListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerRuleArn());
  }
}

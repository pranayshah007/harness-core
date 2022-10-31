/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraType;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

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
  @Mock private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock private LogCallback createServiceLogCallback;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

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
    EcsBlueGreenCreateServiceRequest ecsBlueGreenCreateServiceRequest =
        EcsBlueGreenCreateServiceRequest.builder()
            .ecsInfraConfig(ecsInfraConfig)
            .timeoutIntervalInMin(10)
            .ecsTaskDefinitionManifestContent("taskDef")
            .ecsServiceDefinitionManifestContent("serviceDef")
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(createServiceLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder =
        RegisterTaskDefinitionRequest.builder().family("ecs").taskRoleArn("arn");
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName("ecs").cluster("cluster");
    doReturn(registerTaskDefinitionRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("taskDef", RegisterTaskDefinitionRequest.serializableBuilderClass());
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("serviceDef", CreateServiceRequest.serializableBuilderClass());
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = registerTaskDefinitionRequestBuilder.build();
    TaskDefinition taskDefinition =
        TaskDefinition.builder().taskDefinitionArn("arn").revision(1).family("family").build();
    RegisterTaskDefinitionResponse registerTaskDefinitionResponse =
        RegisterTaskDefinitionResponse.builder().taskDefinition(taskDefinition).build();
    doReturn(registerTaskDefinitionResponse)
        .when(ecsCommandTaskHelper)
        .createTaskDefinition(
            registerTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    EcsBlueGreenCreateServiceResponse ecsBlueGreenCreateServiceResponse =
        (EcsBlueGreenCreateServiceResponse) ecsBlueGreenCreateServiceCommandTaskHandler.executeTaskInternal(
            ecsBlueGreenCreateServiceRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsBlueGreenCreateServiceResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsBlueGreenCreateServiceResponse.getEcsBlueGreenCreateServiceResult().getRegion())
        .isEqualTo(ecsInfraConfig.getRegion());
    assertThat(ecsBlueGreenCreateServiceResponse.getEcsBlueGreenCreateServiceResult().getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(ecsBlueGreenCreateServiceResponse.getEcsBlueGreenCreateServiceResult().getListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerArn());
    assertThat(ecsBlueGreenCreateServiceResponse.getEcsBlueGreenCreateServiceResult().getListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerRuleArn());
  }
}

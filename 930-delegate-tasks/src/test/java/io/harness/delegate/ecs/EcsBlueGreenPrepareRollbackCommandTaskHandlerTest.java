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
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraType;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenPrepareRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenPrepareRollbackDataResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;

public class EcsBlueGreenPrepareRollbackCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private final String prodListenerArn = "prodListenerArn";
  private final String prodListenerRuleArn = "prodListenerRuleArn";
  private final String stageListenerArn = "stageListenerArn";
  private final String stageListenerRuleArn = "stageListenerArn";
  private final String loadBalancer = "loadBalancer";
  private final String cluster = "cluster";
  private final String region = "us-east-1";

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
  @Mock private LogCallback prepareRollbackLogCallback;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @Spy @InjectMocks private EcsBlueGreenPrepareRollbackCommandTaskHandler ecsBlueGreenPrepareRollbackCommandTaskHandler;

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalNotEcsBlueGreenPrepareRollbackRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsBlueGreenPrepareRollbackCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalTestIsFirstDeployment() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(prepareRollbackLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder()
                                        .region(region)
                                        .ecsInfraType(EcsInfraType.ECS)
                                        .cluster(cluster)
                                        .awsConnectorDTO(AwsConnectorDTO.builder().build())
                                        .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    EcsBlueGreenPrepareRollbackRequest ecsBlueGreenPrepareRollbackRequest =
        EcsBlueGreenPrepareRollbackRequest.builder()
            .ecsInfraConfig(ecsInfraConfig)
            .timeoutIntervalInMin(10)
            .ecsServiceDefinitionManifestContent("serviceDef")
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .build();
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName("ecs").cluster(cluster);
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject(ecsBlueGreenPrepareRollbackRequest.getEcsServiceDefinitionManifestContent(),
            CreateServiceRequest.serializableBuilderClass());

    Optional<String> optionalServiceName = Optional.of("");
    doReturn(optionalServiceName)
        .when(ecsCommandTaskHelper)
        .getBlueVersionServiceName(
            createServiceRequestBuilder.build().serviceName() + EcsCommandTaskNGHelper.DELIMITER, ecsInfraConfig);

    EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse =
        (EcsBlueGreenPrepareRollbackDataResponse) ecsBlueGreenPrepareRollbackCommandTaskHandler.executeTaskInternal(
            ecsBlueGreenPrepareRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult().isFirstDeployment())
        .isEqualTo(true);
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getEcsLoadBalancerConfig()
                   .getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getEcsLoadBalancerConfig()
                   .getProdListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerArn());
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getEcsLoadBalancerConfig()
                   .getProdListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerRuleArn());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalTestServiceNotExistsTest() throws Exception {
    String serviceName = "serviceName";
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(prepareRollbackLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder()
                                        .region(region)
                                        .ecsInfraType(EcsInfraType.ECS)
                                        .cluster(cluster)
                                        .awsConnectorDTO(AwsConnectorDTO.builder().build())
                                        .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());

    EcsBlueGreenPrepareRollbackRequest ecsBlueGreenPrepareRollbackRequest =
        EcsBlueGreenPrepareRollbackRequest.builder()
            .ecsInfraConfig(ecsInfraConfig)
            .timeoutIntervalInMin(10)
            .ecsServiceDefinitionManifestContent("serviceDef")
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .build();
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName(serviceName).cluster(cluster);
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject(ecsBlueGreenPrepareRollbackRequest.getEcsServiceDefinitionManifestContent(),
            CreateServiceRequest.serializableBuilderClass());

    Optional<String> optionalServiceName = Optional.of(serviceName);
    doReturn(optionalServiceName)
        .when(ecsCommandTaskHelper)
        .getBlueVersionServiceName(
            createServiceRequestBuilder.build().serviceName() + EcsCommandTaskNGHelper.DELIMITER, ecsInfraConfig);

    Optional<Service> optionalService = Optional.of(Service.builder().serviceName(serviceName).build());
    doReturn(optionalService)
        .when(ecsCommandTaskHelper)
        .describeService(
            ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse =
        (EcsBlueGreenPrepareRollbackDataResponse) ecsBlueGreenPrepareRollbackCommandTaskHandler.executeTaskInternal(
            ecsBlueGreenPrepareRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult().isFirstDeployment())
        .isEqualTo(true);
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getEcsLoadBalancerConfig()
                   .getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getEcsLoadBalancerConfig()
                   .getProdListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerArn());
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getEcsLoadBalancerConfig()
                   .getProdListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerRuleArn());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalNotFirstDeploymentTest() throws Exception {
    String serviceName = "serviceName";
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(prepareRollbackLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder()
                                        .region(region)
                                        .ecsInfraType(EcsInfraType.ECS)
                                        .cluster(cluster)
                                        .awsConnectorDTO(AwsConnectorDTO.builder().build())
                                        .build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    EcsBlueGreenPrepareRollbackRequest ecsBlueGreenPrepareRollbackRequest =
        EcsBlueGreenPrepareRollbackRequest.builder()
            .ecsInfraConfig(ecsInfraConfig)
            .timeoutIntervalInMin(10)
            .ecsServiceDefinitionManifestContent("serviceDef")
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .build();
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName(serviceName).cluster(cluster);
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject(ecsBlueGreenPrepareRollbackRequest.getEcsServiceDefinitionManifestContent(),
            CreateServiceRequest.serializableBuilderClass());

    Optional<String> optionalServiceName = Optional.of(serviceName);
    doReturn(optionalServiceName)
        .when(ecsCommandTaskHelper)
        .getBlueVersionServiceName(
            createServiceRequestBuilder.build().serviceName() + EcsCommandTaskNGHelper.DELIMITER, ecsInfraConfig);

    Optional<Service> optionalService = Optional.of(Service.builder().serviceName(serviceName).build());
    doReturn(optionalService)
        .when(ecsCommandTaskHelper)
        .describeService(
            ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
    doReturn(true).when(ecsCommandTaskHelper).isServiceActive(optionalService.get());

    EcsBlueGreenPrepareRollbackDataResponse ecsBlueGreenPrepareRollbackDataResponse =
        (EcsBlueGreenPrepareRollbackDataResponse) ecsBlueGreenPrepareRollbackCommandTaskHandler.executeTaskInternal(
            ecsBlueGreenPrepareRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
    Service service = optionalService.get();

    // Get createServiceRequestBuilderString from service
    String createServiceRequestBuilderString = EcsMapper.createCreateServiceRequestFromService(service);
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getCreateServiceRequestBuilderString())
        .isEqualTo(createServiceRequestBuilderString);
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult().isFirstDeployment())
        .isEqualTo(false);
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getEcsLoadBalancerConfig()
                   .getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getEcsLoadBalancerConfig()
                   .getProdListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerArn());
    assertThat(ecsBlueGreenPrepareRollbackDataResponse.getEcsBlueGreenPrepareRollbackDataResult()
                   .getEcsLoadBalancerConfig()
                   .getProdListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerRuleArn());
  }
}

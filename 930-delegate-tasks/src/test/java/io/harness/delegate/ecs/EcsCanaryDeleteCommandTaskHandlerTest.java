/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static java.lang.String.format;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraType;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCanaryDeleteRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsCanaryDeleteResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
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

public class EcsCanaryDeleteCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String serviceName = "ecs";
  private String canaryServiceName = "ecsCanary";
  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock LogCallback canaryDeleteLogCallback;

  @Spy @InjectMocks private EcsCanaryDeleteCommandTaskHandler ecsCanaryDeleteCommandTaskHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalOptionalServiceIsPresentTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().region("us-east-1").ecsInfraType(EcsInfraType.ECS).build();
    EcsCanaryDeleteRequest ecsCanaryDeleteRequest = EcsCanaryDeleteRequest.builder()
                                                        .timeoutIntervalInMin(10)
                                                        .ecsInfraConfig(ecsInfraConfig)
                                                        .ecsServiceNameSuffix("Canary")
                                                        .ecsServiceDefinitionManifestContent("serviceDef")
                                                        .build();

    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName("ecs").cluster("cluster");
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("serviceDef", CreateServiceRequest.serializableBuilderClass());
    Optional<Service> optionalService = Optional.of(Service.builder().serviceName(serviceName).build());
    doReturn(optionalService).when(ecsCommandTaskHelper).describeService(any(), any(), any(), any());
    doReturn(true).when(ecsCommandTaskHelper).isServiceActive(optionalService.get());
    doReturn(canaryDeleteLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.deleteService.toString(), true, commandUnitsProgress);
    EcsCanaryDeleteResponse ecsCanaryDeleteResponse =
        (EcsCanaryDeleteResponse) ecsCanaryDeleteCommandTaskHandler.executeTaskInternal(
            ecsCanaryDeleteRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(ecsCanaryDeleteResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsCanaryDeleteResponse.getEcsCanaryDeleteResult().getCanaryServiceName()).isEqualTo(canaryServiceName);
    assertTrue(ecsCanaryDeleteResponse.getEcsCanaryDeleteResult().isCanaryDeleted());
    verify(canaryDeleteLogCallback)
        .saveExecutionLog(
            format("Canary service %s deleted", canaryServiceName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalOptionalServiceNotPresentTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().region("us-east-1").ecsInfraType(EcsInfraType.ECS).build();
    EcsCanaryDeleteRequest ecsCanaryDeleteRequest = EcsCanaryDeleteRequest.builder()
                                                        .timeoutIntervalInMin(10)
                                                        .ecsInfraConfig(ecsInfraConfig)
                                                        .ecsServiceNameSuffix("Canary")
                                                        .ecsServiceDefinitionManifestContent("serviceDef")
                                                        .build();

    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName("ecs").cluster("cluster");
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("serviceDef", CreateServiceRequest.serializableBuilderClass());
    doReturn(canaryDeleteLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.deleteService.toString(), true, commandUnitsProgress);
    EcsCanaryDeleteResponse ecsCanaryDeleteResponse =
        (EcsCanaryDeleteResponse) ecsCanaryDeleteCommandTaskHandler.executeTaskInternal(
            ecsCanaryDeleteRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(ecsCanaryDeleteResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsCanaryDeleteResponse.getEcsCanaryDeleteResult().getCanaryServiceName()).isEqualTo(canaryServiceName);
    assertFalse(ecsCanaryDeleteResponse.getEcsCanaryDeleteResult().isCanaryDeleted());
    verify(canaryDeleteLogCallback)
        .saveExecutionLog(format("Canary service %s doesn't exist", canaryServiceName), LogLevel.INFO,
            CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalEcsRollingDeployRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsCanaryDeleteCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}

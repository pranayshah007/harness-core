/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.PRAGYESH;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsResizeStrategy;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsServiceSetupRequest;
import io.harness.delegate.task.ecs.response.EcsServiceSetupResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.List;
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

public class EcsServiceSetupTaskHandlerTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock EcsDeploymentHelper ecsDeploymentHelper;
  @Mock LogCallback logCallback;

  @Spy @InjectMocks private EcsServiceSetupTaskHandler ecsServiceSetupTaskHandler;

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    EcsServiceSetupRequest serviceSetupRequest = EcsServiceSetupRequest.builder()
                                                     .accountId("accountId")
                                                     .timeoutIntervalInMillis(10)
                                                     .infraConfig(ecsInfraConfig)
                                                     .commandType(EcsCommandTypeNG.ECS_BASIC_PREPARE_ROLLBACK)
                                                     .serviceDefinitionManifestContent("service")
                                                     .resizeStrategy(EcsResizeStrategy.RESIZE_NEW_FIRST)
                                                     .taskDefinitionManifestContent("task")
                                                     .oldServiceName("abc__1")
                                                     .newServiceName("abc__2")
                                                     .build();

    doReturn(logCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.serviceSetup.toString(), true, commandUnitsProgress);

    doReturn(CreateServiceRequest.builder().build())
        .when(ecsDeploymentHelper)
        .createServiceDefinitionRequest(any(), any(), any(), any(), any(), any(), any());

    List<Service> services = newArrayList();
    services.add(Service.builder().build());

    doReturn(services).when(ecsCommandTaskHelper).fetchServicesOfCluster(any(), any(), any());

    doReturn(services).when(ecsCommandTaskHelper).fetchServicesWithPrefix(any(), any());

    doReturn(newArrayList()).when(ecsCommandTaskHelper).fetchStaleServices(any(), any());

    doNothing().when(ecsCommandTaskHelper).createService(any(), any(), any(), anyLong());

    EcsServiceSetupResponse serviceSetupResponse =
        (EcsServiceSetupResponse) ecsServiceSetupTaskHandler.executeTaskInternal(
            serviceSetupRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(serviceSetupResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(serviceSetupResponse.getDeployData().getNewServiceData().getServiceName()).isEqualTo("abc__2");
    assertThat(serviceSetupResponse.getDeployData().getOldServiceData().getServiceName()).isEqualTo("abc__1");
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsServiceSetupTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}

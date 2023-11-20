/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBasicRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBasicRollbackResponse;
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

public class EcsBasicRollbackTaskHandlerTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String serviceName = "ecs";

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock LogCallback logCallback;

  @Spy @InjectMocks private EcsBasicRollbackTaskHandler ecsBasicRollbackTaskHandler;

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    EcsBasicRollbackRequest basicRollbackRequest = EcsBasicRollbackRequest.builder()
                                                       .accountId("accountId")
                                                       .timeoutIntervalInMillis(10)
                                                       .infraConfig(ecsInfraConfig)
                                                       .commandType(EcsCommandTypeNG.ECS_BASIC_ROLLBACK)
                                                       .createServiceRequestYaml("service")
                                                       .oldServiceName("abc__1")
                                                       .newServiceName("abc__2")
                                                       .build();

    doReturn(logCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);

    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName(serviceName).cluster("cluster");
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("service", CreateServiceRequest.serializableBuilderClass());

    doNothing()
        .when(ecsCommandTaskHelper)
        .createOrUpdateService(any(), any(), any(), any(), any(), anyLong(), anyBoolean(), anyBoolean());

    doReturn(Optional.empty()).when(ecsCommandTaskHelper).describeService(any(), any(), any(), any());

    EcsBasicRollbackResponse basicRollbackResponse =
        (EcsBasicRollbackResponse) ecsBasicRollbackTaskHandler.executeTaskInternal(
            basicRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(basicRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(basicRollbackResponse.getRollbackData().getNewServiceData().getServiceName()).isEqualTo("abc__2");
    assertThat(basicRollbackResponse.getRollbackData().getOldServiceData().getServiceName()).isEqualTo("abc__1");
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsBasicRollbackTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}

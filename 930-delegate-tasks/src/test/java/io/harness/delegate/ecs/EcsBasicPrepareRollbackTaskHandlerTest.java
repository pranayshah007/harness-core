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
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBasicPrepareRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBasicPrepareRollbackResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.List;
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

public class EcsBasicPrepareRollbackTaskHandlerTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String serviceName = "ecs";

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock LogCallback logCallback;

  @Spy @InjectMocks private EcsBasicPrepareRollbackTaskHandler ecsBasicPrepareRollbackTaskHandler;

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    EcsBasicPrepareRollbackRequest basicPrepareRollbackRequest =
        EcsBasicPrepareRollbackRequest.builder()
            .accountId("accountId")
            .timeoutIntervalInMillis(10)
            .infraConfig(ecsInfraConfig)
            .commandType(EcsCommandTypeNG.ECS_BASIC_PREPARE_ROLLBACK)
            .serviceDefinitionManifestContent("service")
            .build();

    doReturn(logCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);

    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName(serviceName).cluster("cluster");
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("service", CreateServiceRequest.serializableBuilderClass());

    List<Service> services = newArrayList();
    services.add(Service.builder().build());

    doReturn(services).when(ecsCommandTaskHelper).fetchServicesOfCluster(any(), any(), any());

    doReturn(services).when(ecsCommandTaskHelper).fetchServicesWithPrefix(any(), any());

    Optional<Service> optionalService = Optional.of(Service.builder().serviceName(serviceName).build());

    doReturn(optionalService).when(ecsCommandTaskHelper).getLatestRunningService(any());

    ecsCommandTaskHelper.fetchServicesOfCluster(
        ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion());

    doReturn(newArrayList())
        .when(ecsCommandTaskHelper)
        .getScalableTargetsAsString(
            logCallback, optionalService.get().serviceName(), optionalService.get(), ecsInfraConfig);
    doReturn(newArrayList())
        .when(ecsCommandTaskHelper)
        .getScalingPoliciesAsString(
            logCallback, optionalService.get().serviceName(), optionalService.get(), ecsInfraConfig);

    EcsBasicPrepareRollbackResponse prepareRollbackDataResponse =
        (EcsBasicPrepareRollbackResponse) ecsBasicPrepareRollbackTaskHandler.executeTaskInternal(
            basicPrepareRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(prepareRollbackDataResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(prepareRollbackDataResponse.getPrepareRollbackData().getCurrentServiceName()).isEqualTo(serviceName);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsBasicPrepareRollbackTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }
}

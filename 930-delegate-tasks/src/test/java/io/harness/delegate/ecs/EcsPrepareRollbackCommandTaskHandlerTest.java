/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
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
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsPrepareRollbackDataRequest;
import io.harness.delegate.task.ecs.response.EcsPrepareRollbackDataResponse;
import io.harness.ecs.EcsCommandUnitConstants;
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

public class EcsPrepareRollbackCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String serviceName = "ecs";

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock LogCallback prepareRollbackLogCallback;

  @Spy @InjectMocks private EcsPrepareRollbackCommandTaskHandler ecsPrepareRollbackCommandTaskHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalWithScalableTargetAndScalingPolicyTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    EcsPrepareRollbackDataRequest ecsPrepareRollbackDataRequest = EcsPrepareRollbackDataRequest.builder()
                                                                      .accountId("accountId")
                                                                      .timeoutIntervalInMin(10)
                                                                      .ecsInfraConfig(ecsInfraConfig)
                                                                      .ecsServiceDefinitionManifestContent("service")
                                                                      .build();

    doReturn(prepareRollbackLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName(serviceName).cluster("cluster");
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("service", CreateServiceRequest.serializableBuilderClass());

    Optional<Service> optionalService = Optional.of(Service.builder().serviceName(serviceName).build());
    doReturn(true).when(ecsCommandTaskHelper).isServiceActive(optionalService.get());
    doReturn(optionalService).when(ecsCommandTaskHelper).describeService(any(), any(), any(), any());
    doReturn(newArrayList())
        .when(ecsCommandTaskHelper)
        .getScalableTargetsAsString(
            prepareRollbackLogCallback, optionalService.get().serviceName(), optionalService.get(), ecsInfraConfig);
    doReturn(newArrayList())
        .when(ecsCommandTaskHelper)
        .getScalingPoliciesAsString(
            prepareRollbackLogCallback, optionalService.get().serviceName(), optionalService.get(), ecsInfraConfig);

    EcsPrepareRollbackDataResponse ecsPrepareRollbackDataResponse =
        (EcsPrepareRollbackDataResponse) ecsPrepareRollbackCommandTaskHandler.executeTaskInternal(
            ecsPrepareRollbackDataRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(ecsPrepareRollbackDataResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsPrepareRollbackDataResponse.getEcsPrepareRollbackDataResult().getServiceName())
        .isEqualTo(serviceName);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalNoOptionalServiceTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    EcsPrepareRollbackDataRequest ecsPrepareRollbackDataRequest = EcsPrepareRollbackDataRequest.builder()
                                                                      .accountId("accountId")
                                                                      .timeoutIntervalInMin(10)
                                                                      .ecsInfraConfig(ecsInfraConfig)
                                                                      .ecsServiceDefinitionManifestContent("service")
                                                                      .build();

    doReturn(prepareRollbackLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(iLogStreamingTaskClient, EcsCommandUnitConstants.prepareRollbackData.toString(), true,
            commandUnitsProgress);
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName(serviceName).cluster("cluster");
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject("service", CreateServiceRequest.serializableBuilderClass());

    EcsPrepareRollbackDataResponse ecsPrepareRollbackDataResponse =
        (EcsPrepareRollbackDataResponse) ecsPrepareRollbackCommandTaskHandler.executeTaskInternal(
            ecsPrepareRollbackDataRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsPrepareRollbackDataResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsPrepareRollbackDataResponse.getEcsPrepareRollbackDataResult().getServiceName())
        .isEqualTo(serviceName);
    assertTrue(ecsPrepareRollbackDataResponse.getEcsPrepareRollbackDataResult().isFirstDeployment());
    verify(prepareRollbackLogCallback)
        .saveExecutionLog(format("Service %s doesn't exist. Skipping Prepare Rollback Data..", serviceName),
            LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }
}

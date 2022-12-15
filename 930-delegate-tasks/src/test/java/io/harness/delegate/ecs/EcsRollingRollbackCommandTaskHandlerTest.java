/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PRAGYESH;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsRollingRollbackConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsRollingDeployRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsRollingRollbackResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.util.Arrays;
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

public class EcsRollingRollbackCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String serviceName = "ecs";

  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock EcsTaskHelperBase ecsTaskHelperBase;
  @Mock EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock LogCallback rollbackLogCallback;

  @Spy @InjectMocks private EcsRollingRollbackCommandTaskHandler ecsRollingRollbackCommandTaskHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalIsFirstDeploymentFalseTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().region("us-east-1").build();
    EcsRollingRollbackConfig ecsRollingRollbackConfig =
        EcsRollingRollbackConfig.builder()
            .isFirstDeployment(false)
            .createServiceRequestBuilderString("service")
            .registerScalableTargetRequestBuilderStrings(Arrays.asList("target"))
            .registerScalingPolicyRequestBuilderStrings(Arrays.asList("policy"))
            .build();
    EcsRollingRollbackRequest ecsRollingRollbackRequest =
        EcsRollingRollbackRequest.builder()
            .accountId("accountId")
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .ecsInfraConfig(ecsInfraConfig)
            .commandName("command")
            .ecsRollingRollbackConfig(ecsRollingRollbackConfig)
            .timeoutIntervalInMin(10)
            .build();
    doReturn(rollbackLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName(serviceName).cluster("cluster");
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject(ecsRollingRollbackConfig.getCreateServiceRequestBuilderString(),
            CreateServiceRequest.serializableBuilderClass());
    EcsRollingRollbackResponse ecsRollingRollbackResponse =
        (EcsRollingRollbackResponse) ecsRollingRollbackCommandTaskHandler.executeTaskInternal(
            ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
    assertThat(ecsRollingRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsRollingRollbackResponse.getEcsRollingRollbackResult().getRegion()).isEqualTo("us-east-1");
    verify(rollbackLogCallback)
        .saveExecutionLog(color(format("%n Rollback Successful."), LogColor.Green, LogWeight.Bold), LogLevel.INFO,
            CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalIsFirstDeploymentTrueTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().region("us-east-1").build();
    EcsRollingRollbackConfig ecsRollingRollbackConfig =
        EcsRollingRollbackConfig.builder()
            .isFirstDeployment(true)
            .createServiceRequestBuilderString("service")
            .registerScalableTargetRequestBuilderStrings(Arrays.asList("target"))
            .registerScalingPolicyRequestBuilderStrings(Arrays.asList("policy"))
            .build();
    EcsRollingRollbackRequest ecsRollingRollbackRequest =
        EcsRollingRollbackRequest.builder()
            .accountId("accountId")
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .ecsInfraConfig(ecsInfraConfig)
            .commandName("command")
            .ecsRollingRollbackConfig(ecsRollingRollbackConfig)
            .timeoutIntervalInMin(10)
            .build();
    doReturn(rollbackLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName("ecs").cluster("cluster");
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject(ecsRollingRollbackConfig.getCreateServiceRequestBuilderString(),
            CreateServiceRequest.serializableBuilderClass());

    EcsRollingRollbackResponse ecsRollingRollbackResponse =
        (EcsRollingRollbackResponse) ecsRollingRollbackCommandTaskHandler.executeTaskInternal(
            ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsRollingRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsRollingRollbackResponse.getEcsRollingRollbackResult().getRegion()).isEqualTo("us-east-1");
    verify(rollbackLogCallback)
        .saveExecutionLog(color(format("%n Rollback Successful."), LogColor.Green, LogWeight.Bold), LogLevel.INFO,
            CommandExecutionStatus.SUCCESS);
    rollbackLogCallback.saveExecutionLog(format("Deleted service %s", serviceName), LogLevel.INFO);
    rollbackLogCallback.saveExecutionLog(format("Deleted service %s", serviceName), LogLevel.INFO);
  }

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalExceptionTest() throws Exception {
    EcsRollingDeployRequest ecsRollingDeployRequest = EcsRollingDeployRequest.builder().build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    ecsRollingRollbackCommandTaskHandler.executeTaskInternal(
        ecsRollingDeployRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeRollbackTaskForMaxDesiredCountTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    EcsRollingRollbackConfig ecsRollingRollbackConfig = EcsRollingRollbackConfig.builder().build();
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder()
                                                              .ecsInfraConfig(EcsInfraConfig.builder().build())
                                                              .ecsRollingRollbackConfig(ecsRollingRollbackConfig)
                                                              .timeoutIntervalInMin(10)
                                                              .build();
    CreateServiceRequest.Builder createServiceRequestBuilder = CreateServiceRequest.builder().desiredCount(8);
    Optional<Service> optionalService = Optional.of(Service.builder().desiredCount(10).build());

    doReturn(rollbackLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskHelper)
        .parseYamlAsObject(ecsRollingRollbackConfig.getCreateServiceRequestBuilderString(),
            CreateServiceRequest.serializableBuilderClass());
    doReturn(true).when(ecsCommandTaskHelper).isServiceActive(optionalService.get());
    doReturn(optionalService)
        .when(ecsCommandTaskHelper)
        .describeService(ecsRollingRollbackRequest.getEcsInfraConfig().getCluster(),
            createServiceRequestBuilder.build().serviceName(),
            ecsRollingRollbackRequest.getEcsInfraConfig().getRegion(),
            ecsRollingRollbackRequest.getEcsInfraConfig().getAwsConnectorDTO());
    ecsRollingRollbackCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);

    verify(ecsCommandTaskHelper)
        .createOrUpdateService(CreateServiceRequest.builder().desiredCount(10).build(), null, null,
            EcsInfraConfig.builder().build(), rollbackLogCallback, 600000L, false, false);
  }
}

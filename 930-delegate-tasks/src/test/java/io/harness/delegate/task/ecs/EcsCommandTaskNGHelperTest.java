/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
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
import software.amazon.awssdk.core.internal.waiters.DefaultWaiterResponse;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalingPolicy;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DeleteServiceRequest;
import software.amazon.awssdk.services.ecs.model.DeleteServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceEvent;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

public class EcsCommandTaskNGHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String region = "us-east-1";
  private String serviceName = "name";
  private String cluster = "cluster";
  private int serviceSteadyStateTimeout = 10;
  private AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();

  @Mock EcsV2Client ecsV2Client;
  @Mock AwsNgConfigMapper awsNgConfigMapper;
  @Mock LogCallback logCallback;
  @Spy @InjectMocks private EcsCommandTaskNGHelper ecsCommandTaskNGHelper;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createTaskDefinitionTest() {
    RegisterTaskDefinitionResponse registerTaskDefinitionResponse = RegisterTaskDefinitionResponse.builder().build();
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = RegisterTaskDefinitionRequest.builder().build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(registerTaskDefinitionResponse)
        .when(ecsV2Client)
        .createTask(awsInternalConfig, registerTaskDefinitionRequest, region);
    RegisterTaskDefinitionResponse registerTaskDefinitionResponseTest =
        ecsCommandTaskNGHelper.createTaskDefinition(registerTaskDefinitionRequest, region, awsConnectorDTO);
    assertThat(registerTaskDefinitionResponseTest).isEqualTo(registerTaskDefinitionResponse);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createServiceTest() {
    CreateServiceResponse createServiceResponse = CreateServiceResponse.builder().build();
    CreateServiceRequest createServiceRequest = CreateServiceRequest.builder().build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(createServiceResponse).when(ecsV2Client).createService(awsInternalConfig, createServiceRequest, region);
    CreateServiceResponse createServiceResponseTest =
        ecsCommandTaskNGHelper.createService(createServiceRequest, region, awsConnectorDTO);
    assertThat(createServiceResponseTest).isEqualTo(createServiceResponse);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void updateServiceTest() {
    UpdateServiceResponse updateServiceResponse = UpdateServiceResponse.builder().build();
    UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.builder().build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(updateServiceResponse).when(ecsV2Client).updateService(awsInternalConfig, updateServiceRequest, region);
    UpdateServiceResponse updateServiceResponseTest =
        ecsCommandTaskNGHelper.updateService(updateServiceRequest, region, awsConnectorDTO);
    assertThat(updateServiceResponseTest).isEqualTo(updateServiceResponse);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void deleteServiceTest() {
    DeleteServiceResponse deleteServiceResponse = DeleteServiceResponse.builder().build();
    DeleteServiceRequest deleteServiceRequest =
        DeleteServiceRequest.builder().service(serviceName).cluster(cluster).force(true).build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(deleteServiceResponse).when(ecsV2Client).deleteService(awsInternalConfig, deleteServiceRequest, region);
    DeleteServiceResponse deleteServiceResponseTest =
        ecsCommandTaskNGHelper.deleteService(serviceName, cluster, region, awsConnectorDTO);
    assertThat(deleteServiceResponseTest).isEqualTo(deleteServiceResponse);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void describeServiceTest() {
    DescribeServicesResponse describeServicesResponse =
        DescribeServicesResponse.builder().services(Arrays.asList(Service.builder().build())).build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(describeServicesResponse)
        .when(ecsV2Client)
        .describeService(awsInternalConfig, cluster, serviceName, region);
    Optional<Service> describeServiceResponseTest =
        ecsCommandTaskNGHelper.describeService(cluster, serviceName, region, awsConnectorDTO);
    assertThat(describeServiceResponseTest).isEqualTo(Optional.of(describeServicesResponse.services().get(0)));
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void ecsServiceSteadyStateCheckTest() {
    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(cluster).build();
    DescribeServicesResponse describeServicesResponse =
        DescribeServicesResponse.builder().services(Arrays.asList(Service.builder().build())).build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(DefaultWaiterResponse.builder().response(describeServicesResponse).attemptsExecuted(1).build())
        .when(ecsV2Client)
        .ecsServiceSteadyStateCheck(awsInternalConfig, describeServicesRequest, region, serviceSteadyStateTimeout);
    List<ServiceEvent> eventsAlreadyProcessed = Arrays.asList(ServiceEvent.builder().build());
    doReturn(Optional.of(describeServicesResponse.services().get(0)))
        .when(ecsCommandTaskNGHelper)
        .describeService(cluster, serviceName, region, awsConnectorDTO);
    WaiterResponse<DescribeServicesResponse> describeServicesResponseWaiterResponse =
        ecsCommandTaskNGHelper.ecsServiceSteadyStateCheck(logCallback, awsConnectorDTO, cluster, serviceName, region,
            serviceSteadyStateTimeout, eventsAlreadyProcessed);
    assertThat(describeServicesResponseWaiterResponse)
        .isEqualTo(DefaultWaiterResponse.builder().response(describeServicesResponse).attemptsExecuted(1).build());
    verify(logCallback).saveExecutionLog(format("Service %s reached steady state %n", serviceName), LogLevel.INFO);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void ecsServiceSteadyStateCheckExceptionTest() {
    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(cluster).build();
    DescribeServicesResponse describeServicesResponse = DescribeServicesResponse.builder().build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(DefaultWaiterResponse.builder()
                 .response(describeServicesResponse)
                 .attemptsExecuted(1)
                 .exception(new Throwable("error"))
                 .build())
        .when(ecsV2Client)
        .ecsServiceSteadyStateCheck(awsInternalConfig, describeServicesRequest, region, serviceSteadyStateTimeout);
    List<ServiceEvent> eventsAlreadyProcessed = Arrays.asList(ServiceEvent.builder().build());
    ecsCommandTaskNGHelper.ecsServiceSteadyStateCheck(
        logCallback, awsConnectorDTO, cluster, serviceName, region, serviceSteadyStateTimeout, eventsAlreadyProcessed);
    verify(logCallback)
        .saveExecutionLog(format("Service %s failed to reach steady state %n", serviceName), LogLevel.ERROR);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void ecsServiceInactiveStateCheckTest() {
    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(cluster).build();
    DescribeServicesResponse describeServicesResponse = DescribeServicesResponse.builder().build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(DefaultWaiterResponse.builder().response(describeServicesResponse).attemptsExecuted(1).build())
        .when(ecsV2Client)
        .ecsServiceInactiveStateCheck(awsInternalConfig, describeServicesRequest, region, serviceSteadyStateTimeout);
    WaiterResponse<DescribeServicesResponse> describeServicesResponseWaiterResponse =
        ecsCommandTaskNGHelper.ecsServiceInactiveStateCheck(
            logCallback, awsConnectorDTO, cluster, serviceName, region, serviceSteadyStateTimeout);
    assertThat(describeServicesResponseWaiterResponse)
        .isEqualTo(DefaultWaiterResponse.builder().response(describeServicesResponse).attemptsExecuted(1).build());
    verify(logCallback)
        .saveExecutionLog(format("Existing Service %s reached inactive state %n", serviceName), LogLevel.INFO);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void ecsServiceInactiveStateCheckExceptionTest() {
    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(cluster).build();
    DescribeServicesResponse describeServicesResponse = DescribeServicesResponse.builder().build();
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(DefaultWaiterResponse.builder()
                 .response(describeServicesResponse)
                 .attemptsExecuted(1)
                 .exception(new Throwable("error"))
                 .build())
        .when(ecsV2Client)
        .ecsServiceInactiveStateCheck(awsInternalConfig, describeServicesRequest, region, serviceSteadyStateTimeout);
    ecsCommandTaskNGHelper.ecsServiceInactiveStateCheck(
        logCallback, awsConnectorDTO, cluster, serviceName, region, serviceSteadyStateTimeout);
    verify(logCallback)
        .saveExecutionLog(format("Existing Service %s reached inactive state %n", serviceName), LogLevel.INFO);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void listScalableTargetsTest() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    DescribeScalableTargetsRequest describeScalableTargetsRequest =
        DescribeScalableTargetsRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceIds(Collections.singletonList(format("service/%s/%s", cluster, serviceName)))
            .build();

    doReturn(DescribeScalableTargetsResponse.builder().build())
        .when(ecsV2Client)
        .listScalableTargets(awsInternalConfig, describeScalableTargetsRequest, region);
    DescribeScalableTargetsResponse describeScalableTargetsResponse =
        ecsCommandTaskNGHelper.listScalableTargets(awsConnectorDTO, cluster, serviceName, region);
    assertThat(describeScalableTargetsResponse).isEqualTo(DescribeScalableTargetsResponse.builder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void listScalingPoliciesTest() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    DescribeScalingPoliciesRequest describeScalingPoliciesRequest =
        DescribeScalingPoliciesRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .build();

    doReturn(DescribeScalingPoliciesResponse.builder().build())
        .when(ecsV2Client)
        .listScalingPolicies(awsInternalConfig, describeScalingPoliciesRequest, region);
    DescribeScalingPoliciesResponse describeScalingPoliciesResponse =
        ecsCommandTaskNGHelper.listScalingPolicies(awsConnectorDTO, cluster, serviceName, region);
    assertThat(describeScalingPoliciesResponse).isEqualTo(DescribeScalingPoliciesResponse.builder().build());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void deleteScalingPoliciesTest() {
    DescribeScalingPoliciesRequest describeScalingPoliciesRequest =
        DescribeScalingPoliciesRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .build();

    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    DescribeScalingPoliciesResponse describeScalingPoliciesResponse =
        DescribeScalingPoliciesResponse.builder().scalingPolicies(ScalingPolicy.builder().build()).build();
    doReturn(describeScalingPoliciesResponse)
        .when(ecsV2Client)
        .listScalingPolicies(awsInternalConfig, describeScalingPoliciesRequest, region);

    ecsCommandTaskNGHelper.deleteScalingPolicies(awsConnectorDTO, serviceName, cluster, region, logCallback);
    verify(logCallback)
        .saveExecutionLog(format("Deleted Scaling Policies from service %s %n", serviceName), LogLevel.INFO);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void deleteScalingPoliciesDescribeScalingPoliciesResponseNullTest() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    ecsCommandTaskNGHelper.deleteScalingPolicies(awsConnectorDTO, serviceName, cluster, region, logCallback);
    verify(logCallback)
        .saveExecutionLog(
            format("Didn't find any Scaling Policies attached to service %s %n", serviceName), LogLevel.INFO);
  }
}

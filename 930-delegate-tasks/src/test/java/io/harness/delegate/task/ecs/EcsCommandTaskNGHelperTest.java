/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.SAINATH;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.EcsV2Client;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.ecs.EcsMapper;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.exception.HintException;
import io.harness.exception.TimeoutException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.core.internal.waiters.DefaultWaiterResponse;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableDimension;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableTarget;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalingPolicy;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.ecs.model.Container;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DeleteServiceRequest;
import software.amazon.awssdk.services.ecs.model.DeleteServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.DesiredStatus;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceEvent;
import software.amazon.awssdk.services.ecs.model.Task;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule;

public class EcsCommandTaskNGHelperTest extends CategoryTest {
  @org.junit.Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String region = "us-east-1";
  private final String serviceName = "name";
  private final String cluster = "cluster";
  private final int serviceSteadyStateTimeout = 10;
  private final long timeoutInMillis = 100000L;
  private final AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();
  private final String DELIMITER = "__";

  private final String prodListenerArn = "prodListenerArn";
  private final String prodListenerRuleArn = "prodListenerRuleArn";
  private final String stageListenerArn = "stageListenerArn";
  private final String stageListenerRuleArn = "stageListenerArn";
  private final String loadBalancer = "loadBalancer";
  private final String targetGroupArn = "groupArn";
  private final String loadBalancerArn = "loadBalancerArn";

  private final EcsInfraConfig ecsInfraConfig =
      EcsInfraConfig.builder().awsConnectorDTO(awsConnectorDTO).region(region).cluster(cluster).build();
  private final AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
  private final EcsLoadBalancerConfig ecsLoadBalancerConfig = EcsLoadBalancerConfig.builder()
                                                                  .loadBalancer(loadBalancer)
                                                                  .prodListenerArn(prodListenerArn)
                                                                  .prodListenerRuleArn(prodListenerRuleArn)
                                                                  .prodTargetGroupArn(targetGroupArn)
                                                                  .stageListenerArn(stageListenerArn)
                                                                  .stageListenerRuleArn(stageListenerRuleArn)
                                                                  .stageTargetGroupArn(targetGroupArn)
                                                                  .build();

  @Mock private EcsV2Client ecsV2Client;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private LogCallback logCallback;
  @Mock private LogCallback prepareRollbackDataLogCallback;
  @Mock private TimeLimiter timeLimiter;
  @Mock private ElbV2Client elbV2Client;

  @Spy @InjectMocks private EcsCommandTaskNGHelper ecsCommandTaskNGHelper;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createTaskDefinitionTest() {
    RegisterTaskDefinitionResponse registerTaskDefinitionResponse = RegisterTaskDefinitionResponse.builder().build();
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = RegisterTaskDefinitionRequest.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(registerTaskDefinitionResponse)
        .when(ecsV2Client)
        .createTask(awsInternalConfig, registerTaskDefinitionRequest, region);

    ecsCommandTaskNGHelper.createTaskDefinition(registerTaskDefinitionRequest, region, awsConnectorDTO);

    verify(ecsV2Client)
        .createTask(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), registerTaskDefinitionRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createServiceTest() {
    CreateServiceResponse createServiceResponse = CreateServiceResponse.builder().build();
    CreateServiceRequest createServiceRequest = CreateServiceRequest.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(createServiceResponse).when(ecsV2Client).createService(awsInternalConfig, createServiceRequest, region);
    ecsCommandTaskNGHelper.createService(createServiceRequest, region, awsConnectorDTO);

    ecsV2Client.createService(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), createServiceRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void updateServiceTest() {
    UpdateServiceResponse updateServiceResponse = UpdateServiceResponse.builder().build();
    UpdateServiceRequest updateServiceRequest = UpdateServiceRequest.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(updateServiceResponse).when(ecsV2Client).updateService(awsInternalConfig, updateServiceRequest, region);

    ecsCommandTaskNGHelper.updateService(updateServiceRequest, region, awsConnectorDTO);

    verify(ecsV2Client)
        .updateService(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), updateServiceRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void deleteServiceTest() {
    DeleteServiceResponse deleteServiceResponse = DeleteServiceResponse.builder().build();
    DeleteServiceRequest deleteServiceRequest =
        DeleteServiceRequest.builder().service(serviceName).cluster(cluster).force(true).build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(deleteServiceResponse).when(ecsV2Client).deleteService(awsInternalConfig, deleteServiceRequest, region);

    ecsCommandTaskNGHelper.deleteService(serviceName, cluster, region, awsConnectorDTO);

    verify(ecsV2Client)
        .deleteService(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), deleteServiceRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void describeServiceTest() {
    DescribeServicesResponse describeServicesResponse =
        DescribeServicesResponse.builder().services(Arrays.asList(Service.builder().build())).build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(describeServicesResponse)
        .when(ecsV2Client)
        .describeService(awsInternalConfig, cluster, serviceName, region);
    Optional<Service> describeServiceResponseTest =
        ecsCommandTaskNGHelper.describeService(cluster, serviceName, region, awsConnectorDTO);

    assertThat(describeServiceResponseTest).isEqualTo(Optional.of(describeServicesResponse.services().get(0)));
    verify(ecsV2Client).describeService(awsInternalConfig, cluster, serviceName, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void ecsServiceInactiveStateCheckTest() {
    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(cluster).build();
    DescribeServicesResponse describeServicesResponse = DescribeServicesResponse.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(DefaultWaiterResponse.builder().response(describeServicesResponse).attemptsExecuted(1).build())
        .when(ecsV2Client)
        .ecsServiceInactiveStateCheck(awsInternalConfig, describeServicesRequest, region, serviceSteadyStateTimeout);

    WaiterResponse<DescribeServicesResponse> describeServicesResponseWaiterResponse =
        ecsCommandTaskNGHelper.ecsServiceInactiveStateCheck(
            logCallback, awsConnectorDTO, cluster, serviceName, region, serviceSteadyStateTimeout);
    assertThat(describeServicesResponseWaiterResponse)
        .isEqualTo(DefaultWaiterResponse.builder().response(describeServicesResponse).attemptsExecuted(1).build());

    verify(ecsV2Client)
        .ecsServiceInactiveStateCheck(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO),
            describeServicesRequest, region, serviceSteadyStateTimeout);
  }

  @Test(expected = RuntimeException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void ecsServiceInactiveStateCheckExceptionTest() {
    DescribeServicesRequest describeServicesRequest =
        DescribeServicesRequest.builder().services(Collections.singletonList(serviceName)).cluster(cluster).build();
    DescribeServicesResponse describeServicesResponse = DescribeServicesResponse.builder().build();
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

    verify(ecsV2Client)
        .ecsServiceInactiveStateCheck(awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO),
            describeServicesRequest, region, serviceSteadyStateTimeout);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void listScalableTargetsTest() {
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

    ecsCommandTaskNGHelper.listScalableTargets(awsConnectorDTO, cluster, serviceName, region);

    verify(ecsV2Client)
        .listScalableTargets(
            awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), describeScalableTargetsRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void listScalingPoliciesTest() {
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    DescribeScalingPoliciesRequest describeScalingPoliciesRequest =
        DescribeScalingPoliciesRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .build();

    ecsCommandTaskNGHelper.listScalingPolicies(awsConnectorDTO, cluster, serviceName, region);

    verify(ecsV2Client)
        .listScalingPolicies(
            awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), describeScalingPoliciesRequest, region);
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

    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    ScalingPolicy scalingPolicy = ScalingPolicy.builder().policyName("P1").scalableDimension("1").build();
    DescribeScalingPoliciesResponse describeScalingPoliciesResponse =
        DescribeScalingPoliciesResponse.builder().scalingPolicies(scalingPolicy).build();
    doReturn(describeScalingPoliciesResponse)
        .when(ecsV2Client)
        .listScalingPolicies(awsInternalConfig, describeScalingPoliciesRequest, region);

    ecsCommandTaskNGHelper.deleteScalingPolicies(awsConnectorDTO, serviceName, cluster, region, logCallback);

    DeleteScalingPolicyRequest deleteScalingPolicyRequest =
        DeleteScalingPolicyRequest.builder()
            .policyName(scalingPolicy.policyName())
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .scalableDimension(scalingPolicy.scalableDimension())
            .serviceNamespace(scalingPolicy.serviceNamespace())
            .build();
    ecsV2Client.deleteScalingPolicy(awsInternalConfig, deleteScalingPolicyRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void deleteScalingPoliciesDescribeScalingPoliciesResponseNullTest() {
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    ecsCommandTaskNGHelper.deleteScalingPolicies(awsConnectorDTO, serviceName, cluster, region, logCallback);

    DescribeScalingPoliciesRequest describeScalingPoliciesRequest =
        DescribeScalingPoliciesRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .build();
    verify(ecsV2Client).listScalingPolicies(awsInternalConfig, describeScalingPoliciesRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getRunningEcsTasksTest() {
    ListTasksRequest.Builder listTasksRequestBuilder =
        ListTasksRequest.builder().cluster(cluster).serviceName(serviceName).desiredStatus(DesiredStatus.RUNNING);
    ListTasksResponse listTasksResponse = ListTasksResponse.builder().taskArns("arn").build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(listTasksResponse)
        .when(ecsV2Client)
        .listTaskArns(awsInternalConfig, listTasksRequestBuilder.build(), region);
    Task task = Task.builder()
                    .clusterArn("arn")
                    .launchType("FARGATE")
                    .taskDefinitionArn("arn")
                    .startedAt(Instant.ofEpochSecond(10))
                    .startedBy("7")
                    .version(20L)
                    .build();
    DescribeTasksResponse describeTasksResponse = DescribeTasksResponse.builder().tasks(Arrays.asList(task)).build();
    doReturn(describeTasksResponse)
        .when(ecsV2Client)
        .getTasks(awsInternalConfig, cluster, listTasksResponse.taskArns(), region);
    EcsTask ecsTask = ecsCommandTaskNGHelper.getRunningEcsTasks(awsConnectorDTO, cluster, serviceName, region).get(0);

    verify(ecsV2Client)
        .listTaskArns(
            awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), listTasksRequestBuilder.build(), region);
    verify(ecsV2Client)
        .getTasks(
            awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO), cluster, listTasksResponse.taskArns(), region);

    assertThat(ecsTask.getClusterArn()).isEqualTo(task.clusterArn());
    assertThat(ecsTask.getTaskDefinitionArn()).isEqualTo(task.taskDefinitionArn());
    assertThat(ecsTask.getLaunchType()).isEqualTo(task.launchTypeAsString());
    assertThat(ecsTask.getStartedAt()).isEqualTo(task.startedAt().getEpochSecond());
    assertThat(ecsTask.getStartedBy()).isEqualTo(task.startedBy());
    assertThat(ecsTask.getVersion()).isEqualTo(task.version());
    assertThat(ecsTask.getServiceName()).isEqualTo(listTasksRequestBuilder.build().serviceName());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void deregisterScalableTargetsTest() {
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    DescribeScalableTargetsRequest describeScalableTargetsRequest =
        DescribeScalableTargetsRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceIds(format("service/%s/%s", cluster, serviceName))
            .build();
    ScalableTarget scalableTarget = ScalableTarget.builder()
                                        .serviceNamespace(serviceName)
                                        .scalableDimension(ScalableDimension.ELASTICACHE_REPLICATION_GROUP_REPLICAS)
                                        .build();
    DescribeScalableTargetsResponse describeScalableTargetsResponse =
        DescribeScalableTargetsResponse.builder().scalableTargets(scalableTarget).build();
    doReturn(describeScalableTargetsResponse)
        .when(ecsV2Client)
        .listScalableTargets(awsInternalConfig, describeScalableTargetsRequest, region);

    ecsCommandTaskNGHelper.deregisterScalableTargets(awsConnectorDTO, serviceName, cluster, region, logCallback);

    DeregisterScalableTargetRequest deregisterScalableTargetRequest =
        DeregisterScalableTargetRequest.builder()
            .scalableDimension(scalableTarget.scalableDimension())
            .serviceNamespace(scalableTarget.serviceNamespace())
            .resourceId(format("service/%s/%s", cluster, serviceName))
            .build();

    verify(ecsV2Client).deregisterScalableTarget(awsInternalConfig, deregisterScalableTargetRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void deregisterScalableTargetDescribeScalableTargetsResponseNullTest() {
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    ecsCommandTaskNGHelper.deregisterScalableTargets(awsConnectorDTO, serviceName, cluster, region, logCallback);

    DescribeScalableTargetsRequest describeScalableTargetsRequest =
        DescribeScalableTargetsRequest.builder()
            .maxResults(100)
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceIds(format("service/%s/%s", cluster, serviceName))
            .build();
    verify(ecsV2Client).listScalableTargets(awsInternalConfig, describeScalableTargetsRequest, region);
  }

  @Test(expected = HintException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void attachScalingPoliciesParseYamlAsObjectExceptionTest() {
    List<String> ecsScalingPolicyManifestContentList = Arrays.asList("content");
    ecsCommandTaskNGHelper.attachScalingPolicies(
        ecsScalingPolicyManifestContentList, awsConnectorDTO, serviceName, cluster, region, logCallback);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void attachScalingPoliciesTest() {
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    List<String> ecsScalingPolicyManifestContentList = Arrays.asList("content");
    PutScalingPolicyRequest.Builder putScalingPolicyRequestBuilder = PutScalingPolicyRequest.builder().policyName("P1");
    doReturn(putScalingPolicyRequestBuilder)
        .when(ecsCommandTaskNGHelper)
        .parseYamlAsObject(
            ecsScalingPolicyManifestContentList.get(0), PutScalingPolicyRequest.serializableBuilderClass());
    ecsCommandTaskNGHelper.attachScalingPolicies(
        ecsScalingPolicyManifestContentList, awsConnectorDTO, serviceName, cluster, region, logCallback);

    PutScalingPolicyRequest putScalingPolicyRequest =
        putScalingPolicyRequestBuilder.resourceId(format("service/%s/%s", cluster, serviceName)).build();

    verify(ecsV2Client).attachScalingPolicy(awsInternalConfig, putScalingPolicyRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void registerScalableTargetsTest() {
    List<String> ecsScalableTargetManifestContentList = Arrays.asList("content");

    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    RegisterScalableTargetRequest.Builder registerScalableTargetRequestBuilder =
        RegisterScalableTargetRequest.builder();
    doReturn(registerScalableTargetRequestBuilder)
        .when(ecsCommandTaskNGHelper)
        .parseYamlAsObject(
            ecsScalableTargetManifestContentList.get(0), RegisterScalableTargetRequest.serializableBuilderClass());

    ecsCommandTaskNGHelper.registerScalableTargets(
        ecsScalableTargetManifestContentList, awsConnectorDTO, serviceName, cluster, region, logCallback);

    RegisterScalableTargetRequest registerScalableTargetRequest =
        registerScalableTargetRequestBuilder.resourceId(format("service/%s/%s", cluster, serviceName)).build();
    verify(ecsV2Client).registerScalableTarget(awsInternalConfig, registerScalableTargetRequest, region);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createOrUpdateServiceNotEmptyServiceTest() {
    CreateServiceRequest createServiceRequest =
        CreateServiceRequest.builder().serviceName(serviceName).cluster(cluster).build();
    List<String> ecsScalableTargetManifestContentList = Arrays.asList("content");
    List<String> ecsScalingPolicyManifestContentList = Arrays.asList("content");

    Service service = Service.builder().status("Running").build();
    DescribeServicesResponse describeServicesResponse =
        DescribeServicesResponse.builder().services(Arrays.asList(service)).build();
    doReturn(Optional.of(describeServicesResponse.services().get(0)))
        .when(ecsCommandTaskNGHelper)
        .describeService(cluster, serviceName, region, awsConnectorDTO);

    CreateServiceResponse createServiceResponse = CreateServiceResponse.builder().service(service).build();
    doReturn(createServiceResponse)
        .when(ecsCommandTaskNGHelper)
        .createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);
    ecsCommandTaskNGHelper.createOrUpdateService(createServiceRequest, ecsScalableTargetManifestContentList,
        ecsScalingPolicyManifestContentList, ecsInfraConfig, logCallback, timeoutInMillis, true, true);

    verify(ecsCommandTaskNGHelper)
        .createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    verify(ecsCommandTaskNGHelper)
        .ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);
    verify(ecsCommandTaskNGHelper)
        .registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);

    verify(ecsCommandTaskNGHelper)
        .attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createOrUpdateServiceNotEmptyServiceActiveTest() {
    boolean forceNewDeployment = true;
    CreateServiceRequest createServiceRequest =
        CreateServiceRequest.builder().serviceName(serviceName).cluster(cluster).build();
    List<String> ecsScalableTargetManifestContentList = Arrays.asList("content");
    List<String> ecsScalingPolicyManifestContentList = Arrays.asList("content");

    Service service = Service.builder().status("ACTIVE").build();
    DescribeServicesResponse describeServicesResponse =
        DescribeServicesResponse.builder().services(Arrays.asList(service)).build();
    doReturn(Optional.of(describeServicesResponse.services().get(0)))
        .when(ecsCommandTaskNGHelper)
        .describeService(cluster, serviceName, region, awsConnectorDTO);

    CreateServiceResponse createServiceResponse = CreateServiceResponse.builder().service(service).build();
    doReturn(createServiceResponse)
        .when(ecsCommandTaskNGHelper)
        .createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
            ecsInfraConfig.getRegion(), logCallback);
    doNothing()
        .when(ecsCommandTaskNGHelper)
        .deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(),
            ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

    UpdateServiceRequest updateServiceRequest =
        EcsMapper.createServiceRequestToUpdateServiceRequest(createServiceRequest, forceNewDeployment);
    UpdateServiceResponse updateServiceResponse = UpdateServiceResponse.builder().service(service).build();
    doReturn(updateServiceResponse)
        .when(ecsCommandTaskNGHelper)
        .updateService(updateServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(updateServiceResponse.service().events());

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);
    doNothing()
        .when(ecsCommandTaskNGHelper)
        .registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    ecsCommandTaskNGHelper.createOrUpdateService(createServiceRequest, ecsScalableTargetManifestContentList,
        ecsScalingPolicyManifestContentList, ecsInfraConfig, logCallback, timeoutInMillis, true, forceNewDeployment);

    verify(ecsCommandTaskNGHelper)
        .deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
            ecsInfraConfig.getRegion(), logCallback);
    verify(ecsCommandTaskNGHelper)
        .deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(),
            ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    verify(ecsCommandTaskNGHelper)
        .updateService(updateServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    verify(ecsCommandTaskNGHelper)
        .ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);
    verify(ecsCommandTaskNGHelper)
        .registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    verify(ecsCommandTaskNGHelper)
        .attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createOrUpdateServiceNotEmptyServiceActiveSkipUpdateServiceTest() {
    boolean forceNewDeployment = false;
    String taskArn = "taskArn";
    CreateServiceRequest createServiceRequest = CreateServiceRequest.builder()
                                                    .serviceName(serviceName)
                                                    .taskDefinition(taskArn)
                                                    .desiredCount(1)
                                                    .cluster(cluster)
                                                    .build();
    List<String> ecsScalableTargetManifestContentList = Arrays.asList("content");
    List<String> ecsScalingPolicyManifestContentList = Arrays.asList("content");

    Service service =
        Service.builder().status("ACTIVE").serviceName(serviceName).desiredCount(1).taskDefinition(taskArn).build();
    DescribeServicesResponse describeServicesResponse =
        DescribeServicesResponse.builder().services(Arrays.asList(service)).build();
    doReturn(Optional.of(describeServicesResponse.services().get(0)))
        .when(ecsCommandTaskNGHelper)
        .describeService(cluster, serviceName, region, awsConnectorDTO);

    CreateServiceResponse createServiceResponse = CreateServiceResponse.builder().service(service).build();
    doReturn(createServiceResponse)
        .when(ecsCommandTaskNGHelper)
        .createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
            ecsInfraConfig.getRegion(), logCallback);
    doNothing()
        .when(ecsCommandTaskNGHelper)
        .deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(),
            ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

    UpdateServiceRequest updateServiceRequest =
        EcsMapper.createServiceRequestToUpdateServiceRequest(createServiceRequest, forceNewDeployment);
    UpdateServiceResponse updateServiceResponse = UpdateServiceResponse.builder().service(service).build();
    doReturn(updateServiceResponse)
        .when(ecsCommandTaskNGHelper)
        .updateService(updateServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    ecsCommandTaskNGHelper.createOrUpdateService(createServiceRequest, ecsScalableTargetManifestContentList,
        ecsScalingPolicyManifestContentList, ecsInfraConfig, logCallback, timeoutInMillis, true, forceNewDeployment);

    verify(ecsCommandTaskNGHelper)
        .deleteScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(), ecsInfraConfig.getCluster(),
            ecsInfraConfig.getRegion(), logCallback);
    verify(ecsCommandTaskNGHelper)
        .deregisterScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), service.serviceName(),
            ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    verify(ecsCommandTaskNGHelper)
        .registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);
    verify(ecsCommandTaskNGHelper)
        .attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            service.serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(), logCallback);

    verify(logCallback)
        .saveExecutionLog(color(format("Service %s is already up to date", serviceName), White, Bold), LogLevel.INFO);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createCanaryServiceTest() {
    CreateServiceRequest createServiceRequest = CreateServiceRequest.builder()
                                                    .serviceName(serviceName)
                                                    .taskDefinition("taskDef")
                                                    .desiredCount(1)
                                                    .cluster(cluster)
                                                    .build();
    List<String> ecsScalableTargetManifestContentList = Arrays.asList("content");
    List<String> ecsScalingPolicyManifestContentList = Arrays.asList("content");
    Service service =
        Service.builder().status("ACTIVE").clusterArn("arn").serviceArn("arn").serviceName(serviceName).build();
    DescribeServicesResponse describeServicesResponse =
        DescribeServicesResponse.builder().services(Arrays.asList(service)).build();
    doReturn(Optional.of(describeServicesResponse.services().get(0)))
        .when(ecsCommandTaskNGHelper)
        .describeService(cluster, serviceName, region, awsConnectorDTO);
    DeleteServiceResponse deleteServiceResponse = DeleteServiceResponse.builder().build();
    doReturn(deleteServiceResponse)
        .when(ecsCommandTaskNGHelper)
        .deleteService(service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(),
            ecsInfraConfig.getAwsConnectorDTO());

    WaiterResponse<Object> describeServicesResponseWaiterResponse =
        DefaultWaiterResponse.builder().response(describeServicesResponse).attemptsExecuted(1).build();
    doReturn(describeServicesResponseWaiterResponse)
        .when(ecsCommandTaskNGHelper)
        .ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
            (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

    CreateServiceResponse createServiceResponse = CreateServiceResponse.builder().service(service).build();
    doReturn(createServiceResponse)
        .when(ecsCommandTaskNGHelper)
        .createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doNothing()
        .when(ecsCommandTaskNGHelper)
        .ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);

    ecsCommandTaskNGHelper.createCanaryService(createServiceRequest, ecsScalableTargetManifestContentList,
        ecsScalingPolicyManifestContentList, ecsInfraConfig, logCallback, timeoutInMillis);

    verify(ecsCommandTaskNGHelper)
        .deleteService(service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(),
            ecsInfraConfig.getAwsConnectorDTO());
    verify(ecsCommandTaskNGHelper)
        .ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(),
            (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

    verify(ecsCommandTaskNGHelper)
        .ecsServiceSteadyStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), createServiceRequest.cluster(),
            createServiceRequest.serviceName(), ecsInfraConfig.getRegion(), timeoutInMillis, eventsAlreadyProcessed);
    verify(ecsCommandTaskNGHelper)
        .createService(createServiceRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

    verify(ecsCommandTaskNGHelper)
        .registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);

    verify(ecsCommandTaskNGHelper)
        .attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getScalableTargetsAsStringDescribeScalableTargetsResponseNullTest() {
    Service service =
        Service.builder().status("Active").clusterArn("arn").serviceArn("arn").serviceName(serviceName).build();
    DescribeScalableTargetsResponse describeScalableTargetsResponse = DescribeScalableTargetsResponse.builder().build();
    doReturn(describeScalableTargetsResponse)
        .when(ecsCommandTaskNGHelper)
        .listScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());
    ecsCommandTaskNGHelper.getScalableTargetsAsString(
        prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);

    verify(ecsCommandTaskNGHelper)
        .listScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getScalableTargetsAsStringTest() {
    Service service =
        Service.builder().status("Active").clusterArn("arn").serviceArn("arn").serviceName(serviceName).build();
    ScalableTarget scalableTarget =
        ScalableTarget.builder().serviceNamespace(serviceName).scalableDimension("1").build();
    DescribeScalableTargetsResponse describeScalableTargetsResponse =
        DescribeScalableTargetsResponse.builder().scalableTargets(scalableTarget).build();
    doReturn(describeScalableTargetsResponse)
        .when(ecsCommandTaskNGHelper)
        .listScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());
    ecsCommandTaskNGHelper.getScalableTargetsAsString(
        prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);

    verify(ecsCommandTaskNGHelper)
        .listScalableTargets(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getScalingPoliciesAsStringDescribeScalableTargetsResponseNullTest() {
    Service service =
        Service.builder().status("Active").clusterArn("arn").serviceArn("arn").serviceName(serviceName).build();
    DescribeScalingPoliciesResponse describeScalingPoliciesResponse = DescribeScalingPoliciesResponse.builder().build();
    doReturn(describeScalingPoliciesResponse)
        .when(ecsCommandTaskNGHelper)
        .listScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());
    ecsCommandTaskNGHelper.getScalingPoliciesAsString(
        prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);

    verify(ecsCommandTaskNGHelper)
        .listScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getScalingPoliciesAsStringTest() {
    Service service =
        Service.builder().status("Active").clusterArn("arn").serviceArn("arn").serviceName(serviceName).build();
    ScalingPolicy scalingPolicy = ScalingPolicy.builder().policyName("P1").build();
    DescribeScalingPoliciesResponse describeScalingPoliciesResponse =
        DescribeScalingPoliciesResponse.builder().scalingPolicies(scalingPolicy).build();
    doReturn(describeScalingPoliciesResponse)
        .when(ecsCommandTaskNGHelper)
        .listScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());
    ecsCommandTaskNGHelper.getScalingPoliciesAsString(
        prepareRollbackDataLogCallback, serviceName, service, ecsInfraConfig);
    verify(ecsCommandTaskNGHelper)
        .listScalingPolicies(ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), service.serviceName(),
            ecsInfraConfig.getRegion());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void createStageServiceTest() {
    String ecsServiceDefinitionManifestContent = "grpArnKey";
    String taskDefinitionArn = "taskArn";

    List<String> ecsScalableTargetManifestContentList = Arrays.asList("content");
    List<String> ecsScalingPolicyManifestContentList = Arrays.asList("content");
    EcsBlueGreenCreateServiceRequest ecsBlueGreenCreateServiceRequest =
        EcsBlueGreenCreateServiceRequest.builder().targetGroupArnKey(ecsServiceDefinitionManifestContent).build();

    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName(serviceName).cluster(cluster).desiredCount(1);
    String ecsServiceDefinitionManifestContentUpdate = targetGroupArn;
    doReturn(createServiceRequestBuilder)
        .when(ecsCommandTaskNGHelper)
        .parseYamlAsObject(ecsServiceDefinitionManifestContentUpdate, CreateServiceRequest.serializableBuilderClass());

    Service service = Service.builder()
                          .status("ACTIVE")
                          .clusterArn("arn")
                          .serviceArn("arn")
                          .events(ServiceEvent.builder().build())
                          .serviceName(serviceName)
                          .build();
    DescribeServicesResponse describeServicesResponse = DescribeServicesResponse.builder().services(service).build();

    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    doReturn(describeServicesResponse)
        .when(ecsV2Client)
        .describeServices(eq(awsInternalConfig), any(), eq(ecsInfraConfig.getRegion()));
    String stageServiceName =
        ecsCommandTaskNGHelper.getNonBlueVersionServiceName(trim(service.serviceName() + DELIMITER), ecsInfraConfig);

    doReturn(Optional.of(describeServicesResponse.services().get(0)))
        .when(ecsCommandTaskNGHelper)
        .describeService(ecsInfraConfig.getCluster(), stageServiceName, ecsInfraConfig.getRegion(),
            ecsInfraConfig.getAwsConnectorDTO());

    WaiterResponse<Object> describeServicesResponseWaiterResponse =
        DefaultWaiterResponse.builder().response(describeServicesResponse).attemptsExecuted(1).build();
    doReturn(describeServicesResponseWaiterResponse)
        .when(ecsCommandTaskNGHelper)
        .ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
            service.serviceName(), ecsInfraConfig.getRegion(), (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

    DeleteServiceResponse deleteServiceResponse = DeleteServiceResponse.builder().build();
    doReturn(deleteServiceResponse)
        .when(ecsCommandTaskNGHelper)
        .deleteService(service.serviceName(), service.clusterArn(), ecsInfraConfig.getRegion(),
            ecsInfraConfig.getAwsConnectorDTO());
    CreateServiceResponse createServiceResponse = CreateServiceResponse.builder().service(service).build();
    doReturn(createServiceResponse)
        .when(ecsCommandTaskNGHelper)
        .createService(any(), eq(ecsInfraConfig.getRegion()), eq(ecsInfraConfig.getAwsConnectorDTO()));
    List<ServiceEvent> eventsAlreadyProcessed = new ArrayList<>(createServiceResponse.service().events());

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .ecsServiceSteadyStateCheck(any(), any(), anyString(), anyString(), anyString(), anyLong(), any());

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .registerScalableTargets(ecsScalableTargetManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);

    doNothing()
        .when(ecsCommandTaskNGHelper)
        .attachScalingPolicies(ecsScalingPolicyManifestContentList, ecsInfraConfig.getAwsConnectorDTO(),
            createServiceResponse.service().serviceName(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            logCallback);

    String serviceNameOutput = ecsCommandTaskNGHelper.createStageService(ecsServiceDefinitionManifestContent,
        ecsScalableTargetManifestContentList, ecsScalingPolicyManifestContentList, ecsInfraConfig, logCallback,
        timeoutInMillis, ecsBlueGreenCreateServiceRequest.getTargetGroupArnKey(), taskDefinitionArn, targetGroupArn,
        false, false);

    assertThat(serviceNameOutput).isEqualTo(createServiceRequestBuilder.build().serviceName());
    verify(ecsCommandTaskNGHelper)
        .ecsServiceInactiveStateCheck(logCallback, ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(),
            service.serviceName(), ecsInfraConfig.getRegion(), (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void swapTargetGroupsTest() {
    ModifyListenerRequest modifyListenerRequest =
        ModifyListenerRequest.builder()
            .listenerArn(prodListenerArn)
            .defaultActions(Action.builder().type(ActionTypeEnum.FORWARD).targetGroupArn(targetGroupArn).build())
            .build();
    String nextToken = null;
    DescribeRulesRequest describeRulesRequestProd =
        DescribeRulesRequest.builder().listenerArn(prodListenerArn).marker(nextToken).pageSize(10).build();
    DescribeRulesRequest describeRulesRequestStage =
        DescribeRulesRequest.builder().listenerArn(stageListenerArn).marker(nextToken).pageSize(10).build();
    Rule rule = Rule.builder().ruleArn(prodListenerRuleArn).isDefault(true).build();
    DescribeRulesResponse describeRulesResponse = DescribeRulesResponse.builder().rules(rule).build();
    doReturn(describeRulesResponse)
        .when(elbV2Client)
        .describeRules(awsInternalConfig, describeRulesRequestProd, ecsInfraConfig.getRegion());
    doReturn(describeRulesResponse)
        .when(elbV2Client)
        .describeRules(awsInternalConfig, describeRulesRequestStage, ecsInfraConfig.getRegion());
    elbV2Client.modifyListener(awsInternalConfig, modifyListenerRequest, ecsInfraConfig.getRegion());
    ecsCommandTaskNGHelper.swapTargetGroups(ecsInfraConfig, logCallback, ecsLoadBalancerConfig, awsInternalConfig);

    verify(ecsCommandTaskNGHelper)
        .modifyListenerRule(ecsInfraConfig, ecsLoadBalancerConfig.getProdListenerArn(),
            ecsLoadBalancerConfig.getProdListenerRuleArn(), ecsLoadBalancerConfig.getStageTargetGroupArn(),
            awsInternalConfig, logCallback);
    verify(ecsCommandTaskNGHelper)
        .modifyListenerRule(ecsInfraConfig, ecsLoadBalancerConfig.getStageListenerArn(),
            ecsLoadBalancerConfig.getStageListenerRuleArn(), ecsLoadBalancerConfig.getProdTargetGroupArn(),
            awsInternalConfig, logCallback);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getTargetGroupArnFromLoadBalancerTest() {
    DescribeLoadBalancersRequest describeLoadBalancersRequest =
        DescribeLoadBalancersRequest.builder().names(loadBalancer).build();
    LoadBalancer loadBalancerObj = LoadBalancer.builder().loadBalancerArn(loadBalancerArn).build();
    DescribeLoadBalancersResponse describeLoadBalancersResponse =
        DescribeLoadBalancersResponse.builder().loadBalancers(loadBalancerObj).build();
    doReturn(describeLoadBalancersResponse)
        .when(elbV2Client)
        .describeLoadBalancer(awsInternalConfig, describeLoadBalancersRequest, ecsInfraConfig.getRegion());

    String nextToken = null;
    DescribeListenersRequest describeListenersRequest =
        DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).marker(nextToken).pageSize(10).build();
    Listener listener = Listener.builder().listenerArn(prodListenerArn).build();
    DescribeListenersResponse describeListenersResponse =
        DescribeListenersResponse.builder().listeners(listener).build();
    doReturn(describeListenersResponse)
        .when(elbV2Client)
        .describeListener(awsInternalConfig, describeListenersRequest, ecsInfraConfig.getRegion());

    DescribeRulesRequest describeRulesRequest =
        DescribeRulesRequest.builder().listenerArn(prodListenerArn).marker(nextToken).pageSize(10).build();
    Action action = Action.builder().targetGroupArn(targetGroupArn).build();
    Rule rule = Rule.builder().ruleArn(prodListenerRuleArn).isDefault(true).actions(action).build();
    DescribeRulesResponse describeRulesResponse = DescribeRulesResponse.builder().rules(rule).build();
    doReturn(describeRulesResponse)
        .when(elbV2Client)
        .describeRules(awsInternalConfig, describeRulesRequest, ecsInfraConfig.getRegion());
    String targetGroupArnOutput = ecsCommandTaskNGHelper.getTargetGroupArnFromLoadBalancer(
        ecsInfraConfig, prodListenerArn, prodListenerRuleArn, loadBalancer, awsInternalConfig);
    assertThat(targetGroupArnOutput).isEqualTo(describeRulesResponse.rules().get(0).actions().get(0).targetGroupArn());
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testIsEcsTaskContainerFailed() {
    Container container1 = Container.builder().exitCode(0).build();
    assertThat(ecsCommandTaskNGHelper.isEcsTaskContainerFailed(container1)).isFalse();

    Container container2 = Container.builder().exitCode(null).build();
    assertThat(ecsCommandTaskNGHelper.isEcsTaskContainerFailed(container2)).isFalse();

    Container container3 = Container.builder().exitCode(null).lastStatus("STOPPED").build();
    assertThat(ecsCommandTaskNGHelper.isEcsTaskContainerFailed(container3)).isTrue();
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGetDefaultListenerRuleForListener() {
    String defaultRuleArn = "defaultRuleArn";
    List<Rule> rules = Arrays.asList(Rule.builder().ruleArn(defaultRuleArn).isDefault(true).build(),
        Rule.builder().ruleArn("other").isDefault(false).build());

    doReturn(rules).when(ecsCommandTaskNGHelper).getListenerRulesForListener(any(), any(), any());

    assertThat(
        ecsCommandTaskNGHelper.getDefaultListenerRuleForListener(awsInternalConfig, ecsInfraConfig, "listenerRuleArn"))
        .isEqualTo(defaultRuleArn);
  }

  @Test(expected = Exception.class)
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGetDefaultListenerRuleForListenerWithNoDefaultRule() {
    List<Rule> rules = Arrays.asList(Rule.builder().ruleArn("other").isDefault(false).build());

    doReturn(rules).when(ecsCommandTaskNGHelper).getListenerRulesForListener(any(), any(), any());

    ecsCommandTaskNGHelper.getDefaultListenerRuleForListener(awsInternalConfig, ecsInfraConfig, "listenerRuleArn");
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testWaitAndDoSteadyStateCheck() {
    MockedStatic<HTimeLimiter> hTimeLimiterMockedStatic = mockStatic(HTimeLimiter.class);
    hTimeLimiterMockedStatic.when(() -> HTimeLimiter.callInterruptible(any(), any(), any())).thenReturn(null);
    ecsCommandTaskNGHelper.waitAndDoSteadyStateCheck(null, 10l, awsConnectorDTO, "region", "clusterName", logCallback);
    verify(logCallback)
        .saveExecutionLog("All Tasks completed successfully.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test(expected = TimeoutException.class)
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testWaitAndDoSteadyStateCheckTimeoutFailure() {
    MockedStatic<HTimeLimiter> hTimeLimiterMockedStatic = mockStatic(HTimeLimiter.class);
    hTimeLimiterMockedStatic.when(() -> HTimeLimiter.callInterruptible(any(), any(), any()))
        .thenThrow(UncheckedTimeoutException.class);
    ecsCommandTaskNGHelper.waitAndDoSteadyStateCheck(null, 10l, awsConnectorDTO, "region", "clusterName", logCallback);
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testUpdateECSLoadbalancerConfigWithDefaultListenerRulesIfEmpty() {
    String defaultRuleArn = "defaultRuleArn";

    doReturn(defaultRuleArn).when(ecsCommandTaskNGHelper).getDefaultListenerRuleForListener(any(), any(), any());

    EcsLoadBalancerConfig ecsLoadBalancerConfigWithEmptyListenerRules = EcsLoadBalancerConfig.builder()
                                                                            .loadBalancer(loadBalancer)
                                                                            .prodListenerArn(prodListenerArn)
                                                                            .prodListenerRuleArn("")
                                                                            .prodTargetGroupArn(targetGroupArn)
                                                                            .stageListenerArn(stageListenerArn)
                                                                            .stageListenerRuleArn("")
                                                                            .stageTargetGroupArn(targetGroupArn)
                                                                            .build();

    ecsCommandTaskNGHelper.updateECSLoadbalancerConfigWithDefaultListenerRulesIfEmpty(
        ecsLoadBalancerConfigWithEmptyListenerRules, awsInternalConfig, ecsInfraConfig, logCallback);

    assertThat(ecsLoadBalancerConfigWithEmptyListenerRules.getProdListenerRuleArn()).isEqualTo(defaultRuleArn);
    assertThat(ecsLoadBalancerConfigWithEmptyListenerRules.getStageListenerRuleArn()).isEqualTo(defaultRuleArn);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void toYamlTest() throws JsonProcessingException {
    CreateServiceRequest.Builder createServiceRequestBuilder =
        CreateServiceRequest.builder().serviceName(serviceName).cluster(cluster);
    String serviceRequest = ecsCommandTaskNGHelper.toYaml(createServiceRequestBuilder);
    assertThat(serviceRequest).contains(cluster);
    assertThat(serviceRequest).contains(serviceName);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.container.ContainerInfo.Status.FAILURE;
import static io.harness.container.ContainerInfo.Status.SUCCESS;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.SAINATH;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.command.EcsSetupParams.EcsSetupParamsBuilder.anEcsSetupParams;
import static software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsSwapRoutesCommandTaskHelper.BG_GREEN;
import static software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsSwapRoutesCommandTaskHelper.BG_VERSION;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.TASK_FAMILY;
import static software.wings.utils.WingsTestConstants.TASK_REVISION;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static wiremock.com.google.common.collect.Lists.newArrayList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.k8s.model.ImageDetails;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData.ContainerSetupCommandUnitExecutionDataBuilder;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.beans.dto.EcsContainerTask;
import software.wings.beans.dto.EcsServiceSpecification;
import software.wings.beans.dto.SettingAttribute;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.aws.EcsContainerService;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsAppAutoScalingHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEcsHelperServiceDelegate;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalingPoliciesResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ScalingPolicy;
import com.amazonaws.services.ecs.model.AssignPublicIp;
import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.DeleteServiceRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkMode;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.ServiceEvent;
import com.amazonaws.services.ecs.model.ServiceRegistry;
import com.amazonaws.services.ecs.model.Tag;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OwnedBy(CDP)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsSetupCommandTaskHelperTest extends WingsBaseTest {
  public static final String SECURITY_GROUP_ID_1 = "sg-id";
  public static final String CLUSTER_NAME = "clusterName";
  public static final String TARGET_GROUP_ARN = "targetGroupArn";
  public static final String SUBNET_ID = "subnet-id";
  public static final String VPC_ID = "vpc-id";
  public static final String CONTAINER_SERVICE_NAME = "containerServiceName";
  public static final String CONTAINER_NAME = "containerName";
  public static final String ROLE_ARN = "taskToleArn";
  public static final String DOCKER_IMG_NAME = "dockerImgName";
  public static final String DOCKER_DOMAIN_NAME = "domainName";
  @Mock private AwsClusterService awsClusterService;
  @Mock private AwsEcsHelperServiceDelegate mockAwsEcsHelperServiceDelegate;
  @Mock private AwsAppAutoScalingHelperServiceDelegate mockAwsAppAutoScalingService;
  @Mock private AwsHelperService mockAwsHelperService;
  @Mock private EcsContainerService mockEcsContainerService;
  @Spy @InjectMocks @Inject private EcsSetupCommandTaskHelper ecsSetupCommandTaskHelper;

  private SettingAttribute computeProvider = SettingAttribute.builder().value(AwsConfig.builder().build()).build();

  private String ecsSErviceSpecJsonString = "{\n\"capacityProviderStrategy\":[],\n"
      + "\"placementConstraints\":[],\n"
      + "\"placementStrategy\":[],\n"
      + "\"healthCheckGracePeriodSeconds\":null,\n"
      + "\"serviceRegistries\": [\n"
      + "                {\n"
      + "                    \"registryArn\": \"arn:aws:servicediscovery:us-east-1:448640225317:service/srv-v43my342legaqd3r\",\n"
      + "                    \"containerName\": \"containerName\",\n"
      + "                    \"containerPort\": 80\n"
      + "                }\n"
      + "            ],\n"
      + "\"schedulingStrategy\":\"REPLICA\"\n"
      + "}";

  private String taskDefJson = "{\n"
      + "  \"containerDefinitions\" : [ {\n"
      + "    \"name\" : \"${CONTAINER_NAME}\",\n"
      + "    \"image\" : \"${DOCKER_IMAGE_NAME}\",\n"
      + "    \"cpu\" : 1,\n"
      + "    \"memory\" : 512,\n"
      + "    \"links\" : [ ],\n"
      + "    \"portMappings\" : [ {\n"
      + "      \"containerPort\" : 80,\n"
      + "      \"protocol\" : \"tcp\"\n"
      + "    } ],\n"
      + "\"secrets\": [\n"
      + "            {\n"
      + "                \"name\": \"environment_variable_name\",\n"
      + "                \"valueFrom\": \"arn:aws:ssm:region:aws_account_id:parameter/parameter_name\"\n"
      + "            }\n"
      + "        ],\n"
      + "    \"entryPoint\" : [ ],\n"
      + "    \"command\" : [ ],\n"
      + "    \"environment\" : [ ],\n"
      + "    \"mountPoints\" : [ ],\n"
      + "    \"volumesFrom\" : [ ],\n"
      + "    \"dnsServers\" : [ ],\n"
      + "    \"dnsSearchDomains\" : [ ],\n"
      + "    \"extraHosts\" : [ ],\n"
      + "    \"dockerSecurityOptions\" : [ ],\n"
      + "    \"ulimits\" : [ ],\n"
      + "    \"systemControls\" : [ ]\n"
      + "  } ],\n"
      + "  \"executionRoleArn\" : \"abc\",\n"
      + "  \"volumes\" : [ ],\n"
      + "  \"requiresAttributes\" : [ ],\n"
      + "  \"placementConstraints\" : [ ],\n"
      + "  \"compatibilities\" : [ ],\n"
      + "  \"requiresCompatibilities\" : [ ],\n"
      + "  \"cpu\" : \"1\",\n"
      + "  \"memory\" : \"512\",\n"
      + "  \"networkMode\" : \"awsvpc\"\n"
      + "}";

  private String registerTaskDefinitionRequestJson = "{\n"
      + "  \"containerDefinitions\" : [ {\n"
      + "    \"name\" : \"${CONTAINER_NAME}\",\n"
      + "    \"image\" : \"${DOCKER_IMAGE_NAME}\",\n"
      + "    \"cpu\" : 1,\n"
      + "    \"memory\" : 512,\n"
      + "    \"links\" : [ ],\n"
      + "    \"portMappings\" : [ {\n"
      + "      \"containerPort\" : 80,\n"
      + "      \"protocol\" : \"tcp\"\n"
      + "    } ],\n"
      + "\"secrets\": [\n"
      + "            {\n"
      + "                \"name\": \"environment_variable_name\",\n"
      + "                \"valueFrom\": \"arn:aws:ssm:region:aws_account_id:parameter/parameter_name\"\n"
      + "            }\n"
      + "        ],\n"
      + "    \"entryPoint\" : [ ],\n"
      + "    \"command\" : [ ],\n"
      + "    \"environment\" : [ ],\n"
      + "    \"mountPoints\" : [ ],\n"
      + "    \"volumesFrom\" : [ ],\n"
      + "    \"dnsServers\" : [ ],\n"
      + "    \"dnsSearchDomains\" : [ ],\n"
      + "    \"extraHosts\" : [ ],\n"
      + "    \"dockerSecurityOptions\" : [ ],\n"
      + "    \"ulimits\" : [ ],\n"
      + "    \"systemControls\" : [ ]\n"
      + "  } ],\n"
      + "  \"executionRoleArn\" : \"abc\",\n"
      + "  \"volumes\" : [ ],\n"
      + "  \"requiresAttributes\" : [ ],\n"
      + "  \"placementConstraints\" : [ ],\n"
      + "  \"compatibilities\" : [ ],\n"
      + "  \"requiresCompatibilities\" : [ ],\n"
      + "  \"cpu\" : \"1\",\n"
      + "  \"memory\" : \"512\",\n"
      + "  \"networkMode\" : \"awsvpc\",\n"
      + "  \"tags\" : [{\"key\" : \"key1\" , \"value\" : \"value1\"}, {\"key\" : \"key2\", \"value\" : \"value2\"}]"
      + "}";

  private final String fargateConfigYaml = "{\n"
      + "  \"networkMode\": \"awsvpc\", \n"
      + "  \"taskRoleArn\":null,\n"
      + "  \"executionRoleArn\": \"abc\", \n"
      + "  \"containerDefinitions\" : [ {\n"
      + "    \"logConfiguration\": {\n"
      + "        \"logDriver\": \"awslogs\",\n"
      + "        \"options\": {\n"
      + "          \"awslogs-group\": \"/ecs/test_3__fargate__env\",\n"
      + "          \"awslogs-region\": \"us-east-1\",\n"
      + "          \"awslogs-stream-prefix\": \"ecs\"\n"
      + "        }\n"
      + "    },\n"
      + "    \"name\" : \"${CONTAINER_NAME}\",\n"
      + "    \"image\" : \"${DOCKER_IMAGE_NAME}\",\n"
      + "    \"links\" : [ ],\n"
      + "    \"cpu\": 256, \n"
      + "    \"memoryReservation\": 1024, \n"
      + "    \"portMappings\": [\n"
      + "                {\n"
      + "                    \"containerPort\": 80,\n"
      + "                    \"protocol\": \"tcp\"\n"
      + "                }\n"
      + "    ], \n"
      + "    \"entryPoint\" : [ ],\n"
      + "    \"command\" : [ ],\n"
      + "    \"environment\" : [ ],\n"
      + "    \"mountPoints\" : [ ],\n"
      + "    \"volumesFrom\" : [ ],\n"
      + "    \"dnsServers\" : [ ],\n"
      + "    \"dnsSearchDomains\" : [ ],\n"
      + "    \"extraHosts\" : [ ],\n"
      + "    \"dockerSecurityOptions\" : [ ],\n"
      + "    \"ulimits\" : [ ]\n"
      + "  } ],\n"
      + "  \"volumes\" : [ ],\n"
      + "  \"requiresAttributes\" : [ ],\n"
      + "  \"placementConstraints\" : [ ],\n"
      + "  \"compatibilities\" : [ ],\n"
      + "  \"requiresCompatibilities\": [\n"
      + "        \"FARGATE\"\n"
      + "  ], \n"
      + "  \"cpu\": \"256\", \n"
      + "  \"memory\": \"1024\"\n"
      + "}";

  private final String fargateRegisterTaskDefinitionRequestJson = "{\n"
      + "  \"networkMode\": \"awsvpc\", \n"
      + "  \"taskRoleArn\":null,\n"
      + "  \"executionRoleArn\": \"abc\", \n"
      + "  \"containerDefinitions\" : [ {\n"
      + "    \"logConfiguration\": {\n"
      + "        \"logDriver\": \"awslogs\",\n"
      + "        \"options\": {\n"
      + "          \"awslogs-group\": \"/ecs/test_3__fargate__env\",\n"
      + "          \"awslogs-region\": \"us-east-1\",\n"
      + "          \"awslogs-stream-prefix\": \"ecs\"\n"
      + "        }\n"
      + "    },\n"
      + "    \"name\" : \"${CONTAINER_NAME}\",\n"
      + "    \"image\" : \"${DOCKER_IMAGE_NAME}\",\n"
      + "    \"links\" : [ ],\n"
      + "    \"cpu\": 256, \n"
      + "    \"memoryReservation\": 1024, \n"
      + "    \"portMappings\": [\n"
      + "                {\n"
      + "                    \"containerPort\": 80,\n"
      + "                    \"protocol\": \"tcp\"\n"
      + "                }\n"
      + "    ], \n"
      + "    \"entryPoint\" : [ ],\n"
      + "    \"command\" : [ ],\n"
      + "    \"environment\" : [ ],\n"
      + "    \"mountPoints\" : [ ],\n"
      + "    \"volumesFrom\" : [ ],\n"
      + "    \"dnsServers\" : [ ],\n"
      + "    \"dnsSearchDomains\" : [ ],\n"
      + "    \"extraHosts\" : [ ],\n"
      + "    \"dockerSecurityOptions\" : [ ],\n"
      + "    \"ulimits\" : [ ]\n"
      + "  } ],\n"
      + "  \"volumes\" : [ ],\n"
      + "  \"requiresAttributes\" : [ ],\n"
      + "  \"placementConstraints\" : [ ],\n"
      + "  \"compatibilities\" : [ ],\n"
      + "  \"requiresCompatibilities\": [\n"
      + "        \"FARGATE\"\n"
      + "  ], \n"
      + "  \"cpu\": \"256\", \n"
      + "  \"memory\": \"1024\",\n"
      + "  \"tags\" : [{\"key\" : \"key1\" , \"value\" : \"value1\"}, {\"key\" : \"key2\", \"value\" : \"value2\"}]"
      + "}";

  private TaskDefinition taskDefinition;

  /**
   * Set up.
   */
  @Before
  public void setup() {
    taskDefinition = new TaskDefinition();
    taskDefinition.setFamily(TASK_FAMILY);
    taskDefinition.setRevision(TASK_REVISION);

    when(awsClusterService.createTask(eq(Regions.US_EAST_1.getName()), any(SettingAttribute.class), any(),
             any(RegisterTaskDefinitionRequest.class)))
        .thenReturn(taskDefinition);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetCreateServiceRequest_Fargate() throws Exception {
    EcsSetupParams setupParams = anEcsSetupParams()
                                     .withClusterName(CLUSTER_NAME)
                                     .withTargetGroupArn(TARGET_GROUP_ARN)
                                     .withRoleArn(ROLE_ARN)
                                     .withRegion(Regions.US_EAST_1.getName())
                                     .withAssignPublicIps(true)
                                     .withVpcId(VPC_ID)
                                     .withSecurityGroupIds(new String[] {SECURITY_GROUP_ID_1})
                                     .withSubnetIds(new String[] {SUBNET_ID})
                                     .withExecutionRoleArn("arn")
                                     .withUseLoadBalancer(true)
                                     .withLaunchType(LaunchType.FARGATE.name())
                                     .build();

    TaskDefinition taskDefinition =
        new TaskDefinition()
            .withRequiresCompatibilities(LaunchType.FARGATE.name())
            .withExecutionRoleArn("arn")
            .withFamily("family")
            .withRevision(1)
            .withContainerDefinitions(
                new ContainerDefinition()
                    .withPortMappings(new PortMapping().withContainerPort(80).withProtocol("http"))
                    .withName(CONTAINER_NAME));

    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any());

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    TargetGroup targetGroup = new TargetGroup();
    targetGroup.setPort(80);
    targetGroup.setTargetGroupArn(TARGET_GROUP_ARN);

    when(awsClusterService.getTargetGroup(Regions.US_EAST_1.getName(), computeProvider, emptyList(), TARGET_GROUP_ARN))
        .thenReturn(targetGroup);

    CreateServiceRequest createServiceRequest = ecsSetupCommandTaskHelper.getCreateServiceRequest(computeProvider,
        encryptedDataDetails, setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, log,
        ContainerSetupCommandUnitExecutionData.builder(), false);

    assertThat(createServiceRequest).isNotNull();

    // Required for fargate using Load balancer, as ECS assumes role automatically
    assertThat(createServiceRequest.getRole()).isNull();

    assertThat(createServiceRequest.getNetworkConfiguration()).isNotNull();
    assertThat(createServiceRequest.getNetworkConfiguration().getAwsvpcConfiguration()).isNotNull();
    assertThat(createServiceRequest.getLaunchType()).isEqualTo(LaunchType.FARGATE.name());

    AwsVpcConfiguration awsvpcConfiguration = createServiceRequest.getNetworkConfiguration().getAwsvpcConfiguration();
    assertThat(awsvpcConfiguration.getAssignPublicIp()).isEqualTo(AssignPublicIp.ENABLED.name());
    assertThat(awsvpcConfiguration.getSecurityGroups()).hasSize(1);
    assertThat(awsvpcConfiguration.getSecurityGroups().iterator().next()).isEqualTo(SECURITY_GROUP_ID_1);
    assertThat(awsvpcConfiguration.getSubnets()).hasSize(1);
    assertThat(awsvpcConfiguration.getSubnets().iterator().next()).isEqualTo(SUBNET_ID);

    assertThat(createServiceRequest.getServiceName()).isEqualTo(CONTAINER_SERVICE_NAME);
    assertThat(createServiceRequest.getCluster()).isEqualTo(CLUSTER_NAME);
    assertThat(createServiceRequest.getDesiredCount().intValue()).isEqualTo(0);

    assertThat(createServiceRequest.getDeploymentConfiguration()).isNotNull();
    assertThat(createServiceRequest.getDeploymentConfiguration().getMinimumHealthyPercent().intValue()).isEqualTo(100);
    assertThat(createServiceRequest.getDeploymentConfiguration().getMaximumPercent().intValue()).isEqualTo(200);

    assertThat(createServiceRequest.getTaskDefinition())
        .isEqualTo(taskDefinition.getFamily() + ":" + taskDefinition.getRevision());

    assertThat(createServiceRequest.getLoadBalancers()).isNotNull();
    assertThat(createServiceRequest.getLoadBalancers()).hasSize(1);
    LoadBalancer loadBalancer = createServiceRequest.getLoadBalancers().iterator().next();
    assertThat(loadBalancer.getContainerName()).isEqualTo(CONTAINER_NAME);
    assertThat(loadBalancer.getTargetGroupArn()).isEqualTo(TARGET_GROUP_ARN);
    assertThat(loadBalancer.getContainerPort().intValue()).isEqualTo(80);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetCreateServiceRequest_EC2() throws Exception {
    EcsSetupParams setupParams = getEcsSetupParams();
    TaskDefinition taskDefinition = getTaskDefinition();

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any());

    TargetGroup targetGroup = getTargetGroup();

    when(awsClusterService.getTargetGroup(Regions.US_EAST_1.getName(), computeProvider, emptyList(), TARGET_GROUP_ARN))
        .thenReturn(targetGroup);

    CreateServiceRequest createServiceRequest = ecsSetupCommandTaskHelper.getCreateServiceRequest(computeProvider,
        encryptedDataDetails, setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, log,
        ContainerSetupCommandUnitExecutionData.builder(), false);

    assertCreateServiceRequestObject(taskDefinition, createServiceRequest);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetCreateServiceRequest_serviceSpec() throws Exception {
    EcsSetupParams setupParams = getEcsSetupParams();
    setupParams.setUseLoadBalancer(false);
    setupParams.setTargetGroupArn(null);
    setupParams.setLoadBalancerName(null);

    setupParams.setEcsServiceSpecification(
        EcsServiceSpecification.builder().serviceId(SERVICE_ID).serviceSpecJson(ecsSErviceSpecJsonString).build());
    TaskDefinition taskDefinition = getTaskDefinition();

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any());

    CreateServiceRequest createServiceRequest = ecsSetupCommandTaskHelper.getCreateServiceRequest(computeProvider,
        encryptedDataDetails, setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, log,
        ContainerSetupCommandUnitExecutionData.builder(), false);

    List<ServiceRegistry> serviceRegistries = createServiceRequest.getServiceRegistries();
    assertThat(createServiceRequest.getServiceRegistries()).isNotNull();
    assertThat(serviceRegistries).hasSize(1);
    ServiceRegistry serviceRegistry = createServiceRequest.getServiceRegistries().get(0);
    assertThat(serviceRegistry).isNotNull();
    assertThat(serviceRegistry.getRegistryArn())
        .isEqualTo("arn:aws:servicediscovery:us-east-1:448640225317:service/srv-v43my342legaqd3r");
    assertThat(serviceRegistry.getContainerName()).isEqualTo(CONTAINER_NAME);
    assertThat(serviceRegistry.getContainerPort().intValue()).isEqualTo(80);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidateServiceRegistries() throws Exception {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any());

    Service service = ecsSetupCommandTaskHelper.getAwsServiceFromJson(ecsSErviceSpecJsonString, log);
    assertThat(service).isNotNull();
    assertThat(service.getServiceRegistries()).isNotNull();
    assertThat(service.getServiceRegistries()).hasSize(1);

    // Valid case
    ecsSetupCommandTaskHelper.validateServiceRegistries(
        service.getServiceRegistries(), getTaskDefinition(), executionLogCallback);

    // Invalid cases
    service.getServiceRegistries().get(0).setContainerName("invalid");
    TaskDefinition taskDefinition = getTaskDefinition();
    try {
      ecsSetupCommandTaskHelper.validateServiceRegistries(
          service.getServiceRegistries(), taskDefinition, executionLogCallback);
      assertThat(false).isFalse();
    } catch (Exception e) {
      assertThat(true).isTrue();
    }

    service.getServiceRegistries().get(0).setContainerName(CONTAINER_NAME);
    service.getServiceRegistries().get(0).setContainerPort(2000);
    try {
      ecsSetupCommandTaskHelper.validateServiceRegistries(
          service.getServiceRegistries(), getTaskDefinition(), executionLogCallback);
      assertThat(false).isFalse();
    } catch (Exception e) {
      assertThat(true).isTrue();
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetCreateServiceRequest_EC2_awsvpc() throws Exception {
    EcsSetupParams setupParams = getEcsSetupParams();
    setupParams.setSubnetIds(new String[] {"subnet1"});
    setupParams.setSecurityGroupIds(new String[] {"sg1"});
    setupParams.setAssignPublicIps(true);

    TaskDefinition taskDefinition = getTaskDefinition();
    taskDefinition.setNetworkMode("awsvpc");

    TargetGroup targetGroup = getTargetGroup();

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any());

    when(awsClusterService.getTargetGroup(Regions.US_EAST_1.getName(), computeProvider, emptyList(), TARGET_GROUP_ARN))
        .thenReturn(targetGroup);

    CreateServiceRequest createServiceRequest = ecsSetupCommandTaskHelper.getCreateServiceRequest(computeProvider,
        encryptedDataDetails, setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, log,
        ContainerSetupCommandUnitExecutionData.builder(), false);

    assertThat(createServiceRequest.getNetworkConfiguration()).isNotNull();
    assertThat(createServiceRequest.getNetworkConfiguration().getAwsvpcConfiguration()).isNotNull();
    AwsVpcConfiguration awsVpcConfiguration = createServiceRequest.getNetworkConfiguration().getAwsvpcConfiguration();
    assertThat(awsVpcConfiguration.getSecurityGroups()).hasSize(1);
    assertThat(awsVpcConfiguration.getSecurityGroups().get(0)).isEqualTo("sg1");
    assertThat(awsVpcConfiguration.getSubnets()).hasSize(1);
    assertThat(awsVpcConfiguration.getSubnets().get(0)).isEqualTo("subnet1");
    assertThat(awsVpcConfiguration.getAssignPublicIp()).isEqualTo(AssignPublicIp.DISABLED.name());
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testGetCreateServiceRequestCapacityProviderStrategy() {
    EcsSetupParams setupParams = anEcsSetupParams()
                                     .withClusterName(CLUSTER_NAME)
                                     .withTargetGroupArn(TARGET_GROUP_ARN)
                                     .withRoleArn(ROLE_ARN)
                                     .withRegion(Regions.US_EAST_1.getName())
                                     .withAssignPublicIps(true)
                                     .withVpcId(VPC_ID)
                                     .withSecurityGroupIds(new String[] {SECURITY_GROUP_ID_1})
                                     .withSubnetIds(new String[] {SUBNET_ID})
                                     .withExecutionRoleArn("arn")
                                     .withUseLoadBalancer(true)
                                     .withLaunchType(LaunchType.FARGATE.name())
                                     .withImageDetails(ImageDetails.builder().build())
                                     .build();
    // empty capacityProviderStrategy
    String serviceSpecJson = "{\n\"capacityProviderStrategy\":[],\n"
        + "\"placementConstraints\":[ ],\n"
        + "\"placementStrategy\":[ ],\n"
        + "\"healthCheckGracePeriodSeconds\":null,\n"
        + "\"tags\":[ ],\n"
        + "\"schedulingStrategy\":\"REPLICA\"\n}";

    EcsServiceSpecification ecsServiceSpecification =
        EcsServiceSpecification.builder().serviceSpecJson(serviceSpecJson).build();
    setupParams.setEcsServiceSpecification(ecsServiceSpecification);

    TaskDefinition taskDefinition =
        new TaskDefinition()
            .withRequiresCompatibilities(LaunchType.FARGATE.name())
            .withExecutionRoleArn("arn")
            .withFamily("family")
            .withRevision(1)
            .withContainerDefinitions(
                new ContainerDefinition()
                    .withPortMappings(new PortMapping().withContainerPort(80).withProtocol("http"))
                    .withName(CONTAINER_NAME));

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any());

    TargetGroup targetGroup = getTargetGroup();

    when(awsClusterService.getTargetGroup(Regions.US_EAST_1.getName(), computeProvider, emptyList(), TARGET_GROUP_ARN))
        .thenReturn(targetGroup);

    CreateServiceRequest createServiceRequest = ecsSetupCommandTaskHelper.getCreateServiceRequest(computeProvider,
        encryptedDataDetails, setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, log,
        ContainerSetupCommandUnitExecutionData.builder(), false);

    assertThat(createServiceRequest.getCapacityProviderStrategy().size()).isEqualTo(0);
    assertThat(createServiceRequest.getLaunchType()).isEqualTo("FARGATE");

    // null capacityProviderStrategy
    serviceSpecJson = "{\n\"capacityProviderStrategy\": null,\n"
        + "\"placementConstraints\":[ ],\n"
        + "\"placementStrategy\":[ ],\n"
        + "\"healthCheckGracePeriodSeconds\":null,\n"
        + "\"tags\":[ ],\n"
        + "\"schedulingStrategy\":\"REPLICA\"\n}";

    ecsServiceSpecification = EcsServiceSpecification.builder().serviceSpecJson(serviceSpecJson).build();
    setupParams.setEcsServiceSpecification(ecsServiceSpecification);

    createServiceRequest = ecsSetupCommandTaskHelper.getCreateServiceRequest(computeProvider, encryptedDataDetails,
        setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, log,
        ContainerSetupCommandUnitExecutionData.builder(), false);

    assertThat(createServiceRequest.getCapacityProviderStrategy().size()).isEqualTo(0);
    assertThat(createServiceRequest.getLaunchType()).isEqualTo("FARGATE");

    // non null capacityProviderStrategy
    serviceSpecJson = "{\n\"capacityProviderStrategy\": [{ \"capacityProvider\": \"FARGATE\", \"weight\": 100}],\n"
        + "\"placementConstraints\":[ ],\n"
        + "\"placementStrategy\":[ ],\n"
        + "\"healthCheckGracePeriodSeconds\":null,\n"
        + "\"tags\":[ ],\n"
        + "\"schedulingStrategy\":\"REPLICA\"\n}";

    ecsServiceSpecification = EcsServiceSpecification.builder().serviceSpecJson(serviceSpecJson).build();
    setupParams.setEcsServiceSpecification(ecsServiceSpecification);

    createServiceRequest = ecsSetupCommandTaskHelper.getCreateServiceRequest(computeProvider, encryptedDataDetails,
        setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, log,
        ContainerSetupCommandUnitExecutionData.builder(), false);

    assertThat(createServiceRequest.getCapacityProviderStrategy().size()).isEqualTo(1);
    assertThat(createServiceRequest.getCapacityProviderStrategy().get(0).getCapacityProvider()).isEqualTo("FARGATE");
    assertThat(createServiceRequest.getCapacityProviderStrategy().get(0).getWeight()).isEqualTo(100);
    assertThat(createServiceRequest.getLaunchType()).isEqualTo(null);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testIsServiceWithSamePrefix() {
    assertThat(ecsSetupCommandTaskHelper.isServiceWithSamePrefix("Beacons__Conversions__177", "Beacons__Conversions__"))
        .isTrue();
    assertThat(ecsSetupCommandTaskHelper.isServiceWithSamePrefix(
                   "Beacons__Conversions__177__Fargate__4", "Beacons__Conversions__"))
        .isFalse();
  }

  private TaskDefinition getTaskDefinition() {
    return new TaskDefinition().withFamily("family").withRevision(1).withContainerDefinitions(
        new ContainerDefinition()
            .withPortMappings(new PortMapping().withContainerPort(80).withProtocol("http"))
            .withName(CONTAINER_NAME));
  }

  private TargetGroup getTargetGroup() {
    TargetGroup targetGroup = new TargetGroup();
    targetGroup.setPort(80);
    targetGroup.setTargetGroupArn(TARGET_GROUP_ARN);
    return targetGroup;
  }

  private EcsSetupParams getEcsSetupParams() {
    return anEcsSetupParams()
        .withTaskFamily(TASK_FAMILY)
        .withClusterName(CLUSTER_NAME)
        .withTargetGroupArn(TARGET_GROUP_ARN)
        .withRoleArn(ROLE_ARN)
        .withRegion(Regions.US_EAST_1.getName())
        .withUseLoadBalancer(true)
        .withImageDetails(ImageDetails.builder().name("ImageName").tag("Tag").build())
        .build();
  }

  private void assertCreateServiceRequestObject(
      TaskDefinition taskDefinition, CreateServiceRequest createServiceRequest) {
    assertThat(createServiceRequest).isNotNull();

    // netWorkConfiguration should be ignored here, as its required only for fargate
    assertThat(createServiceRequest.getRole()).isNotNull();
    assertThat(createServiceRequest.getRole()).isEqualTo(ROLE_ARN);
    assertThat(createServiceRequest.getNetworkConfiguration()).isNull();
    assertThat(createServiceRequest.getServiceName()).isEqualTo(CONTAINER_SERVICE_NAME);
    assertThat(createServiceRequest.getCluster()).isEqualTo(CLUSTER_NAME);
    assertThat(createServiceRequest.getDesiredCount().intValue()).isEqualTo(0);
    assertThat(createServiceRequest.getDeploymentConfiguration()).isNotNull();
    assertThat(createServiceRequest.getDeploymentConfiguration().getMinimumHealthyPercent().intValue()).isEqualTo(100);
    assertThat(createServiceRequest.getDeploymentConfiguration().getMaximumPercent().intValue()).isEqualTo(200);
    assertThat(createServiceRequest.getTaskDefinition())
        .isEqualTo(taskDefinition.getFamily() + ":" + taskDefinition.getRevision());
    assertThat(createServiceRequest.getLoadBalancers()).isNotNull();
    assertThat(createServiceRequest.getLoadBalancers()).hasSize(1);

    LoadBalancer loadBalancer = createServiceRequest.getLoadBalancers().iterator().next();
    assertThat(loadBalancer.getContainerName()).isEqualTo(CONTAINER_NAME);
    assertThat(loadBalancer.getTargetGroupArn()).isEqualTo(TARGET_GROUP_ARN);
    assertThat(loadBalancer.getContainerPort().intValue()).isEqualTo(80);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCreateTaskDefinition_ECS() throws Exception {
    EcsContainerTask ecsContainerTask = EcsContainerTask.builder().advancedConfig(taskDefJson).build();

    doReturn(new TaskDefinition()).when(awsClusterService).createTask(anyString(), any(), anyList(), any());

    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    ecsSetupCommandTaskHelper.createTaskDefinition(ecsContainerTask, "ContainerName", DOCKER_IMG_NAME,
        getEcsSetupParams(), AwsConfig.builder().build(), ImmutableMap.of("svk", "svv"),
        ImmutableMap.of("sdvk", "sdvv"), Collections.EMPTY_LIST, executionLogCallback, DOCKER_DOMAIN_NAME);

    ArgumentCaptor<RegisterTaskDefinitionRequest> captor = ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(awsClusterService).createTask(anyString(), any(), anyList(), captor.capture());
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = captor.getValue();
    assertThat(registerTaskDefinitionRequest).isNotNull();
    assertThat(registerTaskDefinitionRequest.getFamily()).isEqualTo(TASK_FAMILY);
    assertThat(registerTaskDefinitionRequest.getNetworkMode().toLowerCase())
        .isEqualTo(NetworkMode.Awsvpc.name().toLowerCase());

    assertThat(registerTaskDefinitionRequest.getExecutionRoleArn()).isEqualTo("abc");
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).isNotNull();
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);

    ContainerDefinition containerDefinition = registerTaskDefinitionRequest.getContainerDefinitions().get(0);
    assertThat(containerDefinition).isNotNull();
    assertThat(containerDefinition.getName()).isEqualTo("ContainerName");
    assertThat(containerDefinition.getMemory().intValue()).isEqualTo(512);
    assertThat(containerDefinition.getCpu().intValue()).isEqualTo(1);
    assertThat(containerDefinition.getImage()).isEqualTo(DOCKER_DOMAIN_NAME + "/" + DOCKER_IMG_NAME);
    assertThat(containerDefinition.getPortMappings()).isNotNull();
    assertThat(containerDefinition.getPortMappings()).hasSize(1);

    List<KeyValuePair> environment = containerDefinition.getEnvironment();
    assertThat(environment).isNotNull();
    assertThat(environment.size()).isEqualTo(1);
    assertThat(environment.get(0).getName()).isEqualTo("svk");
    assertThat(environment.get(0).getValue()).isEqualTo("svv");

    PortMapping portMapping = containerDefinition.getPortMappings().iterator().next();
    assertThat(portMapping.getContainerPort().intValue()).isEqualTo(80);
    assertThat(portMapping.getProtocol()).isEqualTo("tcp");

    assertThat(containerDefinition.getSecrets()).isNotNull();
    assertThat(containerDefinition.getSecrets()).hasSize(1);
    assertThat(containerDefinition.getSecrets().get(0).getName()).isEqualTo("environment_variable_name");
    assertThat(containerDefinition.getSecrets().get(0).getValueFrom())
        .isEqualTo("arn:aws:ssm:region:aws_account_id:parameter/parameter_name");
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo("1");
    assertThat(registerTaskDefinitionRequest.getMemory()).isEqualTo("512");
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testCreateTaskDefinitionParseAsRegisterTaskDefinitionRequest_ECS() {
    EcsContainerTask ecsContainerTask =
        EcsContainerTask.builder().advancedConfig(registerTaskDefinitionRequestJson).build();

    doReturn(new TaskDefinition()).when(awsClusterService).createTask(anyString(), any(), anyList(), any());

    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    ecsSetupCommandTaskHelper.createTaskDefinitionParseAsRegisterTaskDefinitionRequest(ecsContainerTask,
        "ContainerName", DOCKER_IMG_NAME, getEcsSetupParams(), AwsConfig.builder().build(),
        ImmutableMap.of("svk", "svv"), ImmutableMap.of("sdvk", "sdvv"), Collections.EMPTY_LIST, executionLogCallback,
        DOCKER_DOMAIN_NAME);

    ArgumentCaptor<RegisterTaskDefinitionRequest> captor = ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(awsClusterService).createTask(anyString(), any(), anyList(), captor.capture());
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = captor.getValue();
    assertThat(registerTaskDefinitionRequest).isNotNull();
    assertThat(registerTaskDefinitionRequest.getFamily()).isEqualTo(TASK_FAMILY);
    assertThat(registerTaskDefinitionRequest.getNetworkMode().toLowerCase())
        .isEqualTo(NetworkMode.Awsvpc.name().toLowerCase());

    assertThat(registerTaskDefinitionRequest.getExecutionRoleArn()).isEqualTo("abc");
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).isNotNull();
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo("1");
    assertThat(registerTaskDefinitionRequest.getMemory()).isEqualTo("512");
    assertThat(registerTaskDefinitionRequest.getTags().contains(new Tag().withKey("key1").withValue("value1")))
        .isTrue();
    assertThat(registerTaskDefinitionRequest.getTags().contains(new Tag().withKey("key2").withValue("value2")))
        .isTrue();

    ContainerDefinition containerDefinition = registerTaskDefinitionRequest.getContainerDefinitions().get(0);
    assertThat(containerDefinition).isNotNull();
    assertThat(containerDefinition.getName()).isEqualTo("ContainerName");
    assertThat(containerDefinition.getMemory().intValue()).isEqualTo(512);
    assertThat(containerDefinition.getCpu().intValue()).isEqualTo(1);
    assertThat(containerDefinition.getImage()).isEqualTo(DOCKER_DOMAIN_NAME + "/" + DOCKER_IMG_NAME);
    assertThat(containerDefinition.getPortMappings()).isNotNull();
    assertThat(containerDefinition.getPortMappings()).hasSize(1);

    List<KeyValuePair> environment = containerDefinition.getEnvironment();
    assertThat(environment).isNotNull();
    assertThat(environment.size()).isEqualTo(1);
    assertThat(environment.get(0).getName()).isEqualTo("svk");
    assertThat(environment.get(0).getValue()).isEqualTo("svv");

    PortMapping portMapping = containerDefinition.getPortMappings().iterator().next();
    assertThat(portMapping.getContainerPort().intValue()).isEqualTo(80);
    assertThat(portMapping.getProtocol()).isEqualTo("tcp");

    assertThat(containerDefinition.getSecrets()).isNotNull();
    assertThat(containerDefinition.getSecrets()).hasSize(1);
    assertThat(containerDefinition.getSecrets().get(0).getName()).isEqualTo("environment_variable_name");
    assertThat(containerDefinition.getSecrets().get(0).getValueFrom())
        .isEqualTo("arn:aws:ssm:region:aws_account_id:parameter/parameter_name");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateTaskDefinition_Fargate() throws Exception {
    EcsContainerTask ecsContainerTask = EcsContainerTask.builder().advancedConfig(fargateConfigYaml).build();

    EcsSetupParams setupParams = getEcsSetupParams();
    setupParams.setVpcId(VPC_ID);
    setupParams.setSecurityGroupIds(new String[] {SECURITY_GROUP_ID_1});
    setupParams.setSubnetIds(new String[] {SUBNET_ID});
    setupParams.setLaunchType(LaunchType.FARGATE.name());

    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any());

    TaskDefinition taskDefinition = ecsSetupCommandTaskHelper.createTaskDefinition(ecsContainerTask, CONTAINER_NAME,
        DOCKER_IMG_NAME, setupParams, AwsConfig.builder().build(), Collections.EMPTY_MAP, Collections.EMPTY_MAP,
        Collections.EMPTY_LIST, executionLogCallback, DOCKER_DOMAIN_NAME);

    // Capture RegisterTaskDefinitionRequest arg that was passed to "awsClusterService.createTask" and assert it
    ArgumentCaptor<RegisterTaskDefinitionRequest> captor = ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(awsClusterService).createTask(anyString(), any(), anyList(), captor.capture());
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = captor.getValue();

    assertThat(registerTaskDefinitionRequest).isNotNull();
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo("256");
    assertThat(registerTaskDefinitionRequest.getMemory()).isEqualTo("1024");
    assertThat(registerTaskDefinitionRequest.getExecutionRoleArn()).isEqualTo("abc");
    assertThat(registerTaskDefinitionRequest.getFamily()).isEqualTo(setupParams.getTaskFamily());
    assertThat(registerTaskDefinitionRequest.getRequiresCompatibilities().contains(LaunchType.FARGATE.name())).isTrue();
    assertThat(registerTaskDefinitionRequest.getNetworkMode().toLowerCase())
        .isEqualTo(NetworkMode.Awsvpc.name().toLowerCase());
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);
    ContainerDefinition taskDefinition1 = registerTaskDefinitionRequest.getContainerDefinitions().iterator().next();
    assertThat(taskDefinition1.getPortMappings()).isNotNull();
    assertThat(taskDefinition1.getPortMappings()).hasSize(1);

    PortMapping portMapping = taskDefinition1.getPortMappings().iterator().next();
    assertThat(portMapping.getProtocol()).isEqualTo("tcp");
    assertThat(portMapping.getContainerPort().intValue()).isEqualTo(80);
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testCreateTaskDefinitionParseAsRegisterTaskDefinitionRequest_Fargate() {
    EcsContainerTask ecsContainerTask =
        EcsContainerTask.builder().advancedConfig(fargateRegisterTaskDefinitionRequestJson).build();

    EcsSetupParams setupParams = getEcsSetupParams();
    setupParams.setVpcId(VPC_ID);
    setupParams.setSecurityGroupIds(new String[] {SECURITY_GROUP_ID_1});
    setupParams.setSubnetIds(new String[] {SUBNET_ID});
    setupParams.setLaunchType(LaunchType.FARGATE.name());

    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any());

    TaskDefinition taskDefinition = ecsSetupCommandTaskHelper.createTaskDefinitionParseAsRegisterTaskDefinitionRequest(
        ecsContainerTask, CONTAINER_NAME, DOCKER_IMG_NAME, setupParams, AwsConfig.builder().build(),
        Collections.EMPTY_MAP, Collections.EMPTY_MAP, Collections.EMPTY_LIST, executionLogCallback, DOCKER_DOMAIN_NAME);

    // Capture RegisterTaskDefinitionRequest arg that was passed to "awsClusterService.createTask" and assert it
    ArgumentCaptor<RegisterTaskDefinitionRequest> captor = ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(awsClusterService).createTask(anyString(), any(), anyList(), captor.capture());
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = captor.getValue();

    assertThat(registerTaskDefinitionRequest).isNotNull();
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo("256");
    assertThat(registerTaskDefinitionRequest.getMemory()).isEqualTo("1024");
    assertThat(registerTaskDefinitionRequest.getExecutionRoleArn()).isEqualTo("abc");
    assertThat(registerTaskDefinitionRequest.getFamily()).isEqualTo(setupParams.getTaskFamily());
    assertThat(registerTaskDefinitionRequest.getRequiresCompatibilities().contains(LaunchType.FARGATE.name())).isTrue();
    assertThat(registerTaskDefinitionRequest.getNetworkMode().toLowerCase())
        .isEqualTo(NetworkMode.Awsvpc.name().toLowerCase());
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);
    assertThat(registerTaskDefinitionRequest.getTags().contains(new Tag().withKey("key1").withValue("value1")))
        .isTrue();
    assertThat(registerTaskDefinitionRequest.getTags().contains(new Tag().withKey("key2").withValue("value2")))
        .isTrue();
    ContainerDefinition taskDefinition1 = registerTaskDefinitionRequest.getContainerDefinitions().iterator().next();
    assertThat(taskDefinition1.getPortMappings()).isNotNull();
    assertThat(taskDefinition1.getPortMappings()).hasSize(1);

    PortMapping portMapping = taskDefinition1.getPortMappings().iterator().next();
    assertThat(portMapping.getProtocol()).isEqualTo("tcp");
    assertThat(portMapping.getContainerPort().intValue()).isEqualTo(80);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testIsValidateSetupParamasForECS() throws Exception {
    TaskDefinition taskDefinition = new TaskDefinition().withExecutionRoleArn("executionRole");

    EcsSetupParams ecsSetupParams = anEcsSetupParams()
                                        .withVpcId("vpc_id")
                                        .withSubnetIds(new String[] {"subnet_1"})
                                        .withSecurityGroupIds(new String[] {"sg_id"})
                                        .withExecutionRoleArn("executionRoleArn")
                                        .withLaunchType(LaunchType.FARGATE.name())
                                        .build();

    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo(StringUtils.EMPTY);

    ecsSetupParams.setSubnetIds(new String[] {"subnet_1", "subnet_2"});
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo(StringUtils.EMPTY);

    ecsSetupParams.setVpcId(null);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo("VPC Id is required for fargate task");

    ecsSetupParams.setVpcId("");
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo("VPC Id is required for fargate task");

    ecsSetupParams.setVpcId("vpc_id");
    ecsSetupParams.setSubnetIds(null);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo("At least 1 subnetId is required for mentioned VPC");

    ecsSetupParams.setSubnetIds(new String[] {null});
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo("At least 1 subnetId is required for mentioned VPC");

    ecsSetupParams.setSubnetIds(new String[] {"subnet_id"});
    ecsSetupParams.setSecurityGroupIds(new String[0]);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo("At least 1 security Group is required for mentioned VPC");

    ecsSetupParams.setSecurityGroupIds(null);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo("At least 1 security Group is required for mentioned VPC");

    ecsSetupParams.setSecurityGroupIds(new String[] {null});
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo("At least 1 security Group is required for mentioned VPC");

    ecsSetupParams.setSecurityGroupIds(new String[] {"sg_id"});
    taskDefinition.setExecutionRoleArn(null);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo("Execution Role ARN is required for Fargate tasks");

    taskDefinition.setExecutionRoleArn("");
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams))
        .isEqualTo("Execution Role ARN is required for Fargate tasks");
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testIsValidateSetupParamsForECSRegisterTaskDefinitionRequest() {
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
        new RegisterTaskDefinitionRequest().withExecutionRoleArn("executionRole");

    EcsSetupParams ecsSetupParams = anEcsSetupParams()
                                        .withVpcId("vpc_id")
                                        .withSubnetIds(new String[] {"subnet_1"})
                                        .withSecurityGroupIds(new String[] {"sg_id"})
                                        .withExecutionRoleArn("executionRoleArn")
                                        .withLaunchType(LaunchType.FARGATE.name())
                                        .build();

    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo(StringUtils.EMPTY);

    ecsSetupParams.setSubnetIds(new String[] {"subnet_1", "subnet_2"});
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo(StringUtils.EMPTY);

    ecsSetupParams.setVpcId(null);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo("VPC Id is required for fargate task");

    ecsSetupParams.setVpcId("");
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo("VPC Id is required for fargate task");

    ecsSetupParams.setVpcId("vpc_id");
    ecsSetupParams.setSubnetIds(null);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo("At least 1 subnetId is required for mentioned VPC");

    ecsSetupParams.setSubnetIds(new String[] {null});
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo("At least 1 subnetId is required for mentioned VPC");

    ecsSetupParams.setSubnetIds(new String[] {"subnet_id"});
    ecsSetupParams.setSecurityGroupIds(new String[0]);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo("At least 1 security Group is required for mentioned VPC");

    ecsSetupParams.setSecurityGroupIds(null);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo("At least 1 security Group is required for mentioned VPC");

    ecsSetupParams.setSecurityGroupIds(new String[] {null});
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo("At least 1 security Group is required for mentioned VPC");

    ecsSetupParams.setSecurityGroupIds(new String[] {"sg_id"});
    registerTaskDefinitionRequest.setExecutionRoleArn(null);
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo("Execution Role ARN is required for Fargate tasks");

    registerTaskDefinitionRequest.setExecutionRoleArn("");
    assertThat(ecsSetupCommandTaskHelper.isValidateSetupParamsForECSRegisterTaskDefinitionRequest(
                   registerTaskDefinitionRequest, ecsSetupParams))
        .isEqualTo("Execution Role ARN is required for Fargate tasks");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCreateEcsContainerTaskIfNull() throws Exception {
    EcsContainerTask ecsContainerTask = ecsSetupCommandTaskHelper.createEcsContainerTaskIfNull(null);
    assertThat(ecsContainerTask).isNotNull();
    assertThat(ecsContainerTask.getContainerDefinitions()).isNotNull();
    assertThat(ecsContainerTask.getContainerDefinitions()).hasSize(1);
    assertThat(ecsContainerTask.getContainerDefinitions().get(0).getCpu().intValue()).isEqualTo(1);
    assertThat(ecsContainerTask.getContainerDefinitions().get(0).getMemory().intValue()).isEqualTo(256);
    assertThat(ecsContainerTask.getContainerDefinitions().get(0).getPortMappings()).isNotNull();
    assertThat(ecsContainerTask.getContainerDefinitions().get(0).getPortMappings()).isEmpty();
    assertThat(ecsContainerTask.getContainerDefinitions().get(0).getLogConfiguration()).isNull();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testIsFargateTaskLauchType() throws Exception {
    EcsSetupParams setupParams = getEcsSetupParams();
    setupParams.setLaunchType(LaunchType.FARGATE.name());
    assertThat(ecsSetupCommandTaskHelper.isFargateTaskLauchType(setupParams)).isTrue();

    setupParams.setLaunchType(LaunchType.EC2.name());
    assertThat(ecsSetupCommandTaskHelper.isFargateTaskLauchType(setupParams)).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetRevisionFromServiceName() throws Exception {
    assertThat(ecsSetupCommandTaskHelper.getRevisionFromServiceName("App_Service_Env__2")).isEqualTo(2);
    assertThat(ecsSetupCommandTaskHelper.getRevisionFromServiceName("App_Service_Env__21")).isEqualTo(21);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testGetServicePrefixByRemovingNumber() throws Exception {
    assertThat(ecsSetupCommandTaskHelper.getServicePrefixByRemovingNumber("App_Service_Env__2"))
        .isEqualTo("App_Service_Env__");
    assertThat(ecsSetupCommandTaskHelper.getServicePrefixByRemovingNumber("App_Service_Env__21"))
        .isEqualTo("App_Service_Env__");
    assertThat(ecsSetupCommandTaskHelper.getServicePrefixByRemovingNumber("App1_Service1_Env1__2"))
        .isEqualTo("App1_Service1_Env1__");
    assertThat(ecsSetupCommandTaskHelper.getServicePrefixByRemovingNumber("App1_Service1_Env1__21"))
        .isEqualTo("App1_Service1_Env1__");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testMatchWithRegex() throws Exception {
    assertThat(ecsSetupCommandTaskHelper.matchWithRegex("App_Service_Env__2", "App_Service_Env__1")).isTrue();
    assertThat(ecsSetupCommandTaskHelper.matchWithRegex("App_Service_Env__2", "App_Service_Env__21")).isTrue();
    assertThat(ecsSetupCommandTaskHelper.matchWithRegex("App1_Service1_Env1__2", "App1_Service1_Env1__121")).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testSetServiceRegistryForDNSSwap() {
    EcsSetupParams mockParams = mock(EcsSetupParams.class);
    doReturn(ImageDetails.builder().name("imageName").tag("imageTag").build()).when(mockParams).getImageDetails();
    doReturn(true).when(mockParams).isBlueGreen();
    doReturn(true).when(mockParams).isUseRoute53DNSSwap();
    doReturn("json1").when(mockParams).getServiceDiscoveryService1JSON();
    doReturn("json2").when(mockParams).getServiceDiscoveryService2JSON();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    Logger mockLogger = mock(Logger.class);
    doReturn(new ServiceRegistry().withRegistryArn("arn1"))
        .doReturn(new ServiceRegistry().withRegistryArn("arn2"))
        .when(ecsSetupCommandTaskHelper)
        .getServiceRegistryFromJson(anyString(), any());
    doReturn(of(new Service().withServiceRegistries(new ServiceRegistry().withRegistryArn("arn1"))))
        .when(ecsSetupCommandTaskHelper)
        .getLastRunningService(any(), any(), any(), anyString());
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    List<ServiceRegistry> serviceRegistries = newArrayList();
    ecsSetupCommandTaskHelper.setServiceRegistryForDNSSwap(
        AwsConfig.builder().build(), null, mockParams, "foo_2", serviceRegistries, mockCallback, mockLogger, builder);
    ContainerSetupCommandUnitExecutionData data = builder.build();
    assertThat("arn1").isEqualTo(data.getOldServiceDiscoveryArn());
    assertThat("arn2").isEqualTo(data.getNewServiceDiscoveryArn());
    assertThat(1).isEqualTo(serviceRegistries.size());
    assertThat("arn2").isEqualTo(serviceRegistries.get(0).getRegistryArn());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testStoreCurrentServiceNameAndCountInfo() {
    AwsConfig awsConfig = AwsConfig.builder().build();
    EcsSetupParams params = anEcsSetupParams().withRegion("us-east-1").withClusterName("cluster").build();
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    doReturn(newArrayList(new Service().withServiceName("foo__1").withDesiredCount(0),
                 new Service().withServiceName("foo__2").withDesiredCount(2),
                 new Service().withServiceName("foo__3").withDesiredCount(0)))
        .when(mockAwsEcsHelperServiceDelegate)
        .listServicesForCluster(any(), anyList(), anyString(), anyString());
    ecsSetupCommandTaskHelper.storeCurrentServiceNameAndCountInfo(awsConfig, params, emptyList(), builder, "foo__3");
    ContainerSetupCommandUnitExecutionData data = builder.build();
    assertThat(data.getEcsServiceToBeDownsized()).isEqualTo("foo__2");
    assertThat(data.getCountToBeDownsizedForOldService()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testBackupAutoScalarConfig() {
    EcsSetupParams params = anEcsSetupParams().withRegion("us-east-1").withClusterName("cluster").build();
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    result.put("foo__1", 2);
    doReturn(result)
        .when(awsClusterService)
        .getActiveServiceCounts(anyString(), any(), anyList(), anyString(), anyString());
    doReturn(
        new DescribeScalableTargetsResult().withScalableTargets(
            new ScalableTarget().withResourceId("resId").withScalableDimension("scalDim").withServiceNamespace("Ecs")))
        .when(mockAwsAppAutoScalingService)
        .listScalableTargets(anyString(), any(), anyList(), any());
    doReturn(new DescribeScalingPoliciesResult().withScalingPolicies(new ScalingPolicy().withPolicyARN("policyArn")))
        .when(mockAwsAppAutoScalingService)
        .listScalingPolicies(anyString(), any(), anyList(), any());
    doReturn("policyJson").when(mockAwsAppAutoScalingService).getJsonForAwsScalablePolicy(any());
    ecsSetupCommandTaskHelper.backupAutoScalarConfig(params, attribute, emptyList(), "foo__1", builder, mockCallback);
    ContainerSetupCommandUnitExecutionData data = builder.build();
    List<AwsAutoScalarConfig> configs = data.getPreviousAwsAutoScalarConfigs();
    assertThat(configs).isNotNull();
    assertThat(configs.size()).isEqualTo(1);
    assertThat(configs.get(0).getResourceId()).isEqualTo("resId");
    assertThat(configs.get(0).getScalingPolicyJson()[0]).isEqualTo("policyJson");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleRollback_PrevSvcExists() {
    EcsSetupParams params = anEcsSetupParams()
                                .withRegion("us-east-1")
                                .withClusterName("cluster")
                                .withIsDaemonSchedulingStrategy(true)
                                .withPreviousEcsServiceSnapshotJson("PrevJson")
                                .withServiceSteadyStateTimeout(10)
                                .build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    doReturn(new Service().withServiceName("foo__1").withServiceArn("svcArn"))
        .when(ecsSetupCommandTaskHelper)
        .getAwsServiceFromJson(anyString(), any());
    doReturn(AwsConfig.builder().build())
        .when(mockAwsHelperService)
        .validateAndGetAwsConfig(any(), anyList(), anyBoolean());
    doReturn(new DescribeServicesResult().withServices(
                 new Service().withDesiredCount(2).withEvents(new ServiceEvent().withId("evId"))))
        .when(mockAwsHelperService)
        .describeServices(anyString(), any(), anyList(), any());
    doReturn(newArrayList(ContainerInfo.builder().containerId("id1").build(),
                 ContainerInfo.builder().containerId("id2").build()))
        .when(mockEcsContainerService)
        .getContainerInfosAfterEcsWait(anyString(), any(), anyList(), anyString(), anyString(), anyList(), any());
    ecsSetupCommandTaskHelper.handleRollback(params, attribute, builder, emptyList(), mockCallback);
    ContainerSetupCommandUnitExecutionData data = builder.build();
    assertThat(data.getEcsServiceArn()).isEqualTo("svcArn");
    assertThat(data.getContainerServiceName()).isEqualTo("foo__1");
    verify(mockAwsHelperService).updateService(anyString(), any(), anyList(), any());
    verify(mockEcsContainerService).waitForTasksToBeInRunningStateWithHandledExceptions(any());
    verify(mockEcsContainerService).waitForServiceToReachSteadyState(anyInt(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleRollback_PrevSvcDoesNotExist() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    EcsSetupParams params = anEcsSetupParams()
                                .withRegion("us-east-1")
                                .withEcsServiceArn("ecsSvcArn")
                                .withClusterName("cluster")
                                .withIsDaemonSchedulingStrategy(true)
                                .build();
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    ecsSetupCommandTaskHelper.handleRollback(params, attribute, builder, emptyList(), mockCallback);
    verify(mockAwsHelperService).deleteService(anyString(), any(), anyList(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testHandleRollback_ThrowTimeoutException() {
    EcsSetupParams params = anEcsSetupParams()
                                .withRegion("us-east-1")
                                .withClusterName("cluster")
                                .withIsDaemonSchedulingStrategy(true)
                                .withPreviousEcsServiceSnapshotJson("PrevJson")
                                .withServiceSteadyStateTimeout(10)
                                .build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    doReturn(new Service().withServiceName("foo__1").withServiceArn("svcArn"))
        .when(ecsSetupCommandTaskHelper)
        .getAwsServiceFromJson(anyString(), any());
    doReturn(AwsConfig.builder().build())
        .when(mockAwsHelperService)
        .validateAndGetAwsConfig(any(), anyList(), anyBoolean());
    doReturn(new DescribeServicesResult().withServices(
                 new Service().withDesiredCount(2).withEvents(new ServiceEvent().withId("evId"))))
        .when(mockAwsHelperService)
        .describeServices(anyString(), any(), anyList(), any());
    doThrow(TimeoutException.class)
        .when(mockEcsContainerService)
        .waitForTasksToBeInRunningStateWithHandledExceptions(any());

    assertThatExceptionOfType(TimeoutException.class)
        .isThrownBy(
            () -> ecsSetupCommandTaskHelper.handleRollback(params, attribute, builder, emptyList(), mockCallback));
    verify(mockAwsHelperService).updateService(anyString(), any(), anyList(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateEcsService() {
    EcsSetupParams params =
        anEcsSetupParams().withRegion("us-east-1").withClusterName("cluster").withTaskFamily("foo").build();
    TaskDefinition taskDefinition = new TaskDefinition().withRevision(2);
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    result.put("foo__1", 2);
    doReturn(result)
        .when(awsClusterService)
        .getActiveServiceCounts(anyString(), any(), anyList(), anyString(), anyString());
    CreateServiceRequest createServiceRequest =
        new CreateServiceRequest().withServiceName("foo__2").withCluster("cluster");
    doReturn(createServiceRequest)
        .when(ecsSetupCommandTaskHelper)
        .getCreateServiceRequest(any(), anyList(), any(), any(), anyString(), any(), any(), any(), eq(false));
    ecsSetupCommandTaskHelper.createEcsService(params, taskDefinition, attribute, emptyList(), builder, mockCallback);
    verify(awsClusterService).createService(anyString(), any(), anyList(), any());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateEcsServiceEnableExecuteCommandNotPresent() {
    String ecsServiceSpec = "{\n"
        + "\"capacityProviderStrategy\":[ ],\n"
        + "\"placementConstraints\":[ ],\n"
        + "\"placementStrategy\":[ ],\n"
        + "\"healthCheckGracePeriodSeconds\":null,\n"
        + "\"tags\":[ ],\n"
        + "\"schedulingStrategy\":\"REPLICA\"\n"
        + "}";
    EcsServiceSpecification ecsServiceSpecification =
        EcsServiceSpecification.builder().serviceSpecJson(ecsServiceSpec).build();
    ImageDetails imageDetails = new ImageDetails();
    imageDetails.setName("imageDetailsName");
    imageDetails.setTag("imageDetailsTag");
    imageDetails.setDomainName("imageDetailsDomainName");
    EcsSetupParams params = anEcsSetupParams()
                                .withRegion("us-east-1")
                                .withClusterName("cluster")
                                .withTaskFamily("foo")
                                .withImageDetails(imageDetails)
                                .withEcsServiceSpecification(ecsServiceSpecification)
                                .build();
    TaskDefinition taskDefinition = new TaskDefinition().withRevision(2);
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    result.put("foo__1", 2);
    doReturn(result)
        .when(awsClusterService)
        .getActiveServiceCounts(anyString(), any(), anyList(), anyString(), anyString());
    CreateServiceRequest createServiceRequest =
        ecsSetupCommandTaskHelper.getCreateServiceRequest(attribute, emptyList(), params, taskDefinition,
            "containerServiceName", mockCallback, LoggerFactory.getLogger("test-logger"), builder, false);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCreateEcsServiceEnableExecuteCommandTrue() {
    String ecsServiceSpec = "{\n"
        + "\"capacityProviderStrategy\":[ ],\n"
        + "\"placementConstraints\":[ ],\n"
        + "\"placementStrategy\":[ ],\n"
        + "\"healthCheckGracePeriodSeconds\":null,\n"
        + "\"tags\":[ ],\n"
        + "\"schedulingStrategy\":\"REPLICA\",\n"
        + "\"enableExecuteCommand\":true\n"
        + "}";
    EcsServiceSpecification ecsServiceSpecification =
        EcsServiceSpecification.builder().serviceSpecJson(ecsServiceSpec).build();
    ImageDetails imageDetails = new ImageDetails();
    imageDetails.setName("imageDetailsName");
    imageDetails.setTag("imageDetailsTag");
    imageDetails.setDomainName("imageDetailsDomainName");
    EcsSetupParams params = anEcsSetupParams()
                                .withRegion("us-east-1")
                                .withClusterName("cluster")
                                .withTaskFamily("foo")
                                .withImageDetails(imageDetails)
                                .withEcsServiceSpecification(ecsServiceSpecification)
                                .build();
    TaskDefinition taskDefinition = new TaskDefinition().withRevision(2);
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    result.put("foo__1", 2);
    doReturn(result)
        .when(awsClusterService)
        .getActiveServiceCounts(anyString(), any(), anyList(), anyString(), anyString());
    CreateServiceRequest createServiceRequest =
        ecsSetupCommandTaskHelper.getCreateServiceRequest(attribute, emptyList(), params, taskDefinition,
            "containerServiceName", mockCallback, LoggerFactory.getLogger("test-logger"), builder, false);
    assertThat(createServiceRequest.getEnableExecuteCommand()).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDownsizeOldOrUnhealthy() {
    EcsSetupParams params =
        anEcsSetupParams().withRegion("us-east-1").withClusterName("cluster").withTaskFamily("foo").build();
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    result.put("foo__1", 1);
    result.put("foo__2", 1);
    doReturn(result)
        .when(awsClusterService)
        .getActiveServiceCountsByServiceNamePrefix(anyString(), any(), anyList(), anyString(), anyString());
    doReturn(new ListTasksResult().withTaskArns(singletonList("foo__1__arn")))
        .doReturn(new ListTasksResult().withTaskArns(singletonList("foo__2__arn")))
        .when(mockAwsHelperService)
        .listTasks(anyString(), any(), anyList(), any(), anyBoolean());
    doReturn(singletonList(ContainerInfo.builder().status(FAILURE).build()))
        .doReturn(singletonList(ContainerInfo.builder().status(SUCCESS).build()))
        .when(mockEcsContainerService)
        .getContainerInfosAfterEcsWait(anyString(), any(), anyList(), anyString(), anyString(), anyList(), any());
    ecsSetupCommandTaskHelper.downsizeOldOrUnhealthy(attribute, params, emptyList(), mockCallback, false);
    verify(awsClusterService)
        .resizeCluster(
            anyString(), any(), anyList(), anyString(), eq("foo__1"), anyInt(), eq(0), anyInt(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCleanup() {
    EcsSetupParams params =
        anEcsSetupParams().withRegion("us-east-1").withClusterName("clusterName").withTaskFamily("foo").build();
    doReturn(newArrayList(new Service().withServiceName("foo__1").withDesiredCount(0),
                 new Service().withServiceName("foo__2").withDesiredCount(2),
                 new Service().withServiceName("foo__3").withDesiredCount(0)))
        .when(awsClusterService)
        .getServices(anyString(), any(), anyList(), anyString(), anyString());
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    ecsSetupCommandTaskHelper.cleanup(attribute, params, emptyList(), mockCallback);
    verify(awsClusterService).deleteService(anyString(), any(), anyList(), anyString(), eq("foo__1"));
    verify(awsClusterService).deleteService(anyString(), any(), anyList(), anyString(), eq("foo__3"));
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetExistingServiceMetadataSnapshot() {
    EcsSetupParams params = anEcsSetupParams().withRegion("us-east-1").withClusterName("cluster").build();
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new DescribeServicesResult().withServices(new Service().withServiceName("foo__1")))
        .when(mockAwsHelperService)
        .describeServices(anyString(), any(), anyList(), any());
    Optional<Service> serviceOptional = ecsSetupCommandTaskHelper.getExistingServiceMetadataSnapshot(
        params, attribute, emptyList(), "foo__1", mockAwsHelperService);
    assertThat(serviceOptional.isPresent()).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetTargetGroupForDefaultAction() {
    Listener listener = new Listener().withDefaultActions(new Action().withTargetGroupArn("arn").withType("forward"));
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    String targetGroup = ecsSetupCommandTaskHelper.getTargetGroupForDefaultAction(listener, mockCallback);
    assertThat(targetGroup).isEqualTo("arn");
    Listener listener2 = new Listener().withDefaultActions(emptyList());
    assertThatThrownBy(() -> ecsSetupCommandTaskHelper.getTargetGroupForDefaultAction(listener2, mockCallback))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testDeleteExistingServicesOtherThanBlueVersion() {
    EcsSetupParams params = anEcsSetupParams().withRegion("us-east-1").withClusterName("cluster").build();
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(singletonList(new Service().withServiceArn("arn").withServiceName("foo__1").withTags(
                 new Tag().withKey(BG_VERSION).withValue(BG_GREEN))))
        .when(ecsSetupCommandTaskHelper)
        .getServicesForClusterByMatchingPrefix(any(), any(), anyList(), anyString());
    ecsSetupCommandTaskHelper.deleteExistingServicesOtherThanBlueVersion(params, attribute, emptyList(), mockCallback);
    ArgumentCaptor<DeleteServiceRequest> captor = ArgumentCaptor.forClass(DeleteServiceRequest.class);
    verify(mockAwsHelperService).deleteService(anyString(), any(), anyList(), captor.capture());
    DeleteServiceRequest request = captor.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getCluster()).isEqualTo("cluster");
    assertThat(request.getService()).isEqualTo("arn");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSetLoadBalancerToServiceReplaceContainerNamePlaceholderInContainerName() {
    EcsSetupParams params = anEcsSetupParams().build();
    SettingAttribute attribute = SettingAttribute.builder().value(AwsConfig.builder().build()).build();
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    CreateServiceRequest createServiceRequest =
        new CreateServiceRequest().withServiceName("foo__2").withCluster("cluster");
    params.setTargetContainerName("${CONTAINER_NAME}_main");
    params.setTargetPort("80");
    params.setGeneratedContainerName("generatedContainerName");
    ecsSetupCommandTaskHelper.setLoadBalancerToService(
        params, attribute, emptyList(), taskDefinition, createServiceRequest, awsClusterService, mockCallback, false);
    List<LoadBalancer> loadBalancers = createServiceRequest.getLoadBalancers();
    assertThat(loadBalancers.size()).isEqualTo(1);
    assertThat(loadBalancers.get(0).getContainerPort()).isEqualTo(80);
    assertThat(loadBalancers.get(0).getContainerName()).isEqualTo("generatedContainerName_main");
    assertThat(params.getTargetContainerName()).isEqualTo("generatedContainerName_main");
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testCreateTaskDefinitionWithEcsRegisterTaskDefinitionTagsFeatureFlag() {
    ImageDetails imageDetails = new ImageDetails();
    imageDetails.setName("imageDetailsName");
    imageDetails.setTag("imageDetailsTag");
    imageDetails.setDomainName("imageDetailsDomainName");
    doReturn(null)
        .when(ecsSetupCommandTaskHelper)
        .createTaskDefinition(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    doReturn(null)
        .when(ecsSetupCommandTaskHelper)
        .createTaskDefinitionParseAsRegisterTaskDefinitionRequest(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

    EcsSetupParams ecsSetupParams = anEcsSetupParams()
                                        .withImageDetails(imageDetails)
                                        .withContainerTask(EcsContainerTask.builder().build())
                                        .withEcsRegisterTaskDefinitionTagsEnabled(true)
                                        .build();
    ecsSetupCommandTaskHelper.createTaskDefinition(null, null, null, null, new ExecutionLogCallback(), ecsSetupParams);

    verify(ecsSetupCommandTaskHelper, times(0))
        .createTaskDefinition(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(ecsSetupCommandTaskHelper, times(1))
        .createTaskDefinitionParseAsRegisterTaskDefinitionRequest(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

    ecsSetupParams.setEcsRegisterTaskDefinitionTagsEnabled(false);
    ecsSetupCommandTaskHelper.createTaskDefinition(null, null, null, null, new ExecutionLogCallback(), ecsSetupParams);

    verify(ecsSetupCommandTaskHelper, times(1))
        .createTaskDefinition(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(ecsSetupCommandTaskHelper, times(1))
        .createTaskDefinitionParseAsRegisterTaskDefinitionRequest(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }
}

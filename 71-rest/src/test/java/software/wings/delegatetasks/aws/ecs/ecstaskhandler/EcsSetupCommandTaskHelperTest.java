package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import static java.util.Collections.emptyList;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.EcsSetupParams.EcsSetupParamsBuilder.anEcsSetupParams;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.TASK_FAMILY;
import static software.wings.utils.WingsTestConstants.TASK_REVISION;
import static wiremock.com.google.common.collect.Lists.newArrayList;

import com.google.inject.Inject;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.model.AssignPublicIp;
import com.amazonaws.services.ecs.model.AwsVpcConfiguration;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.LaunchType;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.NetworkMode;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.ServiceRegistry;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import io.harness.category.element.UnitTests;
import io.harness.security.encryption.EncryptedDataDetail;
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
import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData.ContainerSetupCommandUnitExecutionDataBuilder;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.container.EcsContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.container.ImageDetails;
import software.wings.cloudprovider.aws.AwsClusterService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
@Slf4j
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
  @Spy @InjectMocks @Inject private EcsSetupCommandTaskHelper ecsSetupCommandTaskHelper;

  private SettingAttribute computeProvider = aSettingAttribute().withValue(AwsConfig.builder().build()).build();

  private String ecsSErviceSpecJsonString = "{\n"
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
                new com.amazonaws.services.ecs.model.ContainerDefinition()
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
        encryptedDataDetails, setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, logger,
        ContainerSetupCommandUnitExecutionData.builder());

    assertThat(createServiceRequest).isNotNull();

    // Required for fargate using Load balancer, as ECS assumes role automatically
    assertThat(createServiceRequest.getRole()).isNull();

    assertThat(createServiceRequest.getNetworkConfiguration()).isNotNull();
    assertThat(createServiceRequest.getNetworkConfiguration().getAwsvpcConfiguration()).isNotNull();
    assertEquals(LaunchType.FARGATE.name(), createServiceRequest.getLaunchType());

    AwsVpcConfiguration awsvpcConfiguration = createServiceRequest.getNetworkConfiguration().getAwsvpcConfiguration();
    assertEquals(AssignPublicIp.ENABLED.name(), awsvpcConfiguration.getAssignPublicIp());
    assertThat(awsvpcConfiguration.getSecurityGroups()).hasSize(1);
    assertEquals(SECURITY_GROUP_ID_1, awsvpcConfiguration.getSecurityGroups().iterator().next());
    assertThat(awsvpcConfiguration.getSubnets()).hasSize(1);
    assertEquals(SUBNET_ID, awsvpcConfiguration.getSubnets().iterator().next());

    assertEquals(CONTAINER_SERVICE_NAME, createServiceRequest.getServiceName());
    assertEquals(CLUSTER_NAME, createServiceRequest.getCluster());
    assertEquals(0, createServiceRequest.getDesiredCount().intValue());

    assertThat(createServiceRequest.getDeploymentConfiguration()).isNotNull();
    assertEquals(100, createServiceRequest.getDeploymentConfiguration().getMinimumHealthyPercent().intValue());
    assertEquals(200, createServiceRequest.getDeploymentConfiguration().getMaximumPercent().intValue());

    assertEquals(
        taskDefinition.getFamily() + ":" + taskDefinition.getRevision(), createServiceRequest.getTaskDefinition());

    assertThat(createServiceRequest.getLoadBalancers()).isNotNull();
    assertThat(createServiceRequest.getLoadBalancers()).hasSize(1);
    LoadBalancer loadBalancer = createServiceRequest.getLoadBalancers().iterator().next();
    assertEquals(CONTAINER_NAME, loadBalancer.getContainerName());
    assertEquals(TARGET_GROUP_ARN, loadBalancer.getTargetGroupArn());
    assertEquals(80, loadBalancer.getContainerPort().intValue());
  }

  @Test
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
        encryptedDataDetails, setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, logger,
        ContainerSetupCommandUnitExecutionData.builder());

    assertCreateServiceRequestObject(taskDefinition, createServiceRequest);
  }

  @Test
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
        encryptedDataDetails, setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, logger,
        ContainerSetupCommandUnitExecutionData.builder());

    List<ServiceRegistry> serviceRegistries = createServiceRequest.getServiceRegistries();
    assertThat(createServiceRequest.getServiceRegistries()).isNotNull();
    assertThat(serviceRegistries).hasSize(1);
    ServiceRegistry serviceRegistry = createServiceRequest.getServiceRegistries().get(0);
    assertThat(serviceRegistry).isNotNull();
    assertEquals("arn:aws:servicediscovery:us-east-1:448640225317:service/srv-v43my342legaqd3r",
        serviceRegistry.getRegistryArn());
    assertEquals(CONTAINER_NAME, serviceRegistry.getContainerName());
    assertEquals(80, serviceRegistry.getContainerPort().intValue());
  }

  @Test
  @Category(UnitTests.class)
  public void testValidateServiceRegistries() throws Exception {
    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    doNothing().when(executionLogCallback).saveExecutionLog(anyString(), any());

    Service service = ecsSetupCommandTaskHelper.getAwsServiceFromJson(ecsSErviceSpecJsonString, logger);
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
        encryptedDataDetails, setupParams, taskDefinition, CONTAINER_SERVICE_NAME, executionLogCallback, logger,
        ContainerSetupCommandUnitExecutionData.builder());

    assertThat(createServiceRequest.getNetworkConfiguration()).isNotNull();
    assertThat(createServiceRequest.getNetworkConfiguration().getAwsvpcConfiguration()).isNotNull();
    AwsVpcConfiguration awsVpcConfiguration = createServiceRequest.getNetworkConfiguration().getAwsvpcConfiguration();
    assertThat(awsVpcConfiguration.getSecurityGroups()).hasSize(1);
    assertEquals("sg1", awsVpcConfiguration.getSecurityGroups().get(0));
    assertThat(awsVpcConfiguration.getSubnets()).hasSize(1);
    assertEquals("subnet1", awsVpcConfiguration.getSubnets().get(0));
    assertEquals(AssignPublicIp.DISABLED.name(), awsVpcConfiguration.getAssignPublicIp());
  }

  @Test
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
        new com.amazonaws.services.ecs.model.ContainerDefinition()
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
    assertEquals(ROLE_ARN, createServiceRequest.getRole());
    assertThat(createServiceRequest.getNetworkConfiguration()).isNull();
    assertEquals(CONTAINER_SERVICE_NAME, createServiceRequest.getServiceName());
    assertEquals(CLUSTER_NAME, createServiceRequest.getCluster());
    assertEquals(0, createServiceRequest.getDesiredCount().intValue());
    assertThat(createServiceRequest.getDeploymentConfiguration()).isNotNull();
    assertEquals(100, createServiceRequest.getDeploymentConfiguration().getMinimumHealthyPercent().intValue());
    assertEquals(200, createServiceRequest.getDeploymentConfiguration().getMaximumPercent().intValue());
    assertEquals(
        taskDefinition.getFamily() + ":" + taskDefinition.getRevision(), createServiceRequest.getTaskDefinition());
    assertThat(createServiceRequest.getLoadBalancers()).isNotNull();
    assertThat(createServiceRequest.getLoadBalancers()).hasSize(1);

    LoadBalancer loadBalancer = createServiceRequest.getLoadBalancers().iterator().next();
    assertEquals(CONTAINER_NAME, loadBalancer.getContainerName());
    assertEquals(TARGET_GROUP_ARN, loadBalancer.getTargetGroupArn());
    assertEquals(80, loadBalancer.getContainerPort().intValue());
  }

  @Test
  @Category(UnitTests.class)
  public void testCreateTaskDefinition_ECS() throws Exception {
    EcsContainerTask ecsContainerTask = new EcsContainerTask();
    ecsContainerTask.setAdvancedConfig(taskDefJson);

    doReturn(new TaskDefinition()).when(awsClusterService).createTask(anyString(), any(), anyList(), any());

    ExecutionLogCallback executionLogCallback = mock(ExecutionLogCallback.class);
    ecsSetupCommandTaskHelper.createTaskDefinition(ecsContainerTask, "ContainerName", DOCKER_IMG_NAME,
        getEcsSetupParams(), AwsConfig.builder().build(), Collections.EMPTY_MAP, Collections.EMPTY_MAP,
        Collections.EMPTY_LIST, executionLogCallback, DOCKER_DOMAIN_NAME);

    ArgumentCaptor<RegisterTaskDefinitionRequest> captor = ArgumentCaptor.forClass(RegisterTaskDefinitionRequest.class);
    verify(awsClusterService).createTask(anyString(), any(), anyList(), captor.capture());
    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = captor.getValue();
    assertThat(registerTaskDefinitionRequest).isNotNull();
    assertEquals(TASK_FAMILY, registerTaskDefinitionRequest.getFamily());
    assertEquals(NetworkMode.Awsvpc.name().toLowerCase(), registerTaskDefinitionRequest.getNetworkMode().toLowerCase());

    assertEquals("abc", registerTaskDefinitionRequest.getExecutionRoleArn());
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).isNotNull();
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);

    ContainerDefinition containerDefinition = registerTaskDefinitionRequest.getContainerDefinitions().get(0);
    assertThat(containerDefinition).isNotNull();
    assertEquals("ContainerName", containerDefinition.getName());
    assertEquals(512, containerDefinition.getMemory().intValue());
    assertEquals(1, containerDefinition.getCpu().intValue());
    assertEquals(DOCKER_DOMAIN_NAME + "/" + DOCKER_IMG_NAME, containerDefinition.getImage());
    assertThat(containerDefinition.getPortMappings()).isNotNull();
    assertThat(containerDefinition.getPortMappings()).hasSize(1);

    PortMapping portMapping = containerDefinition.getPortMappings().iterator().next();
    assertEquals(80, portMapping.getContainerPort().intValue());
    assertEquals("tcp", portMapping.getProtocol());

    assertThat(containerDefinition.getSecrets()).isNotNull();
    assertThat(containerDefinition.getSecrets()).hasSize(1);
    assertEquals("environment_variable_name", containerDefinition.getSecrets().get(0).getName());
    assertEquals("arn:aws:ssm:region:aws_account_id:parameter/parameter_name",
        containerDefinition.getSecrets().get(0).getValueFrom());
  }

  @Test
  @Category(UnitTests.class)
  public void testCreateTaskDefinition_Fargate() throws Exception {
    EcsContainerTask ecsContainerTask = new EcsContainerTask();
    ecsContainerTask.setAdvancedConfig(fargateConfigYaml);

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
    assertEquals("256", registerTaskDefinitionRequest.getCpu());
    assertEquals("1024", registerTaskDefinitionRequest.getMemory());
    assertEquals("abc", registerTaskDefinitionRequest.getExecutionRoleArn());
    assertEquals(setupParams.getTaskFamily(), registerTaskDefinitionRequest.getFamily());
    assertThat(registerTaskDefinitionRequest.getRequiresCompatibilities().contains(LaunchType.FARGATE.name())).isTrue();
    assertEquals(NetworkMode.Awsvpc.name().toLowerCase(), registerTaskDefinitionRequest.getNetworkMode().toLowerCase());
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);
    com.amazonaws.services.ecs.model.ContainerDefinition taskDefinition1 =
        registerTaskDefinitionRequest.getContainerDefinitions().iterator().next();
    assertThat(taskDefinition1.getPortMappings()).isNotNull();
    assertThat(taskDefinition1.getPortMappings()).hasSize(1);

    PortMapping portMapping = taskDefinition1.getPortMappings().iterator().next();
    assertEquals("tcp", portMapping.getProtocol());
    assertEquals(80, portMapping.getContainerPort().intValue());
  }

  @Test
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

    assertEquals(
        StringUtils.EMPTY, ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    ecsSetupParams.setSubnetIds(new String[] {"subnet_1", "subnet_2"});
    assertEquals(
        StringUtils.EMPTY, ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    ecsSetupParams.setVpcId(null);
    assertEquals("VPC Id is required for fargate task",
        ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    ecsSetupParams.setVpcId("");
    assertEquals("VPC Id is required for fargate task",
        ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    ecsSetupParams.setVpcId("vpc_id");
    ecsSetupParams.setSubnetIds(null);
    assertEquals("At least 1 subnetId is required for mentioned VPC",
        ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    ecsSetupParams.setSubnetIds(new String[] {null});
    assertEquals("At least 1 subnetId is required for mentioned VPC",
        ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    ecsSetupParams.setSubnetIds(new String[] {"subnet_id"});
    ecsSetupParams.setSecurityGroupIds(new String[0]);
    assertEquals("At least 1 security Group is required for mentioned VPC",
        ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    ecsSetupParams.setSecurityGroupIds(null);
    assertEquals("At least 1 security Group is required for mentioned VPC",
        ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    ecsSetupParams.setSecurityGroupIds(new String[] {null});
    assertEquals("At least 1 security Group is required for mentioned VPC",
        ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    ecsSetupParams.setSecurityGroupIds(new String[] {"sg_id"});
    taskDefinition.setExecutionRoleArn(null);
    assertEquals("Execution Role ARN is required for Fargate tasks",
        ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));

    taskDefinition.setExecutionRoleArn("");
    assertEquals("Execution Role ARN is required for Fargate tasks",
        ecsSetupCommandTaskHelper.isValidateSetupParamasForECS(taskDefinition, ecsSetupParams));
  }

  @Test
  @Category(UnitTests.class)
  public void testCreateEcsContainerTaskIfNull() throws Exception {
    EcsContainerTask ecsContainerTask = ecsSetupCommandTaskHelper.createEcsContainerTaskIfNull(null);
    assertThat(ecsContainerTask).isNotNull();
    assertThat(ecsContainerTask.getContainerDefinitions()).isNotNull();
    assertThat(ecsContainerTask.getContainerDefinitions()).hasSize(1);
    assertEquals(1, ecsContainerTask.getContainerDefinitions().get(0).getCpu().intValue());
    assertEquals(256, ecsContainerTask.getContainerDefinitions().get(0).getMemory().intValue());
    assertThat(ecsContainerTask.getContainerDefinitions().get(0).getPortMappings()).isNotNull();
    assertThat(ecsContainerTask.getContainerDefinitions().get(0).getPortMappings()).isEmpty();
    assertThat(ecsContainerTask.getContainerDefinitions().get(0).getLogConfiguration()).isNull();
  }

  @Test
  @Category(UnitTests.class)
  public void testIsFargateTaskLauchType() throws Exception {
    EcsSetupParams setupParams = getEcsSetupParams();
    setupParams.setLaunchType(LaunchType.FARGATE.name());
    assertThat(ecsSetupCommandTaskHelper.isFargateTaskLauchType(setupParams)).isTrue();

    setupParams.setLaunchType(LaunchType.EC2.name());
    assertThat(ecsSetupCommandTaskHelper.isFargateTaskLauchType(setupParams)).isFalse();
  }

  @Test
  @Category(UnitTests.class)
  public void testGetRevisionFromServiceName() throws Exception {
    assertEquals(2, ecsSetupCommandTaskHelper.getRevisionFromServiceName("App_Service_Env__2"));
    assertEquals(21, ecsSetupCommandTaskHelper.getRevisionFromServiceName("App_Service_Env__21"));
  }

  @Test
  @Category(UnitTests.class)
  public void testGetServicePrefixByRemovingNumber() throws Exception {
    assertEquals("App_Service_Env__", ecsSetupCommandTaskHelper.getServicePrefixByRemovingNumber("App_Service_Env__2"));
    assertEquals(
        "App_Service_Env__", ecsSetupCommandTaskHelper.getServicePrefixByRemovingNumber("App_Service_Env__21"));
    assertEquals(
        "App1_Service1_Env1__", ecsSetupCommandTaskHelper.getServicePrefixByRemovingNumber("App1_Service1_Env1__2"));
    assertEquals(
        "App1_Service1_Env1__", ecsSetupCommandTaskHelper.getServicePrefixByRemovingNumber("App1_Service1_Env1__21"));
  }

  @Test
  @Category(UnitTests.class)
  public void testMatchWithRegex() throws Exception {
    assertThat(ecsSetupCommandTaskHelper.matchWithRegex("App_Service_Env__2", "App_Service_Env__1")).isTrue();
    assertThat(ecsSetupCommandTaskHelper.matchWithRegex("App_Service_Env__2", "App_Service_Env__21")).isTrue();
    assertThat(ecsSetupCommandTaskHelper.matchWithRegex("App1_Service1_Env1__2", "App1_Service1_Env1__121")).isTrue();
  }

  @Test
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
        .getLastRunningService(any(), anyList(), any(), anyString());
    ContainerSetupCommandUnitExecutionDataBuilder builder = ContainerSetupCommandUnitExecutionData.builder();
    List<ServiceRegistry> serviceRegistries = newArrayList();
    ecsSetupCommandTaskHelper.setServiceRegistryForDNSSwap(
        AwsConfig.builder().build(), null, mockParams, "foo_2", serviceRegistries, mockCallback, mockLogger, builder);
    ContainerSetupCommandUnitExecutionData data = builder.build();
    assertEquals(data.getOldServiceDiscoveryArn(), "arn1");
    assertEquals(data.getNewServiceDiscoveryArn(), "arn2");
    assertEquals(serviceRegistries.size(), 1);
    assertEquals(serviceRegistries.get(0).getRegistryArn(), "arn2");
  }
}

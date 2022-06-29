/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.SAINATH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.amazonaws.services.ecs.model.InferenceAccelerator;
import com.amazonaws.services.ecs.model.ProxyConfiguration;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.Tag;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.ecs.model.TaskDefinitionPlacementConstraint;
import com.amazonaws.services.ecs.model.Volume;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EcsContainerTaskTest extends CategoryTest {
  public static final String CONTAINER_NAME = "containerName";
  public static final String IMAGE_NAME = "imageName";
  public static final String EXEC_ROLE = "exec_role";
  public static final String DOMAIN_NAME = "domain.name.co";

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testcreateTaskDefinition() {
    EcsContainerTask ecsContainerTask = new EcsContainerTask();
    ecsContainerTask.setAdvancedConfig(null);
    PortMapping portMapping = PortMapping.builder().containerPort(80).build();
    ContainerDefinition containerDefinition =
        ContainerDefinition.builder()
            .cpu(256d)
            .memory(1024)
            .portMappings(asList(portMapping))
            .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
            .build();

    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));

    TaskDefinition taskDefinition = EcsContainerTaskUtils.createTaskDefinition(ecsContainerTask.getAdvancedConfig(),
        ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, EXEC_ROLE, DOMAIN_NAME);

    assertThat(taskDefinition).isNotNull();
    assertThat(taskDefinition.getContainerDefinitions()).hasSize(1);
    assertThat(taskDefinition.getCpu()).isEqualTo("256");
    assertThat(taskDefinition.getMemory()).isEqualTo("1024");
    assertThat(taskDefinition.getExecutionRoleArn()).isEqualTo(EXEC_ROLE);

    com.amazonaws.services.ecs.model.ContainerDefinition containerDefinitionAws =
        taskDefinition.getContainerDefinitions().get(0);
    assertThat(containerDefinitionAws).isNotNull();
    assertThat(containerDefinitionAws.getName()).isEqualTo(CONTAINER_NAME);
    assertThat(containerDefinitionAws.getImage()).isEqualTo(DOMAIN_NAME + "/" + IMAGE_NAME);
    assertThat(containerDefinitionAws.getCpu().intValue()).isEqualTo(256);
    assertThat(containerDefinitionAws.getMemory().intValue()).isEqualTo(1024);
    assertThat(containerDefinitionAws.getPortMappings()).isNotNull();
    assertThat(containerDefinitionAws.getPortMappings()).hasSize(1);
    assertThat(containerDefinitionAws.getPortMappings().iterator().next().getContainerPort().intValue()).isEqualTo(80);

    taskDefinition = EcsContainerTaskUtils.createTaskDefinition(ecsContainerTask.getAdvancedConfig(),
        ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, null, DOMAIN_NAME);

    assertThat(taskDefinition).isNotNull();
    assertThat(taskDefinition.getContainerDefinitions()).hasSize(1);
    assertThat(taskDefinition.getCpu()).isEqualTo("256");
    assertThat(taskDefinition.getMemory()).isEqualTo("1024");
    assertThat(taskDefinition.getExecutionRoleArn()).isEqualTo("");

    containerDefinitionAws = taskDefinition.getContainerDefinitions().get(0);
    assertThat(containerDefinitionAws).isNotNull();
    assertThat(containerDefinitionAws.getName()).isEqualTo(CONTAINER_NAME);
    assertThat(containerDefinitionAws.getImage()).isEqualTo(DOMAIN_NAME + "/" + IMAGE_NAME);
    assertThat(containerDefinitionAws.getCpu().intValue()).isEqualTo(256);
    assertThat(containerDefinitionAws.getMemory().intValue()).isEqualTo(1024);
    assertThat(containerDefinitionAws.getPortMappings()).isNotNull();
    assertThat(containerDefinitionAws.getPortMappings()).hasSize(1);
    assertThat(containerDefinitionAws.getPortMappings().iterator().next().getContainerPort().intValue()).isEqualTo(80);

    containerDefinition = ContainerDefinition.builder()
                              .portMappings(asList(portMapping))
                              .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
                              .build();

    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));
    taskDefinition = EcsContainerTaskUtils.createTaskDefinition(ecsContainerTask.getAdvancedConfig(),
        ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, EXEC_ROLE, DOMAIN_NAME);
    assertThat(taskDefinition).isNotNull();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidate() {
    EcsContainerTask ecsContainerTask = new EcsContainerTask();
    ecsContainerTask.setAdvancedConfig(null);
    PortMapping portMapping = PortMapping.builder().containerPort(80).build();
    ContainerDefinition containerDefinition =
        ContainerDefinition.builder()
            .cpu(256d)
            .memory(1024)
            .portMappings(asList(portMapping))
            .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
            .build();

    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));
    ecsContainerTask.validate();

    PortMapping portMapping2 = PortMapping.builder().build();
    containerDefinition = ContainerDefinition.builder()
                              .cpu(256d)
                              .memory(1024)
                              .portMappings(asList(portMapping, portMapping2))
                              .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
                              .build();
    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));
    ecsContainerTask.validate();
    List<PortMapping> portMappings = ecsContainerTask.getContainerDefinitions().get(0).getPortMappings();
    assertThat(portMappings.size()).isEqualTo(1);
    assertThat(portMappings.get(0).getContainerPort()).isEqualTo(80);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCreateTaskDefinitionWithWhenNoMemoryProvided() {
    EcsContainerTask ecsContainerTask = new EcsContainerTask();
    ecsContainerTask.setAdvancedConfig(null);
    PortMapping portMapping = PortMapping.builder().containerPort(80).build();
    ContainerDefinition containerDefinition =
        ContainerDefinition.builder()
            .cpu(256d)
            .portMappings(asList(portMapping))
            .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
            .build();

    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));

    TaskDefinition taskDefinition = EcsContainerTaskUtils.createTaskDefinition(ecsContainerTask.getAdvancedConfig(),
        ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, EXEC_ROLE, DOMAIN_NAME);

    assertThat(taskDefinition).isNotNull();
    assertThat(taskDefinition.getContainerDefinitions()).hasSize(1);
    assertThat(taskDefinition.getCpu()).isEqualTo("256");
    assertThat(taskDefinition.getExecutionRoleArn()).isEqualTo(EXEC_ROLE);

    com.amazonaws.services.ecs.model.ContainerDefinition containerDefinitionAws =
        taskDefinition.getContainerDefinitions().get(0);
    assertThat(containerDefinitionAws).isNotNull();
    assertThat(containerDefinitionAws.getName()).isEqualTo(CONTAINER_NAME);
    assertThat(containerDefinitionAws.getImage()).isEqualTo(DOMAIN_NAME + "/" + IMAGE_NAME);
    assertThat(containerDefinitionAws.getCpu().intValue()).isEqualTo(256);
    assertThat(containerDefinitionAws.getMemory().intValue()).isEqualTo(1024);
    assertThat(containerDefinitionAws.getPortMappings()).isNotNull();
    assertThat(containerDefinitionAws.getPortMappings()).hasSize(1);
    assertThat(containerDefinitionAws.getPortMappings().iterator().next().getContainerPort().intValue()).isEqualTo(80);

    taskDefinition = EcsContainerTaskUtils.createTaskDefinition(ecsContainerTask.getAdvancedConfig(),
        ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, null, DOMAIN_NAME);

    assertThat(taskDefinition).isNotNull();
    assertThat(taskDefinition.getContainerDefinitions()).hasSize(1);
    assertThat(taskDefinition.getCpu()).isEqualTo("256");
    assertThat(taskDefinition.getExecutionRoleArn()).isEqualTo("");

    containerDefinitionAws = taskDefinition.getContainerDefinitions().get(0);
    assertThat(containerDefinitionAws).isNotNull();
    assertThat(containerDefinitionAws.getName()).isEqualTo(CONTAINER_NAME);
    assertThat(containerDefinitionAws.getImage()).isEqualTo(DOMAIN_NAME + "/" + IMAGE_NAME);
    assertThat(containerDefinitionAws.getCpu().intValue()).isEqualTo(256);
    assertThat(containerDefinitionAws.getMemory().intValue()).isEqualTo(1024);
    assertThat(containerDefinitionAws.getPortMappings()).isNotNull();
    assertThat(containerDefinitionAws.getPortMappings()).hasSize(1);
    assertThat(containerDefinitionAws.getPortMappings().iterator().next().getContainerPort().intValue()).isEqualTo(80);

    containerDefinition = ContainerDefinition.builder()
                              .portMappings(asList(portMapping))
                              .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
                              .build();

    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));
    taskDefinition = EcsContainerTaskUtils.createTaskDefinition(ecsContainerTask.getAdvancedConfig(),
        ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, EXEC_ROLE, DOMAIN_NAME);
    assertThat(taskDefinition).isNotNull();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testRemoveEmptySecretsContainerDefinitionString() throws Exception {
    String str = "{\n"
        + "  \"containerDefinitions\" : [ {\n"
        + "    \"name\" : \"${CONTAINER_NAME}\",\n"
        + "    \"image\" : \"${DOCKER_IMAGE_NAME}\",\n"
        + "    \"cpu\" : 1,\n"
        + "    \"memory\" : 512,\n"
        + "    \"links\" : [ ],\n"
        + "    \"portMappings\" : [ {\n"
        + "      \"containerPort\" : 8080,\n"
        + "      \"protocol\" : \"tcp\"\n"
        + "    } ],\n"
        + "    \"entryPoint\" : [ ],\n"
        + "    \"command\" : [ ],\n"
        + "    \"environment\" : [ ],\n"
        + "    \"mountPoints\" : [ ],\n"
        + "    \"volumesFrom\" : [ ],\n"
        + "    \"secrets\" : [ ],\n"
        + "    \"dnsServers\" : [ ],\n"
        + "    \"dnsSearchDomains\" : [ ],\n"
        + "    \"extraHosts\" : [ ],\n"
        + "    \"dockerSecurityOptions\" : [ ],\n"
        + "    \"ulimits\" : [ ],\n"
        + "    \"systemControls\" : [ ]\n"
        + "  } ],\n"
        + "  \"executionRoleArn\" : \"${EXECUTION_ROLE}\",\n"
        + "  \"volumes\" : [ ],\n"
        + "  \"requiresAttributes\" : [ ],\n"
        + "  \"placementConstraints\" : [ ],\n"
        + "  \"compatibilities\" : [ ],\n"
        + "  \"requiresCompatibilities\" : [ ],\n"
        + "  \"cpu\" : \"1\",\n"
        + "  \"memory\" : \"512\"\n"
        + "}";

    str = EcsContainerTaskUtils.removeEmptySecretsContainerDefinitionString(str);
    assertThat(!str.contains("\"secrets\" : [ ],")).isTrue();

    str = "{\n"
        + "  \"containerDefinitions\" : [ {\n"
        + "    \"name\" : \"${CONTAINER_NAME}\",\n"
        + "    \"image\" : \"${DOCKER_IMAGE_NAME}\",\n"
        + "    \"cpu\" : 1,\n"
        + "    \"memory\" : 512,\n"
        + "    \"links\" : [ ],\n"
        + "    \"portMappings\" : [ {\n"
        + "      \"containerPort\" : 8080,\n"
        + "      \"protocol\" : \"tcp\"\n"
        + "    } ],\n"
        + "    \"entryPoint\" : [ ],\n"
        + "    \"command\" : [ ],\n"
        + "    \"environment\" : [ ],\n"
        + "    \"mountPoints\" : [ ],\n"
        + "    \"volumesFrom\" : [ ],\n"
        + "    \"secrets\" : [],\n"
        + "    \"dnsServers\" : [ ],\n"
        + "    \"dnsSearchDomains\" : [ ],\n"
        + "    \"extraHosts\" : [ ],\n"
        + "    \"dockerSecurityOptions\" : [ ],\n"
        + "    \"ulimits\" : [ ],\n"
        + "    \"systemControls\" : [ ]\n"
        + "  } ],\n"
        + "  \"executionRoleArn\" : \"${EXECUTION_ROLE}\",\n"
        + "  \"volumes\" : [ ],\n"
        + "  \"requiresAttributes\" : [ ],\n"
        + "  \"placementConstraints\" : [ ],\n"
        + "  \"compatibilities\" : [ ],\n"
        + "  \"requiresCompatibilities\" : [ ],\n"
        + "  \"cpu\" : \"1\",\n"
        + "  \"memory\" : \"512\"\n"
        + "}";

    str = EcsContainerTaskUtils.removeEmptySecretsContainerDefinitionString(str);
    assertThat(!str.contains("\"secrets\" : [ ],")).isTrue();

    str = "{\n"
        + "  \"containerDefinitions\" : [ {\n"
        + "    \"name\" : \"${CONTAINER_NAME}\",\n"
        + "    \"image\" : \"${DOCKER_IMAGE_NAME}\",\n"
        + "    \"cpu\" : 1,\n"
        + "    \"memory\" : 512,\n"
        + "    \"links\" : [ ],\n"
        + "    \"portMappings\" : [ {\n"
        + "      \"containerPort\" : 8080,\n"
        + "      \"protocol\" : \"tcp\"\n"
        + "    } ],\n"
        + "    \"entryPoint\" : [ ],\n"
        + "    \"command\" : [ ],\n"
        + "    \"environment\" : [ ],\n"
        + "    \"mountPoints\" : [ ],\n"
        + "    \"volumesFrom\" : [ ],\n"
        + "    \"secrets\":[ ],\n"
        + "    \"dnsServers\" : [ ],\n"
        + "    \"dnsSearchDomains\" : [ ],\n"
        + "    \"extraHosts\" : [ ],\n"
        + "    \"dockerSecurityOptions\" : [ ],\n"
        + "    \"ulimits\" : [ ],\n"
        + "    \"systemControls\" : [ ]\n"
        + "  } ],\n"
        + "  \"executionRoleArn\" : \"${EXECUTION_ROLE}\",\n"
        + "  \"volumes\" : [ ],\n"
        + "  \"requiresAttributes\" : [ ],\n"
        + "  \"placementConstraints\" : [ ],\n"
        + "  \"compatibilities\" : [ ],\n"
        + "  \"requiresCompatibilities\" : [ ],\n"
        + "  \"cpu\" : \"1\",\n"
        + "  \"memory\" : \"512\"\n"
        + "}";

    str = EcsContainerTaskUtils.removeEmptySecretsContainerDefinitionString(str);
    assertThat(!str.contains("\"secrets\" : [ ],")).isTrue();

    str = "{\n"
        + "  \"containerDefinitions\" : [ {\n"
        + "    \"name\" : \"${CONTAINER_NAME}\",\n"
        + "    \"image\" : \"${DOCKER_IMAGE_NAME}\",\n"
        + "    \"cpu\" : 1,\n"
        + "    \"memory\" : 512,\n"
        + "    \"links\" : [ ],\n"
        + "    \"portMappings\" : [ {\n"
        + "      \"containerPort\" : 8080,\n"
        + "      \"protocol\" : \"tcp\"\n"
        + "    } ],\n"
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
        + "  \"executionRoleArn\" : \"${EXECUTION_ROLE}\",\n"
        + "  \"volumes\" : [ ],\n"
        + "  \"requiresAttributes\" : [ ],\n"
        + "  \"placementConstraints\" : [ ],\n"
        + "  \"compatibilities\" : [ ],\n"
        + "  \"requiresCompatibilities\" : [ ],\n"
        + "  \"cpu\" : \"1\",\n"
        + "  \"memory\" : \"512\"\n"
        + "}";

    str = EcsContainerTaskUtils.removeEmptySecretsContainerDefinitionString(str);
    assertThat(!str.contains("\"secrets\" : [ ],")).isTrue();
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testCreateRegisterTaskDefinitionRequest() {
    EcsContainerTask ecsContainerTask = new EcsContainerTask();
    ecsContainerTask.setAdvancedConfig(null);
    PortMapping portMapping = PortMapping.builder().containerPort(80).build();
    ContainerDefinition containerDefinition =
        ContainerDefinition.builder()
            .cpu(256d)
            .memory(1024)
            .portMappings(asList(portMapping))
            .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
            .build();

    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));

    RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
        EcsContainerTaskUtils.createRegisterTaskDefinitionRequest(ecsContainerTask.getAdvancedConfig(),
            ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, EXEC_ROLE, DOMAIN_NAME);

    assertThat(registerTaskDefinitionRequest).isNotNull();
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo("256");
    assertThat(registerTaskDefinitionRequest.getMemory()).isEqualTo("1024");
    assertThat(registerTaskDefinitionRequest.getExecutionRoleArn()).isEqualTo(EXEC_ROLE);

    com.amazonaws.services.ecs.model.ContainerDefinition containerDefinitionAws =
        registerTaskDefinitionRequest.getContainerDefinitions().get(0);
    assertThat(containerDefinitionAws).isNotNull();
    assertThat(containerDefinitionAws.getName()).isEqualTo(CONTAINER_NAME);
    assertThat(containerDefinitionAws.getImage()).isEqualTo(DOMAIN_NAME + "/" + IMAGE_NAME);
    assertThat(containerDefinitionAws.getCpu().intValue()).isEqualTo(256);
    assertThat(containerDefinitionAws.getMemory().intValue()).isEqualTo(1024);
    assertThat(containerDefinitionAws.getPortMappings()).isNotNull();
    assertThat(containerDefinitionAws.getPortMappings()).hasSize(1);
    assertThat(containerDefinitionAws.getPortMappings().iterator().next().getContainerPort().intValue()).isEqualTo(80);

    registerTaskDefinitionRequest =
        EcsContainerTaskUtils.createRegisterTaskDefinitionRequest(ecsContainerTask.getAdvancedConfig(),
            ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, null, DOMAIN_NAME);

    assertThat(registerTaskDefinitionRequest).isNotNull();
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo("256");
    assertThat(registerTaskDefinitionRequest.getMemory()).isEqualTo("1024");
    assertThat(registerTaskDefinitionRequest.getExecutionRoleArn()).isEqualTo("");
    assertThat(registerTaskDefinitionRequest.getTags()).hasSize(0);

    containerDefinitionAws = registerTaskDefinitionRequest.getContainerDefinitions().get(0);
    assertThat(containerDefinitionAws).isNotNull();
    assertThat(containerDefinitionAws.getName()).isEqualTo(CONTAINER_NAME);
    assertThat(containerDefinitionAws.getImage()).isEqualTo(DOMAIN_NAME + "/" + IMAGE_NAME);
    assertThat(containerDefinitionAws.getCpu().intValue()).isEqualTo(256);
    assertThat(containerDefinitionAws.getMemory().intValue()).isEqualTo(1024);
    assertThat(containerDefinitionAws.getPortMappings()).isNotNull();
    assertThat(containerDefinitionAws.getPortMappings()).hasSize(1);
    assertThat(containerDefinitionAws.getPortMappings().iterator().next().getContainerPort().intValue()).isEqualTo(80);

    containerDefinition = ContainerDefinition.builder()
                              .portMappings(asList(portMapping))
                              .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
                              .build();

    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));
    registerTaskDefinitionRequest =
        EcsContainerTaskUtils.createRegisterTaskDefinitionRequest(ecsContainerTask.getAdvancedConfig(),
            ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, EXEC_ROLE, DOMAIN_NAME);
    assertThat(registerTaskDefinitionRequest).isNotNull();
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testCreateRegisterTaskDefinitionRequestWithWhenNoMemoryProvided() {
    EcsContainerTask ecsContainerTask = new EcsContainerTask();
    ecsContainerTask.setAdvancedConfig(null);
    PortMapping portMapping = PortMapping.builder().containerPort(80).build();
    ContainerDefinition containerDefinition =
        ContainerDefinition.builder()
            .cpu(256d)
            .portMappings(asList(portMapping))
            .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
            .build();

    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));

    RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
        EcsContainerTaskUtils.createRegisterTaskDefinitionRequest(ecsContainerTask.getAdvancedConfig(),
            ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, EXEC_ROLE, DOMAIN_NAME);

    assertThat(registerTaskDefinitionRequest).isNotNull();
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo("256");
    assertThat(registerTaskDefinitionRequest.getExecutionRoleArn()).isEqualTo(EXEC_ROLE);
    assertThat(registerTaskDefinitionRequest.getTags()).hasSize(0);

    com.amazonaws.services.ecs.model.ContainerDefinition containerDefinitionAws =
        registerTaskDefinitionRequest.getContainerDefinitions().get(0);
    assertThat(containerDefinitionAws).isNotNull();
    assertThat(containerDefinitionAws.getName()).isEqualTo(CONTAINER_NAME);
    assertThat(containerDefinitionAws.getImage()).isEqualTo(DOMAIN_NAME + "/" + IMAGE_NAME);
    assertThat(containerDefinitionAws.getCpu().intValue()).isEqualTo(256);
    assertThat(containerDefinitionAws.getMemory().intValue()).isEqualTo(1024);
    assertThat(containerDefinitionAws.getPortMappings()).isNotNull();
    assertThat(containerDefinitionAws.getPortMappings()).hasSize(1);
    assertThat(containerDefinitionAws.getPortMappings().iterator().next().getContainerPort().intValue()).isEqualTo(80);

    registerTaskDefinitionRequest =
        EcsContainerTaskUtils.createRegisterTaskDefinitionRequest(ecsContainerTask.getAdvancedConfig(),
            ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, null, DOMAIN_NAME);

    assertThat(registerTaskDefinitionRequest).isNotNull();
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions()).hasSize(1);
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo("256");
    assertThat(registerTaskDefinitionRequest.getExecutionRoleArn()).isEqualTo("");

    containerDefinitionAws = registerTaskDefinitionRequest.getContainerDefinitions().get(0);
    assertThat(containerDefinitionAws).isNotNull();
    assertThat(containerDefinitionAws.getName()).isEqualTo(CONTAINER_NAME);
    assertThat(containerDefinitionAws.getImage()).isEqualTo(DOMAIN_NAME + "/" + IMAGE_NAME);
    assertThat(containerDefinitionAws.getCpu().intValue()).isEqualTo(256);
    assertThat(containerDefinitionAws.getMemory().intValue()).isEqualTo(1024);
    assertThat(containerDefinitionAws.getPortMappings()).isNotNull();
    assertThat(containerDefinitionAws.getPortMappings()).hasSize(1);
    assertThat(containerDefinitionAws.getPortMappings().iterator().next().getContainerPort().intValue()).isEqualTo(80);

    containerDefinition = ContainerDefinition.builder()
                              .portMappings(asList(portMapping))
                              .logConfiguration(LogConfiguration.builder().logDriver("awslog").build())
                              .build();

    ecsContainerTask.setContainerDefinitions(asList(containerDefinition));
    registerTaskDefinitionRequest =
        EcsContainerTaskUtils.createRegisterTaskDefinitionRequest(ecsContainerTask.getAdvancedConfig(),
            ecsContainerTask.getContainerDefinitions(), CONTAINER_NAME, IMAGE_NAME, EXEC_ROLE, DOMAIN_NAME);
    assertThat(registerTaskDefinitionRequest).isNotNull();
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testDeserialisationTaskDefinitionToRegisterTaskDefinitionRequest() throws JsonProcessingException {
    com.amazonaws.services.ecs.model.PortMapping portMapping =
        new com.amazonaws.services.ecs.model.PortMapping().withContainerPort(80);
    com.amazonaws.services.ecs.model.ContainerDefinition containerDefinition =
        new com.amazonaws.services.ecs.model.ContainerDefinition().withCpu(256).withPortMappings(portMapping);
    Volume volume = new Volume();

    TaskDefinition taskDefinition = new TaskDefinition()
                                        .withTaskDefinitionArn("taskDefinitionArn")
                                        .withFamily("family")
                                        .withTaskRoleArn("taskRoleArn")
                                        .withExecutionRoleArn("executionRoleArn")
                                        .withNetworkMode("networkMode")
                                        .withContainerDefinitions(containerDefinition)
                                        .withVolumes(asList(volume))
                                        .withPlacementConstraints(new TaskDefinitionPlacementConstraint())
                                        .withRequiresCompatibilities("requiresCompatibility")
                                        .withCpu("256")
                                        .withMemory("memory")
                                        .withPidMode("pidMode")
                                        .withIpcMode("ipcMode")
                                        .withProxyConfiguration(new ProxyConfiguration())
                                        .withInferenceAccelerators(new InferenceAccelerator());

    String taskDefinitionJson = JsonUtils.asPrettyJson(taskDefinition);

    RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
        JsonUtils.asObject(taskDefinitionJson, RegisterTaskDefinitionRequest.class);

    assertThat(registerTaskDefinitionRequest.getFamily()).isEqualTo(taskDefinition.getFamily());
    assertThat(registerTaskDefinitionRequest.getTaskRoleArn()).isEqualTo(taskDefinition.getTaskRoleArn());
    assertThat(registerTaskDefinitionRequest.getExecutionRoleArn()).isEqualTo(taskDefinition.getExecutionRoleArn());
    assertThat(registerTaskDefinitionRequest.getNetworkMode()).isEqualTo(taskDefinition.getNetworkMode());
    assertThat(registerTaskDefinitionRequest.getContainerDefinitions())
        .isEqualTo(taskDefinition.getContainerDefinitions());
    assertThat(registerTaskDefinitionRequest.getVolumes()).isEqualTo(taskDefinition.getVolumes());
    assertThat(registerTaskDefinitionRequest.getPlacementConstraints())
        .isEqualTo(taskDefinition.getPlacementConstraints());
    assertThat(registerTaskDefinitionRequest.getRequiresCompatibilities())
        .isEqualTo(taskDefinition.getRequiresCompatibilities());
    assertThat(registerTaskDefinitionRequest.getCpu()).isEqualTo(taskDefinition.getCpu());
    assertThat(registerTaskDefinitionRequest.getMemory()).isEqualTo(taskDefinition.getMemory());
    assertThat(registerTaskDefinitionRequest.getPidMode()).isEqualTo(taskDefinition.getPidMode());
    assertThat(registerTaskDefinitionRequest.getIpcMode()).isEqualTo(taskDefinition.getIpcMode());
    assertThat(registerTaskDefinitionRequest.getProxyConfiguration()).isEqualTo(taskDefinition.getProxyConfiguration());
    assertThat(registerTaskDefinitionRequest.getInferenceAccelerators())
        .isEqualTo(taskDefinition.getInferenceAccelerators());
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testDeserialisationRegisterTaskDefinitionRequestToTaskDefinition() throws JsonProcessingException {
    com.amazonaws.services.ecs.model.PortMapping portMapping =
        new com.amazonaws.services.ecs.model.PortMapping().withContainerPort(80);
    com.amazonaws.services.ecs.model.ContainerDefinition containerDefinition =
        new com.amazonaws.services.ecs.model.ContainerDefinition().withCpu(256).withPortMappings(portMapping);
    Volume volume = new Volume();

    RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
        new RegisterTaskDefinitionRequest()
            .withTags(new Tag())
            .withFamily("family")
            .withTaskRoleArn("taskRoleArn")
            .withExecutionRoleArn("executionRoleArn")
            .withNetworkMode("networkMode")
            .withContainerDefinitions(containerDefinition)
            .withVolumes(asList(volume))
            .withPlacementConstraints(new TaskDefinitionPlacementConstraint())
            .withRequiresCompatibilities("requiresCompatibility")
            .withCpu("256")
            .withMemory("memory")
            .withPidMode("pidMode")
            .withIpcMode("ipcMode")
            .withProxyConfiguration(new ProxyConfiguration())
            .withInferenceAccelerators(new InferenceAccelerator());

    String taskDefinitionJson =
        EcsContainerTaskUtils.convertRegisterTaskDefinitionRequestAsPrettyJson(registerTaskDefinitionRequest);

    TaskDefinition taskDefinition = JsonUtils.asObject(taskDefinitionJson, TaskDefinition.class);

    assertThat(taskDefinition.getFamily()).isEqualTo(registerTaskDefinitionRequest.getFamily());
    assertThat(taskDefinition.getTaskRoleArn()).isEqualTo(registerTaskDefinitionRequest.getTaskRoleArn());
    assertThat(taskDefinition.getExecutionRoleArn()).isEqualTo(registerTaskDefinitionRequest.getExecutionRoleArn());
    assertThat(taskDefinition.getNetworkMode()).isEqualTo(registerTaskDefinitionRequest.getNetworkMode());
    assertThat(taskDefinition.getContainerDefinitions())
        .isEqualTo(registerTaskDefinitionRequest.getContainerDefinitions());
    assertThat(taskDefinition.getVolumes()).isEqualTo(registerTaskDefinitionRequest.getVolumes());
    assertThat(taskDefinition.getPlacementConstraints())
        .isEqualTo(registerTaskDefinitionRequest.getPlacementConstraints());
    assertThat(taskDefinition.getRequiresCompatibilities())
        .isEqualTo(registerTaskDefinitionRequest.getRequiresCompatibilities());
    assertThat(taskDefinition.getCpu()).isEqualTo(registerTaskDefinitionRequest.getCpu());
    assertThat(taskDefinition.getMemory()).isEqualTo(registerTaskDefinitionRequest.getMemory());
    assertThat(taskDefinition.getPidMode()).isEqualTo(registerTaskDefinitionRequest.getPidMode());
    assertThat(taskDefinition.getIpcMode()).isEqualTo(registerTaskDefinitionRequest.getIpcMode());
    assertThat(taskDefinition.getProxyConfiguration()).isEqualTo(registerTaskDefinitionRequest.getProxyConfiguration());
    assertThat(taskDefinition.getInferenceAccelerators())
        .isEqualTo(registerTaskDefinitionRequest.getInferenceAccelerators());
  }
}

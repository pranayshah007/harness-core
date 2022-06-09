/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.common.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.common.CIExecutionConstants.PORT_PREFIX;
import static io.harness.common.CIExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.common.CIExecutionConstants.STEP_WORK_DIR;
import static io.harness.common.CIExecutionConstants.UNIX_STEP_COMMAND;
import static io.harness.common.CIExecutionConstants.WIN_STEP_COMMAND;
import static io.harness.delegate.beans.ci.pod.CIContainerType.PLUGIN;
import static io.harness.delegate.beans.ci.pod.CIContainerType.RUN;

import static java.util.Arrays.asList;
import static org.assertj.core.util.Lists.newArrayList;

import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.stages.IntegrationStageConfigImpl;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.k8s.model.ImageDetails;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class K8InitializeStepUtilsHelper {
  private static final String BUILD_SCRIPT = "mvn clean install";
  public static final Integer DEFAULT_LIMIT_MILLI_CPU = 200;
  public static final Integer DEFAULT_LIMIT_MEMORY_MIB = 200;
  private static final long BUILD_NUMBER = 20;

  private static final String REPO_BRANCH = "master";
  private static final String RUN_STEP_IMAGE = "maven:3.6.3-jdk-8";
  private static final String RUN_STEP_CONNECTOR = "run";
  private static final String RUN_STEP_ID = "step-2";
  private static final String RUN_STEP_NAME = "test script";
  private static final ImageDetails imageDetails = ImageDetails.builder().name("maven").tag("3.6.3-jdk-8").build();

  private static final String PLUGIN_STEP_IMAGE = "plugins/git";
  private static final String PLUGIN_STEP_ID = "step-3";
  private static final String PLUGIN_STEP_NAME = "plugin step";
  private static final String GIT_CLONE_IMAGE = "drone/git";

  public static final Integer PLUGIN_STEP_LIMIT_MEM = 50;
  public static final Integer PLUGIN_STEP_LIMIT_CPU = 100;
  private static final String PLUGIN_STEP_LIMIT_MEM_STRING = "50Mi";
  private static final String PLUGIN_STEP_LIMIT_CPU_STRING = "100m";
  private static final String PLUGIN_ENV_VAR = "foo";
  private static final String PLUGIN_ENV_VAL = "bar";

  private static final Integer GIT_STEP_LIMIT_MEM = 250;
  private static final Integer GIT_STEP_LIMIT_CPU = 300;

  public static StageElementConfig getIntegrationStageElementConfig() {
    return StageElementConfig.builder().identifier("ciStage").type("CI").stageType(getIntegrationStageConfig()).build();
  }

  public static IntegrationStageConfig getIntegrationStageConfig() {
    List<ExecutionWrapperConfig> executionSectionList = getExecutionWrapperConfigList();
    return IntegrationStageConfigImpl.builder()
        .execution(ExecutionElementConfig.builder().steps(executionSectionList).build())
        .build();
  }

  public static List<ExecutionWrapperConfig> getExecutionWrapperConfigList() {
    return newArrayList(ExecutionWrapperConfig.builder().step(getGitCloneStepElementConfigAsJsonNode()).build(),
        ExecutionWrapperConfig.builder().parallel(getRunAndPluginStepsInParallelAsJsonNode()).build());
  }

  public static CIExecutionArgs getCIExecutionArgs() {
    return CIExecutionArgs.builder()
        .executionSource(ManualExecutionSource.builder().branch(REPO_BRANCH).build())
        .runSequence("20")
        .build();
  }

  private static JsonNode getRunStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", RUN_STEP_ID);

    stepElementConfig.put("type", "Run");
    stepElementConfig.put("name", RUN_STEP_NAME);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", RUN_STEP_ID);
    stepSpecType.put("name", RUN_STEP_NAME);
    stepSpecType.put("command", BUILD_SCRIPT);
    stepSpecType.put("image", RUN_STEP_IMAGE);
    stepSpecType.put("connectorRef", RUN_STEP_CONNECTOR);

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private static JsonNode getPluginStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", PLUGIN_STEP_ID);

    stepElementConfig.put("type", "Plugin");
    stepElementConfig.put("name", PLUGIN_STEP_NAME);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", PLUGIN_STEP_ID);
    stepSpecType.put("name", PLUGIN_STEP_NAME);
    stepSpecType.put("image", PLUGIN_STEP_IMAGE);
    stepSpecType.set("resources",
        mapper.createObjectNode().set("limits",
            mapper.createObjectNode()
                .put("cpu", PLUGIN_STEP_LIMIT_CPU_STRING)
                .put("memory", PLUGIN_STEP_LIMIT_MEM_STRING)));
    stepSpecType.set("settings", mapper.createObjectNode().put(PLUGIN_ENV_VAR, PLUGIN_ENV_VAL));

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static JsonNode getGitCloneStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", GIT_CLONE_STEP_ID);

    stepElementConfig.put("type", "Plugin");
    stepElementConfig.put("name", GIT_CLONE_STEP_NAME);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", GIT_CLONE_STEP_ID);
    stepSpecType.put("name", GIT_CLONE_STEP_NAME);
    stepSpecType.put("image", GIT_CLONE_IMAGE);

    ObjectNode settings = mapper.createObjectNode();

    settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, GIT_CLONE_MANUAL_DEPTH.toString());
    stepSpecType.set("settings", settings);

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static List<ContainerDefinitionInfo> getStepContainers() {
    Integer index = 1;
    return asList(getGitPluginStepContainer(index), getRunStepContainer(index + 1), getPluginStepContainer(index + 2));
  }

  private static ContainerDefinitionInfo getRunStepContainer(Integer index) {
    Integer port = PORT_STARTING_RANGE + index - 1;
    return ContainerDefinitionInfo.builder()
        .containerImageDetails(
            ContainerImageDetails.builder().connectorIdentifier(RUN_STEP_CONNECTOR).imageDetails(imageDetails).build())
        .name("step-" + index.toString())
        .containerType(RUN)
        .args(Arrays.asList(PORT_PREFIX, port.toString()))
        .commands(Arrays.asList(UNIX_STEP_COMMAND))
        .ports(Arrays.asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(DEFAULT_LIMIT_MILLI_CPU)
                                     .resourceLimitMemoryMiB(DEFAULT_LIMIT_MEMORY_MIB)
                                     .build())
        .envVars(getEnvVariables(false))
        .secretVariables(new ArrayList<>())
        .stepIdentifier(RUN_STEP_ID)
        .stepName(RUN_STEP_NAME)
        .build();
  }

  private static ContainerDefinitionInfo getGitPluginStepContainer(Integer index) {
    Map<String, String> envVar = new HashMap<>();
    envVar.putAll(getEnvVariables(false));
    Integer port = PORT_STARTING_RANGE + index - 1;
    return ContainerDefinitionInfo.builder()
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(ImageDetails.builder().name(GIT_CLONE_IMAGE).tag("").build())
                                   .build())
        .name("step-" + index.toString())
        .containerType(PLUGIN)
        .args(Arrays.asList(PORT_PREFIX, port.toString()))
        .commands(Arrays.asList(UNIX_STEP_COMMAND))
        .ports(Arrays.asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(GIT_STEP_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(GIT_STEP_LIMIT_MEM)
                                     .build())
        .envVars(envVar)
        .secretVariables(new ArrayList<>())
        .stepName(GIT_CLONE_STEP_NAME)
        .stepIdentifier(GIT_CLONE_STEP_ID)
        .build();
  }

  private static ContainerDefinitionInfo getPluginStepContainer(Integer index) {
    Map<String, String> envVar = new HashMap<>();
    envVar.putAll(getEnvVariables(false));

    Integer port = PORT_STARTING_RANGE + index - 1;
    return ContainerDefinitionInfo.builder()
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(ImageDetails.builder().name(PLUGIN_STEP_IMAGE).tag("").build())
                                   .build())
        .name("step-" + index.toString())
        .containerType(PLUGIN)
        .args(Arrays.asList(PORT_PREFIX, port.toString()))
        .commands(Arrays.asList(UNIX_STEP_COMMAND))
        .ports(Arrays.asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(PLUGIN_STEP_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(PLUGIN_STEP_LIMIT_MEM)
                                     .build())
        .envVars(envVar)
        .secretVariables(new ArrayList<>())
        .stepIdentifier(PLUGIN_STEP_ID)
        .stepName(PLUGIN_STEP_NAME)
        .build();
  }

  private static JsonNode getRunAndPluginStepsInParallelAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(mapper.createObjectNode().set("step", getRunStepElementConfigAsJsonNode()));
    arrayNode.add(mapper.createObjectNode().set("step", getPluginStepElementConfigAsJsonNode()));

    return arrayNode;
  }

  private static Map<String, String> getEnvVariables(boolean includeWorkspace) {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(DRONE_COMMIT_BRANCH, REPO_BRANCH);
    envVars.put("DRONE_BUILD_NUMBER", Long.toString(BUILD_NUMBER));
    if (includeWorkspace) {
      envVars.put("HARNESS_WORKSPACE", STEP_WORK_DIR);
    }
    return envVars;
  }

  public static List<ContainerDefinitionInfo> getWinStepContainers() {
    Integer index = 1;
    return asList(
        getWinGitPluginStepContainer(index), getWinRunStepContainer(index + 1), getWinPluginStepContainer(index + 2));
  }

  private static ContainerDefinitionInfo getWinRunStepContainer(Integer index) {
    Integer port = PORT_STARTING_RANGE + index - 1;
    return ContainerDefinitionInfo.builder()
        .containerImageDetails(
            ContainerImageDetails.builder().connectorIdentifier(RUN_STEP_CONNECTOR).imageDetails(imageDetails).build())
        .name("step-" + index.toString())
        .containerType(RUN)
        .args(Arrays.asList(PORT_PREFIX, port.toString()))
        .commands(Arrays.asList(WIN_STEP_COMMAND))
        .ports(Arrays.asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(DEFAULT_LIMIT_MILLI_CPU)
                                     .resourceLimitMemoryMiB(DEFAULT_LIMIT_MEMORY_MIB)
                                     .build())
        .envVars(getEnvVariables(false))
        .secretVariables(new ArrayList<>())
        .stepIdentifier(RUN_STEP_ID)
        .stepName(RUN_STEP_NAME)
        .build();
  }

  private static ContainerDefinitionInfo getWinGitPluginStepContainer(Integer index) {
    Map<String, String> envVar = new HashMap<>();
    envVar.putAll(getEnvVariables(false));
    Integer port = PORT_STARTING_RANGE + index - 1;
    return ContainerDefinitionInfo.builder()
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(ImageDetails.builder().name(GIT_CLONE_IMAGE).tag("").build())
                                   .build())
        .name("step-" + index.toString())
        .containerType(PLUGIN)
        .args(Arrays.asList(PORT_PREFIX, port.toString()))
        .commands(Arrays.asList(WIN_STEP_COMMAND))
        .ports(Arrays.asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(GIT_STEP_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(GIT_STEP_LIMIT_MEM)
                                     .build())
        .envVars(envVar)
        .secretVariables(new ArrayList<>())
        .stepName(GIT_CLONE_STEP_NAME)
        .stepIdentifier(GIT_CLONE_STEP_ID)
        .build();
  }

  private static ContainerDefinitionInfo getWinPluginStepContainer(Integer index) {
    Map<String, String> envVar = new HashMap<>();
    envVar.putAll(getEnvVariables(false));

    Integer port = PORT_STARTING_RANGE + index - 1;
    return ContainerDefinitionInfo.builder()
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(ImageDetails.builder().name(PLUGIN_STEP_IMAGE).tag("").build())
                                   .build())
        .name("step-" + index.toString())
        .containerType(PLUGIN)
        .args(Arrays.asList(PORT_PREFIX, port.toString()))
        .commands(Arrays.asList(WIN_STEP_COMMAND))
        .ports(Arrays.asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(PLUGIN_STEP_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(PLUGIN_STEP_LIMIT_MEM)
                                     .build())
        .envVars(envVar)
        .secretVariables(new ArrayList<>())
        .stepIdentifier(PLUGIN_STEP_ID)
        .stepName(PLUGIN_STEP_NAME)
        .build();
  }
}

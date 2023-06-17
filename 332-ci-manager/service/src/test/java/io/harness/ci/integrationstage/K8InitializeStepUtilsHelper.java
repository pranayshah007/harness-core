/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.PORT_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_WORK_DIR;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNIX_STEP_COMMAND;
import static io.harness.ci.commonconstants.CIExecutionConstants.WIN_STEP_COMMAND;
import static io.harness.delegate.beans.ci.pod.CIContainerType.BACKGROUND;
import static io.harness.delegate.beans.ci.pod.CIContainerType.PLUGIN;
import static io.harness.delegate.beans.ci.pod.CIContainerType.RUN;

import static java.util.Arrays.asList;
import static org.assertj.core.util.Lists.newArrayList;

import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.ImageDetails;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

  private static final String BACKGROUND_STEP_LIMIT_MEM = "500Mi";
  private static final String BACKGROUND_STEP_LIMIT_CPU = "300m";

  public static IntegrationStageNode getIntegrationStageNode() {
    return IntegrationStageNode.builder()
        .identifier("ciStage")
        .type(IntegrationStageNode.StepType.CI)
        .integrationStageConfig((IntegrationStageConfigImpl) getIntegrationStageConfig())
        .build();
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

  public static JsonNode getRunStepsInStepGroupAsJsonNode() throws Exception {
    K8InitializeStepUtilsHelper k8InitializeStepUtilsHelper = new K8InitializeStepUtilsHelper();
    String step = k8InitializeStepUtilsHelper.readFile("steps/runStepsInStepGroup.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode arrayNode = mapper.readValue(step, JsonNode.class);

    return arrayNode;
  }

  private static JsonNode getRunStepsInStepGroupAsJsonNode1() throws Exception {
    K8InitializeStepUtilsHelper k8InitializeStepUtilsHelper = new K8InitializeStepUtilsHelper();
    String step = k8InitializeStepUtilsHelper.readFile("steps/runStepsInStepGroup1.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode arrayNode = mapper.readValue(step, JsonNode.class);

    return arrayNode;
  }

  private static JsonNode getRunStepsInStepGroupAsJsonNode2() throws Exception {
    K8InitializeStepUtilsHelper k8InitializeStepUtilsHelper = new K8InitializeStepUtilsHelper();
    String step = k8InitializeStepUtilsHelper.readFile("steps/runStepsInStepGroup2.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode arrayNode = mapper.readValue(step, JsonNode.class);

    return arrayNode;
  }

  private static JsonNode getRunStepsInStepGroupAsJsonNode3() throws Exception {
    K8InitializeStepUtilsHelper k8InitializeStepUtilsHelper = new K8InitializeStepUtilsHelper();
    String step = k8InitializeStepUtilsHelper.readFile("steps/runStepsInStepGroup3.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode arrayNode = mapper.readValue(step, JsonNode.class);

    return arrayNode;
  }

  private static JsonNode getRunStepsInStepGroupAsJsonNode4() throws Exception {
    K8InitializeStepUtilsHelper k8InitializeStepUtilsHelper = new K8InitializeStepUtilsHelper();
    String step = k8InitializeStepUtilsHelper.readFile("steps/runStepsInStepGroup4.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode arrayNode = mapper.readValue(step, JsonNode.class);
    return arrayNode;
  }

  public static List<ExecutionWrapperConfig> getExecutionWrapperConfigListWithStepGroup() throws Exception {
    return newArrayList(ExecutionWrapperConfig.builder().step(getGitCloneStepElementConfigAsJsonNode()).build(),
        ExecutionWrapperConfig.builder().parallel(getRunAndPluginStepsInParallelAsJsonNode()).build(),
        ExecutionWrapperConfig.builder().parallel(getRunAndStepGroupInParallelAsJsonNode()).build(),
        ExecutionWrapperConfig.builder().stepGroup(getRunStepsInStepGroupAsJsonNode()).build());
  }

  public static JsonNode getRunAndStepGroupInParallelAsJsonNode() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode arrayNode = mapper.createArrayNode();
    JsonNode stepElementConfig = getRunStepElementConfigAsJsonNode();
    ((ObjectNode) stepElementConfig).put("identifier", "step-4");
    arrayNode.add(mapper.createObjectNode().set("step", stepElementConfig));
    arrayNode.add(mapper.createObjectNode().set("stepGroup", getRunStepsInStepGroupAsJsonNode2()));

    return arrayNode;
  }

  public static JsonNode getSingleRunStepAsJsonNode() throws Exception {
    return getRunStepElementConfigAsJsonNode();
  }

  public static CIExecutionArgs getCIExecutionArgs() {
    return CIExecutionArgs.builder()
        .executionSource(ManualExecutionSource.builder().branch(REPO_BRANCH).build())
        .runSequence("20")
        .build();
  }

  public static JsonNode getRunStepElementConfigAsJsonNode() {
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

  public static JsonNode getRunStepElementConfigWithVariables() {
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

    Map<String, String> envMap = new HashMap<>();
    envMap.put("stepVar", "stepVar");
    envMap.put("pipelineVar1", "stepVarOverride");
    envMap.put("stageVar3", "9.0");
    stepSpecType.putPOJO("envVariables", envMap);

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

  public static JsonNode getDockerStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "step-docker");

    stepElementConfig.put("type", "BuildAndPushDockerRegistry");
    stepElementConfig.put("name", "docker step");

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", "step-docker");
    stepSpecType.put("name", "step-docker");
    stepSpecType.put("image", "plugins/docker");
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

  public static JsonNode getBackgroundStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", RUN_STEP_ID);

    stepElementConfig.put("type", "Background");
    stepElementConfig.put("name", RUN_STEP_NAME);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", RUN_STEP_ID);
    stepSpecType.put("name", RUN_STEP_NAME);
    stepSpecType.put("command", BUILD_SCRIPT);
    stepSpecType.put("image", RUN_STEP_IMAGE);
    stepSpecType.put("connectorRef", RUN_STEP_CONNECTOR);
    stepSpecType.put("connectorRef", RUN_STEP_CONNECTOR);

    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add("redis-server");

    stepSpecType.set("entrypoint", arrayNode);
    stepSpecType.set("resources",
        mapper.createObjectNode().set("limits",
            mapper.createObjectNode().put("cpu", BACKGROUND_STEP_LIMIT_CPU).put("memory", BACKGROUND_STEP_LIMIT_MEM)));
    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static List<ContainerDefinitionInfo> getStepContainers() {
    Integer index = 1;
    return asList(getGitPluginStepContainer(index), getRunStepContainer(index + 1), getPluginStepContainer(index + 2));
  }

  public static List<ContainerDefinitionInfo> getStepContainersForStepGroup() {
    Integer index = 1;
    ContainerDefinitionInfo step4 = getRunStepContainer(4);
    ContainerDefinitionInfo step5 = getRunStepContainer(5);
    ContainerDefinitionInfo step6 = getRunStepContainer(6);
    ContainerDefinitionInfo step7 = getRunStepContainer(7);
    ContainerDefinitionInfo step8 = getRunStepContainer(8);

    return asList(getGitPluginStepContainer(index), getRunStepContainer(index + 1), getPluginStepContainer(index + 2),
        step4, step5, step6, step7, step8);
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

  public static ContainerDefinitionInfo getBackgroundStepContainer(Integer index) {
    ContainerDefinitionInfo containerDefinitionInfo = getRunStepContainer(index);
    containerDefinitionInfo.setContainerType(BACKGROUND);
    return containerDefinitionInfo;
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

  public static JsonNode getRunAndPluginStepsInParallelAsJsonNode() {
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

  public static IntegrationStageNode getIntegrationStageNodeWithStepGroup() throws Exception {
    return IntegrationStageNode.builder()
        .identifier("ciStage")
        .type(IntegrationStageNode.StepType.CI)
        .integrationStageConfig((IntegrationStageConfigImpl) getIntegrationStageConfigWithStepGroup1())
        .build();
  }

  public static IntegrationStageNode getIntegrationStageNodeWithStepGroup1() throws Exception {
    return IntegrationStageNode.builder()
        .identifier("ciStage")
        .type(IntegrationStageNode.StepType.CI)
        .integrationStageConfig((IntegrationStageConfigImpl) getIntegrationStageConfigWithStepGroup1())
        .build();
  }

  public static IntegrationStageConfig getIntegrationStageConfigWithStepGroup1() throws Exception {
    List<ExecutionWrapperConfig> executionSectionList = getExecutionWrapperConfigListWithStepGroup1();
    return IntegrationStageConfigImpl.builder()
        .execution(ExecutionElementConfig.builder().steps(executionSectionList).build())
        .build();
  }

  public static List<ExecutionWrapperConfig> getExecutionWrapperConfigListWithStepGroup1() throws Exception {
    return newArrayList(ExecutionWrapperConfig.builder().parallel(getTwoStepGroupsInParallelAsJsonNode()).build(),
        ExecutionWrapperConfig.builder().parallel(getRunAndPluginStepsInParallelAsJsonNode()).build(),
        ExecutionWrapperConfig.builder().parallel(getRunAndStepGroupInParallelAsJsonNode()).build());
  }

  public static JsonNode getTwoStepGroupsInParallelAsJsonNode() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(mapper.createObjectNode().set("stepGroup", getRunStepsInStepGroupAsJsonNode()));
    arrayNode.add(mapper.createObjectNode().set("stepGroup", getRunStepsInStepGroupAsJsonNode1()));

    return arrayNode;
  }

  public static List<ExecutionWrapperConfig> getExecutionWrapperConfigListWithStepGroup2() throws Exception {
    return newArrayList(ExecutionWrapperConfig.builder().step(getRunStepElementConfigAsJsonNode()).build(),
        ExecutionWrapperConfig.builder().stepGroup(getRunStepsInStepGroupAsJsonNode3()).build());
  }

  public static List<ExecutionWrapperConfig> getExecutionWrapperConfigListWithNestedStepGroup() throws Exception {
    return newArrayList(ExecutionWrapperConfig.builder().stepGroup(getRunStepsInStepGroupAsJsonNode4()).build());
  }

  public static List<ExecutionWrapperConfig> getExecutionWrapperConfigListWithStrategy() throws Exception {
    K8InitializeStepUtilsHelper k8InitializeStepUtilsHelper = new K8InitializeStepUtilsHelper();
    String step1String = k8InitializeStepUtilsHelper.readFile("strategy/stepWithStrategy1.json");
    String step2String = k8InitializeStepUtilsHelper.readFile("strategy/stepWithStrategy2.json");
    String step3String = k8InitializeStepUtilsHelper.readFile("strategy/stepWithStrategy3.json");
    String stepGroup1String = k8InitializeStepUtilsHelper.readFile("strategy/stepGroupWithStrategy1.json");
    String stepGroup2String = k8InitializeStepUtilsHelper.readFile("strategy/stepGroupWithStrategy2.json");
    String stepGroup3String = k8InitializeStepUtilsHelper.readFile("strategy/stepGroupWithStrategy3.json");
    ObjectMapper mapper = new ObjectMapper();
    ExecutionWrapperConfig step1 = mapper.readValue(step1String, ExecutionWrapperConfig.class);
    ExecutionWrapperConfig step2 = mapper.readValue(step2String, ExecutionWrapperConfig.class);
    ExecutionWrapperConfig step3 = mapper.readValue(step3String, ExecutionWrapperConfig.class);
    ExecutionWrapperConfig stepGroup1 = mapper.readValue(stepGroup1String, ExecutionWrapperConfig.class);
    ExecutionWrapperConfig stepGroup2 = mapper.readValue(stepGroup2String, ExecutionWrapperConfig.class);
    ExecutionWrapperConfig stepGroup3 = mapper.readValue(stepGroup3String, ExecutionWrapperConfig.class);
    step1.setUuid("MAB-xgo2QTyxGP5ER1ZHdg");
    step2.setUuid("MAB-xgo2QTyxGP5ER1ZHdg");
    step3.setUuid("MAB-xgo2QTyxGP5ER1ZHdg");
    stepGroup1.setUuid("18R7LnNLTu6dZpd4Nvjp_A");
    stepGroup2.setUuid("18R7LnNLTu6dZpd4Nvjp_A");
    stepGroup3.setUuid("18R7LnNLTu6dZpd4Nvjp_A");
    return newArrayList(step1, step2, step3, stepGroup1, stepGroup2, stepGroup3);
  }

  public String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}

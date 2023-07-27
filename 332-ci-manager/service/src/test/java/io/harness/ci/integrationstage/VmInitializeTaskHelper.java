/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_NAME;

import static org.assertj.core.util.Lists.newArrayList;

import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.ImageDetails;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class VmInitializeTaskHelper {
  public static final String POOL_NAME = "test";
  private static final String BUILD_SCRIPT = "mvn clean install";

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

  public static StageElementConfig getIntegrationStageElementConfig() {
    return StageElementConfig.builder().identifier("ciStage").type("CI").stageType(getIntegrationStageConfig()).build();
  }

  public static IntegrationStageConfig getIntegrationStageConfig() {
    List<ExecutionWrapperConfig> executionSectionList = getExecutionWrapperConfigList();
    return IntegrationStageConfigImpl.builder()
        .execution(ExecutionElementConfig.builder().steps(executionSectionList).build())
        .build();
  }

  public static IntegrationStageConfig getIntegrationStageConfigWithStepGroup() throws Exception {
    List<ExecutionWrapperConfig> executionSectionList =
        newArrayList(ExecutionWrapperConfig.builder().step(getGitCloneStepElementConfigAsJsonNode()).build(),
            ExecutionWrapperConfig.builder().stepGroup(getStepGroupAsJsonNode()).build());
    return IntegrationStageConfigImpl.builder()
        .execution(ExecutionElementConfig.builder().steps(executionSectionList).build())
        .build();
  }

  private static JsonNode getStepGroupAsJsonNode() throws Exception {
    VmInitializeTaskHelper vmInitializeTaskHelper = new VmInitializeTaskHelper();
    String step = vmInitializeTaskHelper.readFile("steps/runStepsInStepGroup3.json");
    ObjectMapper mapper = new ObjectMapper();
    JsonNode arrayNode = mapper.readValue(step, JsonNode.class);

    return arrayNode;
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

  private static JsonNode getRunAndPluginStepsInParallelAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(mapper.createObjectNode().set("step", getRunStepElementConfigAsJsonNode()));
    arrayNode.add(mapper.createObjectNode().set("step", getPluginStepElementConfigAsJsonNode()));

    return arrayNode;
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

  public static InitializeStepInfo getInitializeStepWithLinuxPoolName() {
    VmInfraYaml vmInfraYaml = VmInfraYaml.builder().spec(getPoolWithName(POOL_NAME)).build();
    return InitializeStepInfo.builder().infrastructure(vmInfraYaml).build();
  }

  public static InitializeStepInfo getInitializeStepWithMacPoolName() {
    VmPoolYaml vmPoolYaml = getPoolWithName(POOL_NAME);
    vmPoolYaml.getSpec().setOs(ParameterField.createValueField(OSType.MacOS));
    VmInfraYaml vmInfraYaml = VmInfraYaml.builder().spec(vmPoolYaml).build();
    return InitializeStepInfo.builder().infrastructure(vmInfraYaml).build();
  }

  private static VmPoolYaml getPoolWithName(String poolName) {
    return VmPoolYaml.builder()
        .spec(VmPoolYamlSpec.builder()
                  .harnessImageConnectorRef(ParameterField.<String>builder().build())
                  .poolName(ParameterField.createValueField(poolName))
                  .build())
        .build();
  }

  private static VmPoolYaml getPoolWithId(String poolId) {
    return VmPoolYaml.builder()
        .spec(VmPoolYamlSpec.builder()
                  .harnessImageConnectorRef(ParameterField.<String>builder().build())
                  .identifier(poolId)
                  .build())
        .build();
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  public static Infrastructure getVMInfra(OSType os) {
    VmPoolYaml vmPoolYaml = getPoolWithName(POOL_NAME);
    vmPoolYaml.getSpec().setOs(ParameterField.createValueField(os));
    VmInfraYaml vmInfraYaml = VmInfraYaml.builder().spec(vmPoolYaml).build();
    return vmInfraYaml;
  }

  public static Infrastructure getHostedInfra(OSType os) {
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(os)).build()))
                      .build())
            .build();

    return hostedVmInfraYaml;
  }
}

/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.ci.integrationstage.K8InitializeStepUtilsHelper.DEFAULT_LIMIT_MEMORY_MIB;
import static io.harness.ci.integrationstage.K8InitializeStepUtilsHelper.DEFAULT_LIMIT_MILLI_CPU;
import static io.harness.ci.integrationstage.K8InitializeStepUtilsHelper.PLUGIN_STEP_LIMIT_CPU;
import static io.harness.ci.integrationstage.K8InitializeStepUtilsHelper.PLUGIN_STEP_LIMIT_MEM;
import static io.harness.ci.integrationstage.K8InitializeStepUtilsHelper.getRunStepElementConfigWithVariables;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;

import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.utils.PortFinder;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.matrix.StrategyExpansionData;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.NumberNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.powermock.reflect.Whitebox;

public class K8InitializeStepUtilsTest extends CIExecutionTestBase {
  private static Integer PORT_STARTING_RANGE = 20002;

  @Inject private K8InitializeStepUtils k8InitializeStepUtils;

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void createStepContainerDefinitions() {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigList();
    IntegrationStageNode stageNode = K8InitializeStepUtilsHelper.getIntegrationStageNode();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    List<ContainerDefinitionInfo> expected = K8InitializeStepUtilsHelper.getStepContainers();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        initializeStepInfo, stageNode, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);

    assertThat(stepContainers).isEqualTo(expected);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void createStepContainerDefinitionsForBackgroundStep() {
    List<ExecutionWrapperConfig> steps =
        Collections.singletonList(ExecutionWrapperConfig.builder()
                                      .step(K8InitializeStepUtilsHelper.getBackgroundStepElementConfigAsJsonNode())
                                      .build());
    IntegrationStageNode stageNode = K8InitializeStepUtilsHelper.getIntegrationStageNode();
    IntegrationStageConfigImpl integrationStageConfigImpl = stageNode.getIntegrationStageConfig();
    integrationStageConfigImpl.setExecution(ExecutionElementConfig.builder().steps(steps).build());
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    ContainerDefinitionInfo containerDefinitionInfo = K8InitializeStepUtilsHelper.getBackgroundStepContainer(1);
    containerDefinitionInfo.setContainerResourceParams(ContainerResourceParams.builder()
                                                           .resourceRequestMemoryMiB(500)
                                                           .resourceRequestMilliCpu(300)
                                                           .resourceLimitMemoryMiB(500)
                                                           .resourceLimitMilliCpu(300)
                                                           .build());

    List<ContainerDefinitionInfo> expected = Collections.singletonList(containerDefinitionInfo);
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        initializeStepInfo, stageNode, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);

    assertThat(stepContainers).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testStepGroupWithParallelSteps() throws Exception {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup();
    IntegrationStageNode stageNode = K8InitializeStepUtilsHelper.getIntegrationStageNodeWithStepGroup();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();

    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        initializeStepInfo, stageNode, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);

    HashMap<String, ContainerResourceParams> map = populateMap(stepContainers);

    assertThat(map.get("harness-git-clone").getResourceLimitMemoryMiB()).isEqualTo(500);
    assertThat(map.get("harness-git-clone").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("step-2").getResourceLimitMemoryMiB()).isEqualTo(325);
    assertThat(map.get("step-2").getResourceLimitMilliCpu()).isEqualTo(250);
    assertThat(map.get("step-3").getResourceLimitMemoryMiB()).isEqualTo(175);
    assertThat(map.get("step-3").getResourceLimitMilliCpu()).isEqualTo(150);
    assertThat(map.get("step-4").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("step-4").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_grup2_run21").getResourceLimitMemoryMiB()).isEqualTo(300);
    assertThat(map.get("step_grup2_run21").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_grup2_run22").getResourceLimitMemoryMiB()).isEqualTo(300);
    assertThat(map.get("step_grup2_run22").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_grup1_run2").getResourceLimitMemoryMiB()).isEqualTo(500);
    assertThat(map.get("step_grup1_run2").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("step_grup1_run1").getResourceLimitMemoryMiB()).isEqualTo(500);
    assertThat(map.get("step_grup1_run1").getResourceLimitMilliCpu()).isEqualTo(400);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testStepGroup() throws Exception {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup2();
    IntegrationStageNode stageNode = K8InitializeStepUtilsHelper.getIntegrationStageNodeWithStepGroup1();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();

    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        initializeStepInfo, stageNode, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);

    HashMap<String, ContainerResourceParams> map = populateMap(stepContainers);

    assertThat(map.get("step-2").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("step-2").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("step_g_run2").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("step_g_run2").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("step_g_run3").getResourceLimitMemoryMiB()).isEqualTo(100);
    assertThat(map.get("step_g_run3").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_g_run4").getResourceLimitMemoryMiB()).isEqualTo(100);
    assertThat(map.get("step_g_run4").getResourceLimitMilliCpu()).isEqualTo(200);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();
    IntegrationStageConfig integrationStageConfig =
        IntegrationStageConfigImpl.builder().execution(executionElementConfig).build();
    List<String> identifiers =
        IntegrationStageUtils.getStepIdentifiers(integrationStageConfig.getExecution().getSteps());
    assertThat(identifiers).isNotEmpty();
    assertThat(identifiers.get(0)).isEqualTo("step-2");
    assertThat(identifiers.get(1)).isEqualTo("step_g_run2");
    assertThat(identifiers.get(2)).isEqualTo("step_g_run3");
    assertThat(identifiers.get(3)).isEqualTo("step_g_run4");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNestedStepGroup() throws Exception {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithNestedStepGroup();
    IntegrationStageNode stageNode = K8InitializeStepUtilsHelper.getIntegrationStageNodeWithStepGroup1();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        initializeStepInfo, stageNode, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);

    HashMap<String, ContainerResourceParams> map = populateMap(stepContainers);
    assertThat(map.get("step_g_run2").getResourceLimitMemoryMiB()).isEqualTo(150);
    assertThat(map.get("step_g_run2").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_g_sg1_run3").getResourceLimitMemoryMiB()).isEqualTo(150);
    assertThat(map.get("step_g_sg1_run3").getResourceLimitMilliCpu()).isEqualTo(200);

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(steps).build();
    IntegrationStageConfig integrationStageConfig =
        IntegrationStageConfigImpl.builder().execution(executionElementConfig).build();
    List<String> identifiers =
        IntegrationStageUtils.getStepIdentifiers(integrationStageConfig.getExecution().getSteps());
    assertThat(identifiers).isNotEmpty();
    assertThat(identifiers.get(0)).isEqualTo("step_g_run2");
    assertThat(identifiers.get(1)).isEqualTo("step_g_sg1_run3");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testParallelStepGroups() throws Exception {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup1();
    IntegrationStageNode stageNode = K8InitializeStepUtilsHelper.getIntegrationStageNodeWithStepGroup1();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();

    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        initializeStepInfo, stageNode, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);

    HashMap<String, ContainerResourceParams> map = populateMap(stepContainers);

    assertThat(map.get("step-2").getResourceLimitMemoryMiB()).isEqualTo(325);
    assertThat(map.get("step-2").getResourceLimitMilliCpu()).isEqualTo(250);
    assertThat(map.get("step-3").getResourceLimitMemoryMiB()).isEqualTo(175);
    assertThat(map.get("step-3").getResourceLimitMilliCpu()).isEqualTo(150);
    assertThat(map.get("step-4").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("step-4").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_grup2_run21").getResourceLimitMemoryMiB()).isEqualTo(300);
    assertThat(map.get("step_grup2_run21").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_grup2_run22").getResourceLimitMemoryMiB()).isEqualTo(300);
    assertThat(map.get("step_grup2_run22").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_grup1_run2").getResourceLimitMemoryMiB()).isEqualTo(375);
    assertThat(map.get("step_grup1_run2").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_grup1_run1").getResourceLimitMemoryMiB()).isEqualTo(375);
    assertThat(map.get("step_grup1_run1").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_grup3_run32").getResourceLimitMemoryMiB()).isEqualTo(125);
    assertThat(map.get("step_grup3_run32").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_grup3_run31").getResourceLimitMemoryMiB()).isEqualTo(125);
    assertThat(map.get("step_grup3_run31").getResourceLimitMilliCpu()).isEqualTo(200);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testStepsWithStrategy() throws Exception {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStrategy();
    IntegrationStageNode stageNode = K8InitializeStepUtilsHelper.getIntegrationStageNodeWithStepGroup1();
    Map<String, StrategyExpansionData> strategyExpansionMap = new HashMap<>();
    strategyExpansionMap.put("MAB-xgo2QTyxGP5ER1ZHdg", StrategyExpansionData.builder().maxConcurrency(2).build());
    strategyExpansionMap.put("lBUdHaJlRMufGBG4u3uydA", StrategyExpansionData.builder().maxConcurrency(3).build());
    strategyExpansionMap.put("18R7LnNLTu6dZpd4Nvjp_A", StrategyExpansionData.builder().maxConcurrency(2).build());
    strategyExpansionMap.put("jaYM93ZLQkehPEcEKJOKmg", StrategyExpansionData.builder().maxConcurrency(1).build());
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .strategyExpansionMap(strategyExpansionMap)
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        initializeStepInfo, stageNode, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);
    assertThat(stepContainers.size()).isEqualTo(15);
    Pair<Integer, Integer> wrapperRequest = k8InitializeStepUtils.getStageRequest(initializeStepInfo, "test");
    assertThat(wrapperRequest.getLeft()).isEqualTo(1600);
    assertThat(wrapperRequest.getRight()).isEqualTo(2500);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void createWinStepContainerDefinitions() {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigList();
    IntegrationStageNode stageNode = K8InitializeStepUtilsHelper.getIntegrationStageNode();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    List<ContainerDefinitionInfo> expected = K8InitializeStepUtilsHelper.getWinStepContainers();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        initializeStepInfo, stageNode, ciExecutionArgs, portFinder, "test", OSType.Windows, ambiance, 0);

    assertThat(stepContainers).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testGetStageMemoryRequest() {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigList();

    Integer expected = PLUGIN_STEP_LIMIT_MEM + DEFAULT_LIMIT_MEMORY_MIB;
    Integer stageMemoryRequest = k8InitializeStepUtils.getStageMemoryRequest(steps, "test");

    assertThat(stageMemoryRequest).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testGetStageCpuRequest() {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigList();

    Integer expected = PLUGIN_STEP_LIMIT_CPU + DEFAULT_LIMIT_MILLI_CPU;
    Integer stageCpuRequest = k8InitializeStepUtils.getStageCpuRequest(steps, "test");

    assertThat(stageCpuRequest).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetStepConnectorRefs() throws Exception {
    List<ExecutionWrapperConfig> wrapperConfigs =
        K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup1();

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepGroupElementConfig = mapper.createObjectNode();

    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(
        mapper.createObjectNode().set("step", K8InitializeStepUtilsHelper.getDockerStepElementConfigAsJsonNode()));

    stepGroupElementConfig.put("identifier", "step-group1");
    stepGroupElementConfig.put("steps", arrayNode);

    wrapperConfigs.add(ExecutionWrapperConfig.builder().stepGroup(stepGroupElementConfig).build());
    wrapperConfigs.add(ExecutionWrapperConfig.builder()
                           .step(K8InitializeStepUtilsHelper.getDockerStepElementConfigAsJsonNode())
                           .build());

    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(wrapperConfigs).build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, List<ConnectorConversionInfo>> stepConnectorRefs =
        k8InitializeStepUtils.getStepConnectorRefs(executionElementConfig, ambiance);
    assertThat(stepConnectorRefs.size()).isEqualTo(2);
    assertThat(stepConnectorRefs.containsKey("step-docker")).isTrue();
    assertThat(stepConnectorRefs.containsKey("step-group1_step-docker")).isTrue();
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetExecutionWrapperCpuRequest() throws Exception {
    ExecutionWrapperConfig executionWrapperConfig =
        ExecutionWrapperConfig.builder()
            .parallel(K8InitializeStepUtilsHelper.getTwoStepGroupsInParallelAsJsonNode())
            .build();
    int cpu =
        Whitebox.invokeMethod(k8InitializeStepUtils, "getExecutionWrapperCpuRequest", executionWrapperConfig, "acct");
    assertThat(cpu).isEqualTo(400);

    executionWrapperConfig = ExecutionWrapperConfig.builder()
                                 .parallel(K8InitializeStepUtilsHelper.getRunAndStepGroupInParallelAsJsonNode())
                                 .build();
    cpu = Whitebox.invokeMethod(k8InitializeStepUtils, "getExecutionWrapperCpuRequest", executionWrapperConfig, "acct");
    assertThat(cpu).isEqualTo(400);

    executionWrapperConfig = ExecutionWrapperConfig.builder()
                                 .parallel(K8InitializeStepUtilsHelper.getRunAndPluginStepsInParallelAsJsonNode())
                                 .build();
    cpu = Whitebox.invokeMethod(k8InitializeStepUtils, "getExecutionWrapperCpuRequest", executionWrapperConfig, "acct");
    assertThat(cpu).isEqualTo(300);

    executionWrapperConfig = ExecutionWrapperConfig.builder()
                                 .stepGroup(K8InitializeStepUtilsHelper.getRunStepsInStepGroupAsJsonNode())
                                 .build();
    cpu = Whitebox.invokeMethod(k8InitializeStepUtils, "getExecutionWrapperCpuRequest", executionWrapperConfig, "acct");
    assertThat(cpu).isEqualTo(200);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetExecutionWrapperMemoryRequest() throws Exception {
    ExecutionWrapperConfig executionWrapperConfig =
        ExecutionWrapperConfig.builder()
            .parallel(K8InitializeStepUtilsHelper.getTwoStepGroupsInParallelAsJsonNode())
            .build();
    int memory = Whitebox.invokeMethod(
        k8InitializeStepUtils, "getExecutionWrapperMemoryRequest", executionWrapperConfig, "acct");
    assertThat(memory).isEqualTo(350);

    executionWrapperConfig = ExecutionWrapperConfig.builder()
                                 .parallel(K8InitializeStepUtilsHelper.getRunAndStepGroupInParallelAsJsonNode())
                                 .build();
    memory = Whitebox.invokeMethod(
        k8InitializeStepUtils, "getExecutionWrapperMemoryRequest", executionWrapperConfig, "acct");
    assertThat(memory).isEqualTo(500);

    executionWrapperConfig = ExecutionWrapperConfig.builder()
                                 .parallel(K8InitializeStepUtilsHelper.getRunAndPluginStepsInParallelAsJsonNode())
                                 .build();
    memory = Whitebox.invokeMethod(
        k8InitializeStepUtils, "getExecutionWrapperMemoryRequest", executionWrapperConfig, "acct");
    assertThat(memory).isEqualTo(250);

    executionWrapperConfig = ExecutionWrapperConfig.builder()
                                 .stepGroup(K8InitializeStepUtilsHelper.getRunStepsInStepGroupAsJsonNode())
                                 .build();
    memory = Whitebox.invokeMethod(
        k8InitializeStepUtils, "getExecutionWrapperMemoryRequest", executionWrapperConfig, "acct");
    assertThat(memory).isEqualTo(300);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testVariablePopulation() {
    List<ExecutionWrapperConfig> steps =
        newArrayList(ExecutionWrapperConfig.builder().step(getRunStepElementConfigWithVariables()).build());

    IntegrationStageNode stageNode = K8InitializeStepUtilsHelper.getIntegrationStageNode();

    List<NGVariable> pipelineVariables = new ArrayList<>();
    pipelineVariables.add(StringNGVariable.builder()
                              .name("pipelineVar1")
                              .type(NGVariableType.STRING)
                              .value(ParameterField.<String>builder().value("pipelineVar1").build())
                              .build());
    pipelineVariables.add(StringNGVariable.builder()
                              .name("pipelineVar2")
                              .type(NGVariableType.STRING)
                              .value(ParameterField.<String>builder().value("pipelineVar2").build())
                              .build());
    pipelineVariables.add(NumberNGVariable.builder()
                              .name("pipelineVar3")
                              .type(NGVariableType.NUMBER)
                              .value(ParameterField.<Double>builder().value(1.0).build())
                              .build());

    List<NGVariable> stageVariables = new ArrayList<>();
    stageVariables.add(StringNGVariable.builder()
                           .name("pipelineVar1")
                           .type(NGVariableType.STRING)
                           .value(ParameterField.<String>builder().value("stageVar1").build())
                           .build());
    stageVariables.add(StringNGVariable.builder()
                           .name("pipelineVar2")
                           .type(NGVariableType.STRING)
                           .value(ParameterField.<String>builder().value("stageVar2").build())
                           .build());
    stageVariables.add(NumberNGVariable.builder()
                           .name("stageVar3")
                           .type(NGVariableType.NUMBER)
                           .value(ParameterField.<Double>builder().value(1.0).build())
                           .build());
    stageVariables.add(NumberNGVariable.builder()
                           .name("stageVar4")
                           .type(NGVariableType.NUMBER)
                           .value(ParameterField.<Double>builder().value(2.0).build())
                           .build());

    stageNode.setVariables(stageVariables);
    stageNode.setPipelineVariables(pipelineVariables);

    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        initializeStepInfo, stageNode, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);
    Map<String, String> envVars = stepContainers.get(0).getEnvVars();
    assertThat(envVars.get("pipelineVar1")).isEqualTo("stepVarOverride");
    assertThat(envVars.get("pipelineVar2")).isEqualTo("stageVar2");
    assertThat(envVars.get("pipelineVar3")).isEqualTo("1.0");
    assertThat(envVars.get("stageVar3")).isEqualTo("9.0");
    assertThat(envVars.get("stageVar4")).isEqualTo("2.0");
    assertThat(envVars.get("stepVar")).isEqualTo("stepVar");
  }

  private HashMap<String, ContainerResourceParams> populateMap(List<ContainerDefinitionInfo> stepContainers) {
    HashMap<String, ContainerResourceParams> map = new HashMap<>();
    for (ContainerDefinitionInfo containerDefinitionInfo : stepContainers) {
      map.put(containerDefinitionInfo.getStepIdentifier(), containerDefinitionInfo.getContainerResourceParams());
    }
    return map;
  }
}

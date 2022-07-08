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
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.stages.IntegrationStageConfigImpl;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.utils.PortFinder;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    StageElementConfig integrationStageConfig = K8InitializeStepUtilsHelper.getIntegrationStageElementConfig();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    List<ContainerDefinitionInfo> expected = K8InitializeStepUtilsHelper.getStepContainers();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Linux);

    assertThat(stepContainers).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testStepGroupWithParallelSteps() throws Exception {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup();
    StageElementConfig integrationStageConfig =
        K8InitializeStepUtilsHelper.getIntegrationStageElementConfigWithStepGroup();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitionsStepGroupWithFF(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Linux, 0);

    HashMap<String, ContainerResourceParams> map = populateMap(stepContainers);

    assertThat(map.get("harness-git-clone").getResourceLimitMemoryMiB()).isEqualTo(500);
    assertThat(map.get("harness-git-clone").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("step-2").getResourceLimitMemoryMiB()).isEqualTo(325);
    assertThat(map.get("step-2").getResourceLimitMilliCpu()).isEqualTo(250);
    assertThat(map.get("step-3").getResourceLimitMemoryMiB()).isEqualTo(175);
    assertThat(map.get("step-3").getResourceLimitMilliCpu()).isEqualTo(150);
    assertThat(map.get("step-4").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("step-4").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run21").getResourceLimitMemoryMiB()).isEqualTo(300);
    assertThat(map.get("run21").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run22").getResourceLimitMemoryMiB()).isEqualTo(300);
    assertThat(map.get("run22").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run2").getResourceLimitMemoryMiB()).isEqualTo(500);
    assertThat(map.get("run2").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("run1").getResourceLimitMemoryMiB()).isEqualTo(500);
    assertThat(map.get("run1").getResourceLimitMilliCpu()).isEqualTo(400);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testStepGroup() throws Exception {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup2();
    StageElementConfig integrationStageConfig =
        K8InitializeStepUtilsHelper.getIntegrationStageElementConfigWithStepGroup1();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitionsStepGroupWithFF(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Linux, 0);

    HashMap<String, ContainerResourceParams> map = populateMap(stepContainers);

    assertThat(map.get("step-2").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("step-2").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("run2").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("run2").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("run3").getResourceLimitMemoryMiB()).isEqualTo(100);
    assertThat(map.get("run3").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run4").getResourceLimitMemoryMiB()).isEqualTo(100);
    assertThat(map.get("run4").getResourceLimitMilliCpu()).isEqualTo(200);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testParallelStepGroups() throws Exception {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup1();
    StageElementConfig integrationStageConfig =
        K8InitializeStepUtilsHelper.getIntegrationStageElementConfigWithStepGroup1();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitionsStepGroupWithFF(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Linux, 0);

    HashMap<String, ContainerResourceParams> map = populateMap(stepContainers);

    assertThat(map.get("step-2").getResourceLimitMemoryMiB()).isEqualTo(325);
    assertThat(map.get("step-2").getResourceLimitMilliCpu()).isEqualTo(250);
    assertThat(map.get("step-3").getResourceLimitMemoryMiB()).isEqualTo(175);
    assertThat(map.get("step-3").getResourceLimitMilliCpu()).isEqualTo(150);
    assertThat(map.get("step-4").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("step-4").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run21").getResourceLimitMemoryMiB()).isEqualTo(300);
    assertThat(map.get("run21").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run22").getResourceLimitMemoryMiB()).isEqualTo(300);
    assertThat(map.get("run22").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run2").getResourceLimitMemoryMiB()).isEqualTo(375);
    assertThat(map.get("run2").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run1").getResourceLimitMemoryMiB()).isEqualTo(375);
    assertThat(map.get("run1").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run32").getResourceLimitMemoryMiB()).isEqualTo(125);
    assertThat(map.get("run32").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("run31").getResourceLimitMemoryMiB()).isEqualTo(125);
    assertThat(map.get("run31").getResourceLimitMilliCpu()).isEqualTo(200);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void createWinStepContainerDefinitions() {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigList();
    StageElementConfig integrationStageConfig = K8InitializeStepUtilsHelper.getIntegrationStageElementConfig();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    List<ContainerDefinitionInfo> expected = K8InitializeStepUtilsHelper.getWinStepContainers();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Windows);

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
    wrapperConfigs.add(ExecutionWrapperConfig.builder()
                           .step(K8InitializeStepUtilsHelper.getDockerStepElementConfigAsJsonNode())
                           .build());
    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(wrapperConfigs).build();
    IntegrationStageConfig integrationStageConfig =
        IntegrationStageConfigImpl.builder().execution(executionElementConfig).build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> stepConnectorRefs =
        k8InitializeStepUtils.getStepConnectorRefs(integrationStageConfig, ambiance);
    assertThat(stepConnectorRefs.size()).isEqualTo(1);
    assertThat(stepConnectorRefs.containsKey("step-3")).isTrue();
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

  private HashMap<String, ContainerResourceParams> populateMap(List<ContainerDefinitionInfo> stepContainers) {
    HashMap<String, ContainerResourceParams> map = new HashMap<>();
    for (ContainerDefinitionInfo containerDefinitionInfo : stepContainers) {
      map.put(containerDefinitionInfo.getStepIdentifier(), containerDefinitionInfo.getContainerResourceParams());
    }
    return map;
  }
}

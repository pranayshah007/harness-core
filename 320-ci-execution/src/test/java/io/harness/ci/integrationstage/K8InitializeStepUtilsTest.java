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
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.rule.Owner;
import io.harness.util.PortFinder;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

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
  public void getStageMemoryRequest() {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigList();

    Integer expected = PLUGIN_STEP_LIMIT_MEM + DEFAULT_LIMIT_MEMORY_MIB;
    Integer stageMemoryRequest = k8InitializeStepUtils.getStageMemoryRequest(steps, "test");

    assertThat(stageMemoryRequest).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getStageCpuRequest() {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigList();

    Integer expected = PLUGIN_STEP_LIMIT_CPU + DEFAULT_LIMIT_MILLI_CPU;
    Integer stageCpuRequest = k8InitializeStepUtils.getStageCpuRequest(steps, "test");

    assertThat(stageCpuRequest).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getStepConnectorRefs() {}
}

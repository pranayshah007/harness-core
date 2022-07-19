/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_SSL_NO_VERIFY;
import static io.harness.ci.integrationstage.K8InitializeStepUtilsHelper.DEFAULT_LIMIT_MEMORY_MIB;
import static io.harness.ci.integrationstage.K8InitializeStepUtilsHelper.DEFAULT_LIMIT_MILLI_CPU;
import static io.harness.ci.integrationstage.K8InitializeStepUtilsHelper.PLUGIN_STEP_LIMIT_CPU;
import static io.harness.ci.integrationstage.K8InitializeStepUtilsHelper.PLUGIN_STEP_LIMIT_MEM;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.JAMES_RICKS;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.stages.IntegrationStageConfigImpl;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.utils.PortFinder;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildSpec;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
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
    Ambiance ambiance = Ambiance.newBuilder().build();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance);

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
    Ambiance ambiance = Ambiance.newBuilder().build();

    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitionsStepGroupWithFF(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);

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
    StageElementConfig integrationStageConfig =
        K8InitializeStepUtilsHelper.getIntegrationStageElementConfigWithStepGroup1();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();
    Ambiance ambiance = Ambiance.newBuilder().build();

    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitionsStepGroupWithFF(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);

    HashMap<String, ContainerResourceParams> map = populateMap(stepContainers);

    assertThat(map.get("step-2").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("step-2").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("step_g_run2").getResourceLimitMemoryMiB()).isEqualTo(200);
    assertThat(map.get("step_g_run2").getResourceLimitMilliCpu()).isEqualTo(400);
    assertThat(map.get("step_g_run3").getResourceLimitMemoryMiB()).isEqualTo(100);
    assertThat(map.get("step_g_run3").getResourceLimitMilliCpu()).isEqualTo(200);
    assertThat(map.get("step_g_run4").getResourceLimitMemoryMiB()).isEqualTo(100);
    assertThat(map.get("step_g_run4").getResourceLimitMilliCpu()).isEqualTo(200);
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
    Ambiance ambiance = Ambiance.newBuilder().build();

    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitionsStepGroupWithFF(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Linux, ambiance, 0);

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
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void createWinStepContainerDefinitions() {
    List<ExecutionWrapperConfig> steps = K8InitializeStepUtilsHelper.getExecutionWrapperConfigList();
    StageElementConfig integrationStageConfig = K8InitializeStepUtilsHelper.getIntegrationStageElementConfig();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(new HashSet<>()).build();
    CIExecutionArgs ciExecutionArgs = K8InitializeStepUtilsHelper.getCIExecutionArgs();

    List<ContainerDefinitionInfo> expected = K8InitializeStepUtilsHelper.getWinStepContainers();
    Ambiance ambiance = Ambiance.newBuilder().build();
    List<ContainerDefinitionInfo> stepContainers = k8InitializeStepUtils.createStepContainerDefinitions(
        steps, integrationStageConfig, ciExecutionArgs, portFinder, "test", OSType.Windows, ambiance);

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

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoBuild() {
    testCreatePluginStepInfoBuild("testStepId", BuildType.BRANCH, "myTestBranch", DRONE_COMMIT_BRANCH);
    testCreatePluginStepInfoBuild("testStepId", BuildType.TAG, "myTestTag", DRONE_TAG);
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoBuildEmptyBranch() {
    testCreatePluginStepInfoBuild("testStepId", BuildType.BRANCH, "", DRONE_COMMIT_BRANCH);
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoBuildEmptyTag() {
    testCreatePluginStepInfoBuild("testStepId", BuildType.TAG, "", DRONE_TAG);
  }

  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoBuildPr() {
    testCreatePluginStepInfoBuild("testStepId", BuildType.PR, "1111", "N/A");
  }

  private static void testCreatePluginStepInfoBuild(String stepIdentifier, BuildType buildType, String buildValue,
                                                    String envVarKey) {
    final ParameterField<Build> buildParameter = createBuildParameter(buildType, buildValue);
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().build(buildParameter).identifier(stepIdentifier).build();
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, "testImage", "testAccount");
    assertThat(pluginStepInfo.getEnvVariables().get(envVarKey)).isEqualTo(buildValue);
    final TextNode depth = JsonNodeFactory.instance.textNode(GIT_CLONE_MANUAL_DEPTH.toString());
    assertThat(pluginStepInfo.getSettings().get(GIT_CLONE_DEPTH_ATTRIBUTE)).isEqualTo(depth);
  }

  private static ParameterField<Build> createBuildParameter(BuildType buildType, String value) {
    final ParameterField<String> buildStringParameter = ParameterField.<String>builder().value(value).build();
    BuildSpec buildSpec = null;
    if(BuildType.BRANCH == buildType) {
      buildSpec = BranchBuildSpec.builder().branch(buildStringParameter).build();
    } else if (BuildType.TAG == buildType) {
      buildSpec = TagBuildSpec.builder().tag(buildStringParameter).build();
    } else if (BuildType.PR == buildType) {
      buildSpec = PRBuildSpec.builder().number(buildStringParameter).build();
    }
    final Build build = Build.builder().spec(buildSpec).type(buildType).build();
    return ParameterField.<Build>builder().value(build).build();
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoNoBuild(){
    final GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().identifier("testStepIdentifier").build();
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, "testImage", "testStepId");
    assertThat(pluginStepInfo.getEnvVariables().get(DRONE_COMMIT_BRANCH)).isNull();
    assertThat(pluginStepInfo.getEnvVariables().get(DRONE_TAG)).isNull();
    //depth does not get set to a default if there is no build specified
    assertThat(pluginStepInfo.getSettings().get(GIT_CLONE_DEPTH_ATTRIBUTE)).isNull();
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoSslVerifyFalse(){
    final boolean sslVerifyValue = false;
    final ParameterField<Boolean> sslVerify = ParameterField.<Boolean>builder().value(sslVerifyValue).build();
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().sslVerify(sslVerify).build();
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, "testImage","testAccountId");
    assertThat(pluginStepInfo.getEnvVariables().get(GIT_SSL_NO_VERIFY)).isEqualTo(Boolean.toString(!sslVerifyValue));
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoSslVerifyTrue(){
    final boolean sslVerifyValue = true;
    final ParameterField<Boolean> sslVerify = ParameterField.<Boolean>builder().value(sslVerifyValue).build();
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().sslVerify(sslVerify).build();
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, "testImage","testAccountId");
    //The GIT_SSL_NO_VERIFY env variable does not get set when sslVerify is true
    assertThat(pluginStepInfo.getEnvVariables().get(GIT_SSL_NO_VERIFY)).isNull();
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoSslVerifyNullValue(){
    final ParameterField<Boolean> sslVerify = ParameterField.<Boolean>builder().value(null).build();
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().sslVerify(sslVerify).build();
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, "testImage","testAccountId");
    assertThat(pluginStepInfo.getEnvVariables().get(GIT_SSL_NO_VERIFY)).isNull();
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoSslVerifyNull(){
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().build();
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo,"testImage", "testAccountId");
    assertThat(pluginStepInfo.getEnvVariables().get(GIT_SSL_NO_VERIFY)).isNull();
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoImage() {
    final String image = "testImage";
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().build();
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, image, "testAccountId");
    assertThat(pluginStepInfo.getImage().getValue()).isEqualTo(image);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoImageBlankAccount() {
    final String image = "testImage";
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().build();
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, image, "");
    assertThat(pluginStepInfo.getImage()).isNull();
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoNullImage() {
    final String image = null;
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().build();
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, image, "testAccountId");
    assertThat(pluginStepInfo.getImage().getValue()).isNull();
  }

  private static PluginStepInfo createPluginStepInfo(GitCloneStepInfo gitCloneStepInfo, String image, String accountId) {
    CIExecutionConfigService ciExecutionConfigService = Mockito.mock(CIExecutionConfigService.class);
    StepImageConfig stepImageConfig = StepImageConfig.builder().image(image).build();
    when(ciExecutionConfigService.getPluginVersionForK8(any(), any())).thenReturn(stepImageConfig);
    return K8InitializeStepUtils.createPluginStepInfo(gitCloneStepInfo, ciExecutionConfigService, accountId, null);
  }

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void testCreatePluginStepInfoEntryPoint() {
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder().build();
    String testEntryPoint = "testEntryPoint0";
    String windowsTestEntryPoint = "windowsTestEntryPoint0";
    List<String> entryPoint = new ArrayList<>();
    entryPoint.add(testEntryPoint);
    List<String> windowsEntryPoint = new ArrayList<>();
    windowsEntryPoint.add("windowsTestEntryPoint0");
    final PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, entryPoint, windowsEntryPoint,
            OSType.MacOS);
    assertThat(pluginStepInfo.getEntrypoint().get(0)).isEqualTo(testEntryPoint);

    final PluginStepInfo pluginStepInfoWindows = createPluginStepInfo(gitCloneStepInfo, entryPoint, windowsEntryPoint,
            OSType.Windows);
    assertThat(pluginStepInfoWindows.getEntrypoint().get(0)).isEqualTo(windowsTestEntryPoint);
  }

  private static PluginStepInfo createPluginStepInfo(GitCloneStepInfo gitCloneStepInfo, List<String> entryPoint,
                                                     List<String> windowsEntryPoint, OSType osType) {
    StepImageConfig stepImageConfig = StepImageConfig.builder()
            .windowsEntrypoint(windowsEntryPoint)
            .entrypoint(entryPoint)
            .build();
    final CIStepConfig ciStepConfig = CIStepConfig.builder().gitCloneConfig(stepImageConfig).build();
    final CIExecutionServiceConfig ciExecutionServiceConfig = CIExecutionServiceConfig.builder().stepConfig(ciStepConfig).build();
    CIExecutionConfigService ciExecutionConfigService = Mockito.mock(CIExecutionConfigService.class);
    when(ciExecutionConfigService.getCiExecutionServiceConfig()).thenReturn(ciExecutionServiceConfig);
    return K8InitializeStepUtils.createPluginStepInfo(gitCloneStepInfo, ciExecutionConfigService, null, osType);
  }

  private HashMap<String, ContainerResourceParams> populateMap(List<ContainerDefinitionInfo> stepContainers) {
    HashMap<String, ContainerResourceParams> map = new HashMap<>();
    for (ContainerDefinitionInfo containerDefinitionInfo : stepContainers) {
      map.put(containerDefinitionInfo.getStepIdentifier(), containerDefinitionInfo.getContainerResourceParams());
    }
    return map;
  }
}

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
import static io.harness.rule.OwnerRule.JAMES_RICKS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.CIStepConfig;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.exception.ngexception.CIStageExecutionException;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class InitializeStepUtilsTest {

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
    return InitializeStepUtils.createPluginStepInfo(gitCloneStepInfo, ciExecutionConfigService, accountId, null);
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
    return InitializeStepUtils.createPluginStepInfo(gitCloneStepInfo, ciExecutionConfigService, null, osType);
  }

}

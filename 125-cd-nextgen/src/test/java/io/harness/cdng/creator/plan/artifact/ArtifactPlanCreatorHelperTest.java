/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptInfo;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScriptSourceWrapper;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomArtifactScripts;
import io.harness.cdng.artifact.bean.yaml.customartifact.CustomScriptInlineSource;
import io.harness.cdng.artifact.bean.yaml.customartifact.FetchAllArtifacts;
import io.harness.cdng.artifact.steps.ArtifactStepParameters;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RunWith(JUnitParamsRunner.class)
public class ArtifactPlanCreatorHelperTest extends CategoryTest {
  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getTestNoDelegateTask")
  public void shouldCreateDelegateTask_0(ArtifactStepParameters testCase) {
    boolean should = ArtifactPlanCreatorHelper.shouldCreateDelegateTask(testCase);
    assertThat(should).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getTestCreateDelegateTask")
  public void shouldCreateDelegateTask_1(ArtifactStepParameters testCase) {
    boolean should = ArtifactPlanCreatorHelper.shouldCreateDelegateTask(testCase);
    assertThat(should).isTrue();
  }

  private Object[][] getTestNoDelegateTask() {
    ArtifactStepParameters testcase1 =
        ArtifactStepParameters.builder()
            .identifier("i1")
            .type(ArtifactSourceType.CUSTOM_ARTIFACT)
            .spec(CustomArtifactConfig.builder().version(ParameterField.createValueField("1.0")).build())
            .build();
    ArtifactStepParameters testcase2 =
        ArtifactStepParameters.builder()
            .identifier("i1")
            .type(ArtifactSourceType.CUSTOM_ARTIFACT)
            .spec(CustomArtifactConfig.builder()
                      .version(ParameterField.createValueField("1.0"))
                      .scripts(CustomArtifactScripts.builder()
                                   .fetchAllArtifacts(
                                       FetchAllArtifacts.builder()
                                           .shellScriptBaseStepInfo(
                                               CustomArtifactScriptInfo.builder()
                                                   .source(CustomArtifactScriptSourceWrapper.builder()
                                                               .spec(CustomScriptInlineSource.builder()
                                                                         .script(ParameterField.createValueField(null))
                                                                         .build())
                                                               .build())
                                                   .build())
                                           .build())
                                   .build())
                      .build())
            .build();
    ArtifactStepParameters testcase3 =
        ArtifactStepParameters.builder()
            .identifier("i1")
            .type(ArtifactSourceType.CUSTOM_ARTIFACT)
            .spec(CustomArtifactConfig.builder()
                      .version(ParameterField.createValueField("1.0"))
                      .scripts(CustomArtifactScripts.builder()
                                   .fetchAllArtifacts(
                                       FetchAllArtifacts.builder()
                                           .shellScriptBaseStepInfo(
                                               CustomArtifactScriptInfo.builder()
                                                   .source(CustomArtifactScriptSourceWrapper.builder()
                                                               .spec(CustomScriptInlineSource.builder()
                                                                         .script(ParameterField.createValueField("  "))
                                                                         .build())
                                                               .build())
                                                   .build())
                                           .build())
                                   .build())
                      .build())
            .build();
    return new Object[][] {{testcase1}, {testcase2}, {testcase3}};
  }

  private Object[][] getTestCreateDelegateTask() {
    ArtifactStepParameters testcase1 =
        ArtifactStepParameters.builder()
            .identifier("i1")
            .type(ArtifactSourceType.CUSTOM_ARTIFACT)
            .spec(
                CustomArtifactConfig.builder()
                    .version(ParameterField.createValueField("1.0"))
                    .scripts(
                        CustomArtifactScripts.builder()
                            .fetchAllArtifacts(
                                FetchAllArtifacts.builder()
                                    .artifactsArrayPath(ParameterField.createValueField("arrayPath"))
                                    .versionPath(ParameterField.createValueField("version"))
                                    .shellScriptBaseStepInfo(
                                        CustomArtifactScriptInfo.builder()
                                            .source(CustomArtifactScriptSourceWrapper.builder()
                                                        .spec(CustomScriptInlineSource.builder()
                                                                  .script(ParameterField.createValueField("echo hello"))
                                                                  .build())
                                                        .build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();
    return new Object[][] {{testcase1}};
  }
}

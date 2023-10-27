/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.category.element.UnitTests;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.utils.StageTimeoutUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.approval.stage.ApprovalStageNode;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class StageTimeoutUtilsTest extends CategoryTest {
  YamlField pipelineYamlField;
  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("complex_pipeline.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
  }
  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldReturnSdkTimeObtainmentFF() {
    long duration = 10;

    SdkTimeoutObtainment sdkTimeoutObtainment =
        StageTimeoutUtils.getStageTimeoutObtainment(ParameterField.createValueField(
            Timeout.builder().timeoutString("10h").timeoutInMillis(TimeUnit.HOURS.toMillis(duration)).build()));

    assertThat(sdkTimeoutObtainment).isNotNull();
    assertThat(sdkTimeoutObtainment.getParameters())
        .isEqualTo(
            AbsoluteSdkTimeoutTrackerParameters.builder()
                .timeout(TimeoutUtils.getTimeoutParameterFieldStringForStage(ParameterField.createValueField(
                    Timeout.builder().timeoutString("10h").timeoutInMillis(TimeUnit.HOURS.toMillis(duration)).build())))
                .build());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldReturnSdkTimeOutObtainment() throws IOException {
    long duration = 10;
    ApprovalStageNode stageNode = new ApprovalStageNode();
    stageNode.setTimeout(ParameterField.createValueField(
        Timeout.builder().timeoutString("10h").timeoutInMillis(TimeUnit.HOURS.toMillis(duration)).build()));
    SdkTimeoutObtainment sdkTimeoutObtainment = StageTimeoutUtils.getStageTimeoutObtainment(stageNode);

    assertThat(sdkTimeoutObtainment).isNotNull();
    assertThat(sdkTimeoutObtainment.getParameters())
        .isEqualTo(
            AbsoluteSdkTimeoutTrackerParameters.builder()
                .timeout(TimeoutUtils.getTimeoutParameterFieldStringForStage(ParameterField.createValueField(
                    Timeout.builder().timeoutString("10h").timeoutInMillis(TimeUnit.HOURS.toMillis(duration)).build())))
                .build());
    stageNode.setTimeout(ParameterField.createValueField(null));
    sdkTimeoutObtainment = StageTimeoutUtils.getStageTimeoutObtainment(stageNode);

    assertThat(sdkTimeoutObtainment).isNull();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldReturnNull() {
    SdkTimeoutObtainment sdkTimeoutObtainment =
        StageTimeoutUtils.getStageTimeoutObtainment(ParameterField.createValueField(null));

    assertThat(sdkTimeoutObtainment).isNull();
  }
}

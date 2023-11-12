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
import io.harness.pms.utils.SdkTimeoutObtainmentUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
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

    ParameterField<Timeout> timeoutParameterField = SdkTimeoutObtainmentUtils.getTimeout(
        ParameterField.createValueField(
            Timeout.builder().timeoutString("10h").timeoutInMillis(TimeUnit.HOURS.toMillis(duration)).build()),
        "10d", false);

    assertThat(timeoutParameterField).isNotNull();
    assertThat(timeoutParameterField.getValue().getTimeoutString()).isEqualTo("10h");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldReturnSdkTimeOutObtainment() throws IOException {
    long duration = 10;
    ParameterField<Timeout> timeoutParameterField = ParameterField.createValueField(
        Timeout.builder().timeoutString("10h").timeoutInMillis(TimeUnit.HOURS.toMillis(duration)).build());
    ParameterField<Timeout> sdkTimeoutObtainment =
        SdkTimeoutObtainmentUtils.getTimeout(timeoutParameterField, "10h", false);

    assertThat(timeoutParameterField).isNotNull();
    assertThat(timeoutParameterField.getValue().getTimeoutString()).isEqualTo("10h");

    sdkTimeoutObtainment = SdkTimeoutObtainmentUtils.getTimeout(ParameterField.createValueField(null), "10d", false);

    assertThat(sdkTimeoutObtainment).isNotNull();
    assertThat(timeoutParameterField.getValue().getTimeoutString()).isEqualTo("10h");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldReturnNull() {
    ParameterField<Timeout> timeoutParameterField =
        SdkTimeoutObtainmentUtils.getTimeout(ParameterField.createValueField(null), "10h", false);
    assertThat(timeoutParameterField).isNotNull();
    assertThat(timeoutParameterField.getValue().getTimeoutString()).isEqualTo("10h");
  }
}

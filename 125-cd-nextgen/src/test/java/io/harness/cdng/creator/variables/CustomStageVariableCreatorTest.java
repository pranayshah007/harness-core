/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.pms.yaml.YAMLFieldNameConstants.CUSTOM;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CustomStageVariableCreatorTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  YamlField yamlField;

  @InjectMocks CustomStageVariableCreator customStageVariableCreator;

  private String SOURCE_PIPELINE_YAML;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String pipeline_yaml_filename = "customStage.yaml";
    SOURCE_PIPELINE_YAML = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipeline_yaml_filename)), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateSupportedTypes() {
    assertThat(customStageVariableCreator.getSupportedTypes())
        .isEqualTo(Collections.singletonMap(YAMLFieldNameConstants.STAGE, Collections.singleton(CUSTOM)));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateFieldClasss() {
    assertThat(customStageVariableCreator.getFieldClass()).isInstanceOf(Class.class);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void shouldValidateVariablesForChildrenNodes() throws IOException {
    String yamlWithUuid = YamlUtils.injectUuid(SOURCE_PIPELINE_YAML);
    YamlField fullYamlFieldWithUuiD = YamlUtils.injectUuidInYamlField(yamlWithUuid);

    VariableCreationContext ctx = VariableCreationContext.builder().build();
    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodes =
        customStageVariableCreator.createVariablesForChildrenNodes(ctx, fullYamlFieldWithUuiD);
    assertThat(variablesForChildrenNodes).isNotNull();
  }
}

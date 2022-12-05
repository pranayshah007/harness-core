/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.ng.core.template.TemplateEntityConstants.STAGE;
import static io.harness.ng.core.template.TemplateEntityConstants.STEP;
import static io.harness.ng.core.template.TemplateEntityConstants.STEP_GROUP;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.TemplateServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.handler.DummyParallelYamlConversionHandler;
import io.harness.template.handler.DummyReplaceYamlConversionHandler;
import io.harness.template.handler.StepGroupTemplateYamlConversionHandler;
import io.harness.template.handler.TemplateYamlConversionHandler;
import io.harness.template.handler.TemplateYamlConversionHandlerRegistry;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class TemplateYamlConversionHelperTest extends TemplateServiceTestBase {
  @Inject TemplateYamlConversionHelper templateYamlConversionHelper;
  @Inject TemplateYamlConversionHandlerRegistry templateYamlConversionHandlerRegistry;

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testConvertStepTemplateYamlToPMSUnderstandableYaml() {
    templateYamlConversionHandlerRegistry.register(STEP, new TemplateYamlConversionHandler());
    String filename = "template-step.yaml";
    String yaml = readFile(filename);
    String newYaml = templateYamlConversionHelper.convertTemplateYamlToEntityYaml(
        TemplateEntity.builder().templateEntityType(TemplateEntityType.STEP_TEMPLATE).yaml(yaml).build());
    assertThat(newYaml).isNotNull();
    assertThat(newYaml).isEqualTo(readFile("pms-template-step.yaml"));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testConvertStageTemplateYamlToPMSUnderstandableYaml() {
    templateYamlConversionHandlerRegistry.register(STAGE, new TemplateYamlConversionHandler());
    String filename = "stage-template.yaml";
    String yaml = readFile(filename);
    String newYaml = templateYamlConversionHelper.convertTemplateYamlToEntityYaml(
        TemplateEntity.builder().templateEntityType(TemplateEntityType.STAGE_TEMPLATE).yaml(yaml).build());
    assertThat(newYaml).isNotNull();
    assertThat(newYaml).isEqualTo(readFile("pms-stage-template.yaml"));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDummyParallelYamlConversionHandler() {
    templateYamlConversionHandlerRegistry.register(STAGE, new DummyParallelYamlConversionHandler());
    String filename = "stage-template.yaml";
    String yaml = readFile(filename);
    String newYaml = templateYamlConversionHelper.convertTemplateYamlToEntityYaml(
        TemplateEntity.builder().templateEntityType(TemplateEntityType.STAGE_TEMPLATE).yaml(yaml).build());
    assertThat(newYaml).isNotNull();
    assertThat(newYaml).isEqualTo(readFile("pms-dummy-parallel-stage-template.yaml"));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testValidStepGroupTemplate() {
    templateYamlConversionHandlerRegistry.register(STEP_GROUP, new StepGroupTemplateYamlConversionHandler());
    String filename = "stepgroup-template.yaml";
    String yaml = readFile(filename);
    String newYaml = templateYamlConversionHelper.convertTemplateYamlToEntityYaml(
        TemplateEntity.builder().templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE).yaml(yaml).build());
    assertThat(newYaml).isNotNull();
    assertThat(newYaml).isEqualTo(readFile("stepgroup-template-replace-stageType.yaml"));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testInvalidStepGroupTemplate() {
    templateYamlConversionHandlerRegistry.register(STEP_GROUP, new StepGroupTemplateYamlConversionHandler());
    String filename = "incorrect-stepgroup-template.yaml";
    String yaml = readFile(filename);
    assertThatThrownBy(
        ()
            -> templateYamlConversionHelper.convertTemplateYamlToEntityYaml(
                TemplateEntity.builder().templateEntityType(TemplateEntityType.STEPGROUP_TEMPLATE).yaml(yaml).build()))
        .isInstanceOf(NGTemplateException.class)
        .hasMessageContaining("yamlNode provided does not have type stageType field");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDummyReplaceYamlConversionHandler() {
    templateYamlConversionHandlerRegistry.register(STAGE, new DummyReplaceYamlConversionHandler());
    String filename = "stage-template.yaml";
    String yaml = readFile(filename);
    String newYaml = templateYamlConversionHelper.convertTemplateYamlToEntityYaml(
        TemplateEntity.builder().templateEntityType(TemplateEntityType.STAGE_TEMPLATE).yaml(yaml).build());
    assertThat(newYaml).isNotNull();
    assertThat(newYaml).isEqualTo(readFile("pms-dummy-replace-stage-template.yaml"));
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.template.utils.TemplateUtils;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class TemplateUtilsTest extends CategoryTest {
  String yaml;
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    String filename = "template.yaml";
    yaml = readFile(filename);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getTemplateYamlFieldElseThrowTest() {
    YamlField yamlField = TemplateUtils.getTemplateYamlFieldElseThrow("org", "proj", "template", yaml);
    assertThat(yamlField).isNotNull();
    assertThat(yamlField.getName()).isEqualTo("template");
    assertThat(yamlField.getType()).isEqualTo("Step");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getTemplateYamlFieldElseThrowEmptyTest() {
    assertThrows(InvalidYamlException.class,
        () -> TemplateUtils.getTemplateYamlFieldElseThrow("org", "proj", "template", "yaml"));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getTemplateYamlFieldElseThrowExceptionTest() throws IOException {
    mockStatic(YamlUtils.class);
    when(YamlUtils.readTree(anyString())).thenThrow(IOException.class);
    assertThrows(InvalidYamlException.class,
        () -> TemplateUtils.getTemplateYamlFieldElseThrow("org", "proj", "template", "yaml"));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void validateAndGetYamlNodeTest() throws IOException {
    YamlNode yamlNode = TemplateUtils.validateAndGetYamlNode(yaml, "template");
    assertThat(yamlNode).isNotNull();
    yamlNode = TemplateUtils.validateAndGetYamlNode(yaml);
    assertThat(yamlNode).isNotNull();
    assertThrows(NGTemplateException.class, () -> TemplateUtils.validateAndGetYamlNode("", "template"));
    assertThrows(NGTemplateException.class, () -> TemplateUtils.validateAndGetYamlNode(""));
    mockStatic(YamlUtils.class);
    when(YamlUtils.readTree(yaml)).thenThrow(IOException.class);
    assertThrows(NGTemplateException.class, () -> TemplateUtils.validateAndGetYamlNode(yaml, "template"));
    assertThrows(NGTemplateException.class, () -> TemplateUtils.validateAndGetYamlNode(yaml));
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}

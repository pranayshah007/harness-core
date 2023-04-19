/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.serializer.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.InputSetValidatorType;
import io.harness.category.element.UnitTests;
import io.harness.multiline.MultilineStringMixin;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ParameterFieldDeserializerTest extends CategoryTest implements MultilineStringMixin {
  private ObjectMapper objectMapper;
  @Before
  public void setUp() {
    objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.registerModule(new NGHarnessJacksonModule());
  }

  @Test
  @Owner(developers = OwnerRule.HARSH)
  @Category(UnitTests.class)
  public void testParameterFieldDeserialization() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yml");
    Pipeline readValue = objectMapper.readValue(testFile, Pipeline.class);
    assertThat(readValue).isNotNull();
    assertThat(readValue.infrastructure.getValue()).isNotNull();
    assertThat(readValue.infrastructure.getValue().inner1.getValue()).isEqualTo("kubernetes-direct");
    assertThat(readValue.infrastructure.isExpression()).isEqualTo(false);
    assertThat(readValue.infrastructure.getValue().inner2.isExpression()).isEqualTo(true);
    assertThat(readValue.infrastructure.getValue().inner2.getExpressionValue()).isEqualTo("<+abc> == \"def\"");
  }

  @Test
  @Owner(developers = OwnerRule.ARCHIT)
  @Category(UnitTests.class)
  public void testParameterFieldInputSetDeserialization() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yml");
    Pipeline readValue = objectMapper.readValue(testFile, Pipeline.class);
    assertThat(readValue).isNotNull();
    assertThat(readValue.infrastructure).isNotNull();
    assertThat(readValue.infrastructure.isExpression()).isEqualTo(false);
    assertThat(readValue.infrastructure.getValue()).isNotNull();
    assertThat(readValue.infrastructure.getValue().inner3.isExpression()).isTrue();
    assertThat(readValue.infrastructure.getValue().inner3.getExpressionValue()).isEqualTo("<+input>");
    assertThat(readValue.infrastructure.getValue().inner3.getInputSetValidator()).isNull();

    assertThat(readValue.infrastructure.getValue().inner4.isExpression()).isTrue();
    assertThat(readValue.infrastructure.getValue().inner4.getExpressionValue()).isEqualTo("<+input>");
    assertThat(readValue.infrastructure.getValue().inner4.getInputSetValidator().getParameters())
        .isEqualTo("dev, <+env>, <+env2>, stage");

    assertThat(readValue.infrastructure.getValue().inner5.isExpression()).isTrue();
    assertThat(readValue.infrastructure.getValue().inner5.getExpressionValue()).isEqualTo("<+input>");
    assertThat(readValue.infrastructure.getValue().inner5.getInputSetValidator().getParameters())
        .isEqualTo("jexl(<+env> == 'prod' ? 'dev, qa':'prod, stage')");

    assertThat(readValue.infrastructure.getValue().inner6.isExpression()).isTrue();
    assertThat(readValue.infrastructure.getValue().inner6.getExpressionValue()).isEqualTo("<+input>");
    assertThat(readValue.infrastructure.getValue().inner6.getInputSetValidator().getParameters()).isEqualTo("^prod*");
    assertThat(readValue.infrastructure.getValue().inner6.getInputSetValidator().getValidatorType())
        .isEqualTo(InputSetValidatorType.REGEX);

    assertThat(readValue.infrastructure.getValue().inner8.isExpression()).isTrue();
    assertThat(readValue.infrastructure.getValue().inner8.getExpressionValue()).isEqualTo("<+input>");
    assertThat(readValue.infrastructure.getValue().inner8.getInputSetValidator().getParameters())
        .isEqualTo("jexl(<+env> == 'dev' ? (<+team> == 'a' ? 'dev_a, dev_b':'dev_qa, dev_qb'):'prod, stage')");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN)
  @Category(UnitTests.class)
  public void testParameterFieldInputSetDeserializationForNonStringFieldsWithAllowedValues()
      throws JsonProcessingException {
    String yaml = "infrastructure:\n"
        + "  inner3:  4.5.allowedValues(1.5, 3.0, 4.5)\n"
        + "  timeout: 1d.allowedValues(12h, 1d)";
    Pipeline readValue = objectMapper.readValue(yaml, Pipeline.class);
    assertThat(readValue).isNotNull();
    assertThat(readValue.infrastructure).isNotNull();
    assertThat(readValue.infrastructure.isExpression()).isEqualTo(false);
    assertThat(readValue.infrastructure.getValue()).isNotNull();

    ParameterField<Double> inner3 = readValue.infrastructure.getValue().inner3;
    assertThat(inner3.isExpression()).isFalse();
    assertThat(inner3.getValue()).isEqualTo(4.5);
    assertThat(inner3.getInputSetValidator().getValidatorType()).isEqualTo(InputSetValidatorType.ALLOWED_VALUES);
    assertThat(inner3.getInputSetValidator().getParameters()).isEqualTo("1.5, 3.0, 4.5");

    ParameterField<Timeout> timeout = readValue.infrastructure.getValue().timeout;
    assertThat(timeout.isExpression()).isFalse();
    assertThat(timeout.getInputSetValidator().getValidatorType()).isEqualTo(InputSetValidatorType.ALLOWED_VALUES);
    assertThat(timeout.getInputSetValidator().getParameters()).isEqualTo("12h, 1d");
    Timeout timeoutValue = timeout.getValue();
    assertThat(timeoutValue.getTimeoutString()).isEqualTo("1d");
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN)
  @Category(UnitTests.class)
  public void testParameterFieldInputSetDeserialization2() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yml");
    Pipeline readValue = objectMapper.readValue(testFile, Pipeline.class);
    assertThat(readValue).isNotNull();
    assertThat(readValue.infrastructure).isNotNull();
    assertThat(readValue.infrastructure.isExpression()).isEqualTo(false);
    assertThat(readValue.infrastructure.getValue()).isNotNull();

    Infrastructure infrastructure = readValue.infrastructure.getValue();

    assertThat(infrastructure.getInner9().isExpression()).isFalse();
    assertThat(infrastructure.getInner9().getValue()).isEqualTo("valueFromInputSet");
    assertThat(infrastructure.getInner9().getInputSetValidator().getValidatorType())
        .isEqualTo(InputSetValidatorType.ALLOWED_VALUES);
    assertThat(infrastructure.getInner9().getInputSetValidator().getParameters()).isEqualTo("dev, nondev, prod");

    assertThat(infrastructure.getInner10().isExpression()).isTrue();
    assertThat(infrastructure.getInner10().getExpressionValue()).isEqualTo("<+dollar.expr.from.inputSet>");
    assertThat(infrastructure.getInner10().getInputSetValidator().getValidatorType())
        .isEqualTo(InputSetValidatorType.ALLOWED_VALUES);
    assertThat(infrastructure.getInner10().getInputSetValidator().getParameters())
        .isEqualTo("dev, <+env>, <+env2>, stage");
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testExtractDefaultValue() throws JsonProcessingException {
    String defaultValueString = "<+input>.executionInput().default(abc)";
    assertThat(objectMapper.readValue(defaultValueString, ParameterField.class).getValue()).isEqualTo("abc");
    defaultValueString = "<+input>.executionInput().default(\"abc\")";
    assertThat(objectMapper.readValue(defaultValueString, ParameterField.class).getValue()).isEqualTo("abc");

    defaultValueString = "<+input>.executionInput().default([\"abc\"])";
    ArrayList responseArray = (ArrayList) objectMapper.readValue(defaultValueString, ParameterField.class).getValue();
    assertThat(responseArray.size()).isEqualTo(1);
    assertThat(responseArray.get(0)).isEqualTo("abc");

    defaultValueString = "<+input>.executionInput().default([\"abc\",\"def\"])";
    responseArray = (ArrayList) objectMapper.readValue(defaultValueString, ParameterField.class).getValue();
    assertThat(responseArray.size()).isEqualTo(2);
    assertThat(responseArray.contains("abc")).isTrue();
    assertThat(responseArray.contains("def")).isTrue();

    defaultValueString = "<+input>.executionInput().default([4,6])";
    responseArray = (ArrayList) objectMapper.readValue(defaultValueString, ParameterField.class).getValue();
    assertThat(responseArray.size()).isEqualTo(2);
    assertThat(responseArray.contains(4.0)).isTrue();
    assertThat(responseArray.contains(6.0)).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void testExtractDefaultValueForObject() throws JsonProcessingException {
    String defaultValueString = "{\"field\":\"<+input>.executionInput().default(abc)\"}";
    DummyClass response = objectMapper.readValue(defaultValueString, DummyClass.class);
    assertThat(response.getField()).isNotNull();
    assertThat(response.getField().getValue().getClass()).isEqualTo(DummyInnerClass.class);
    assertThat(response.getField().getValue().getInnerClassFieldValue()).isEqualTo("abc");
  }

  @Data
  private static class DummyClass {
    @ApiModelProperty(dataType = "string") ParameterField<DummyInnerClass> field;
  }

  @Data
  private static class DummyInnerClass {
    String innerClassFieldValue;
    DummyInnerClass(String value) {
      this.innerClassFieldValue = value;
    }
  }

  @Data
  @Builder
  static class Pipeline {
    private ParameterField<Infrastructure> infrastructure;
  }

  @Data
  @Builder
  static class Infrastructure {
    private ParameterField<String> inner1;
    private ParameterField<List<String>> inner2;
    private ParameterField<String> innerNull;
    private ParameterField<Double> inner3;
    private ParameterField<String> inner4;
    private ParameterField<String> inner5;
    private ParameterField<String> inner6;
    private ParameterField<Double> inner7;
    private ParameterField<String> inner8;
    private ParameterField<String> inner9;
    private ParameterField<String> inner10;
    private ParameterField<Timeout> timeout;
  }
}

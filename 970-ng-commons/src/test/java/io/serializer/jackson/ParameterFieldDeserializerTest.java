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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URL;
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
  }
}

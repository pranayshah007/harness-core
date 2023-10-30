/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.utils.v1;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariableV1;
import io.harness.yaml.core.variables.v1.SecretNGVariableV1;
import io.harness.yaml.core.variables.v1.StringNGVariableV1;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGVariablesUtilsV1Test extends CategoryTest {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchSecretExpression() {
    String secretValueInExpression = "<+pipeline.name>";
    String expectedSecretExpression = NGVariablesUtilsV1.fetchSecretExpression(secretValueInExpression);
    assertThat(expectedSecretExpression).isEqualTo("<+secrets.getValue(" + secretValueInExpression + ")>");

    String secretValueConstant = "secretValue";
    expectedSecretExpression = NGVariablesUtilsV1.fetchSecretExpression(secretValueConstant);
    assertThat(expectedSecretExpression).isEqualTo("<+secrets.getValue(\"" + secretValueConstant + "\")>");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void fetchSecretExpressionWithExpressionToken() {
    Long expressionToken = 1234L;
    String secretValueInExpression = "<+pipeline.name>";
    String expectedSecretExpression =
        NGVariablesUtilsV1.fetchSecretExpressionWithExpressionToken(secretValueInExpression, expressionToken);
    assertThat(expectedSecretExpression).isEqualTo("${ngSecretManager.obtain(<+pipeline.name>, 1234)}");

    String secretValueConstant = "secretValue";
    expectedSecretExpression =
        NGVariablesUtilsV1.fetchSecretExpressionWithExpressionToken(secretValueConstant, expressionToken);
    assertThat(expectedSecretExpression)
        .isEqualTo("${ngSecretManager.obtain(\"" + secretValueConstant + "\", " + expressionToken + ")}");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetSetOfSecretVars() {
    Set<String> secretVars = NGVariablesUtilsV1.getSetOfSecretVars(null);
    assertThat(secretVars).isNotNull();

    Map<String, NGVariableV1> variableMap = new HashMap<>();
    secretVars = NGVariablesUtilsV1.getSetOfSecretVars(variableMap);
    assertThat(secretVars).isNotNull();

    variableMap.put("var1", StringNGVariableV1.builder().value(ParameterField.createValueField("val1")).build());
    secretVars = NGVariablesUtilsV1.getSetOfSecretVars(variableMap);
    assertThat(secretVars).isNotNull();
    assertThat(secretVars.size()).isEqualTo(0);

    variableMap.put("var2",
        SecretNGVariableV1.builder()
            .value(ParameterField.createValueField(SecretRefData.builder().identifier("val1").build()))
            .build());
    secretVars = NGVariablesUtilsV1.getSetOfSecretVars(variableMap);
    assertThat(secretVars).isNotNull();
    assertThat(secretVars).contains("var2");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetMapOfVariables() {
    StringNGVariableV1 var1 =
        StringNGVariableV1.builder().uuid("uuid1").value(ParameterField.createValueField("")).required(true).build();
    StringNGVariableV1 var2 =
        StringNGVariableV1.builder().uuid("uuid2").value(ParameterField.createValueField("")).required(false).build();
    StringNGVariableV1 var3 = StringNGVariableV1.builder()
                                  .uuid("uuid3")
                                  .value(ParameterField.createValueField("value"))
                                  .required(true)
                                  .build();
    assertThatThrownBy(() -> NGVariablesUtilsV1.getMapOfVariables(Map.of("var1", var1, "var2", var2, "var3", var3)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Value not provided for required variable: var1");
    Map<String, Object> variableMap = NGVariablesUtilsV1.getMapOfVariables(Map.of("var3", var3, "var2", var2));
    assertThat(variableMap.size()).isEqualTo(2);
    assertThat(variableMap.get("var3")).isEqualTo(ParameterField.createValueField("value"));
    assertThat(variableMap.get("var2")).isEqualTo(ParameterField.createValueField(""));

    assertThatThrownBy(() -> NGVariablesUtilsV1.getMapOfVariables(Map.of("var1", var1, "var2", var2, "var3", var3), 0L))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Value not provided for required variable: var1");
    variableMap = NGVariablesUtilsV1.getMapOfVariables(Map.of("var2", var2, "var3", var3), 0L);
    assertThat(variableMap.size()).isEqualTo(2);
    assertThat(variableMap.get("var3")).isEqualTo(ParameterField.createValueField("value"));
    assertThat(variableMap.get("var2")).isEqualTo(ParameterField.createValueField(""));

    assertThatThrownBy(()
                           -> NGVariablesUtilsV1.getMapOfVariablesWithoutSecretExpression(
                               Map.of("var1", var1, "var2", var2, "var3", var3)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Value not provided for required variable: var1");
    variableMap = NGVariablesUtilsV1.getMapOfVariablesWithoutSecretExpression(Map.of("var2", var2, "var3", var3));
    assertThat(variableMap.size()).isEqualTo(2);
    assertThat(variableMap.get("var3")).isEqualTo(ParameterField.createValueField("value"));
    assertThat(variableMap.get("var2")).isEqualTo(ParameterField.createValueField(""));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetMapOfVariablesWithExpressions() {
    StringNGVariableV1 var1 = StringNGVariableV1.builder()
                                  .uuid("uuid1")
                                  .value(ParameterField.createExpressionField(true, "<+xyz>", null, false))
                                  .required(true)
                                  .build();
    StringNGVariableV1 var2 = StringNGVariableV1.builder()
                                  .uuid("uuid2")
                                  .value(ParameterField.createExpressionField(true, "<+abc>", null, false))
                                  .required(false)
                                  .build();
    Map<String, Object> variableMap = NGVariablesUtilsV1.getMapOfVariables(Map.of("var1", var1, "var2", var2));
    assertThat(variableMap.size()).isEqualTo(2);
    assertThat(variableMap.get("var2")).isEqualTo(ParameterField.createExpressionField(true, "<+abc>", null, false));
    assertThat(variableMap.get("var1")).isEqualTo(ParameterField.createExpressionField(true, "<+xyz>", null, false));
  }
}

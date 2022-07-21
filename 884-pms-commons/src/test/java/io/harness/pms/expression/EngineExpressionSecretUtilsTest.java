/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expression;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.expression.EngineExpressionSecretUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import lombok.Builder;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class EngineExpressionSecretUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testRevertSecrets() {
    DummyB dummyB1 = DummyB.builder()
                         .str1("str1 ${str1} ${ngSecretManager.obtain(\"str1\", 123)}")
                         .int1(5)
                         .int2(null)
                         .int3(ParameterField.createExpressionField(
                             true, "int3 ${int3} ${ngSecretManager.obtain(\"int3\", 123)}", null, false))
                         .build();
    DummyB dummyB2 = DummyB.builder()
                         .str1("str1 ${str1} ${ngSecretManager.obtain(\"str1\", 123)}")
                         .int1(5)
                         .int2(null)
                         .int3(ParameterField.createExpressionField(
                             true, "int3 ${int3} ${ngSecretManager.obtain(\"int3\", 123)}", null, false))
                         .build();
    DummyA dummyA = DummyA.builder()
                        .str1("str1 ${str1} ${ngSecretManager.obtain(\"str1\", 123)}")
                        .str2(null)
                        .str3(ParameterField.createExpressionField(
                            true, "str3 ${str3} ${ngSecretManager.obtain(\"str3\", 123)}", null, true))
                        .dummyB1(dummyB1)
                        .dummyB2(null)
                        .dummyB3(ParameterField.createExpressionField(
                            true, "dummyB3 ${dummyB3} ${ngSecretManager.obtain(\"dummyB3\", 123)}", null, false))
                        .dummyB4(ParameterField.createValueField(dummyB2))
                        .awsExpression("${AWS::StackName}")
                        .build();

    DummyA res = (DummyA) EngineExpressionSecretUtils.revertSecrets(dummyA);
    assertThat(res).isNotNull();

    // Check null values
    assertThat(res.getStr2()).isNull();
    assertThat(res.getDummyB2()).isNull();
    assertThat(res.getDummyB1().getInt2()).isNull();
    assertThat(res.getDummyB4().getValue().getInt2()).isNull();

    // Non expression values
    assertThat(res.getDummyB1().getInt1()).isEqualTo(5);
    assertThat(res.getDummyB4().getValue().getInt1()).isEqualTo(5);

    assertThat(res.getStr1()).isEqualTo("str1 ${str1} <+secrets.getValue(\"str1\")>");
    assertThat(res.getDummyB1().getStr1()).isEqualTo("str1 ${str1} <+secrets.getValue(\"str1\")>");
    assertThat(res.getDummyB4().getValue().getStr1()).isEqualTo("str1 ${str1} <+secrets.getValue(\"str1\")>");

    assertThat(res.getStr3().getExpressionValue()).isEqualTo("str3 ${str3} <+secrets.getValue(\"str3\")>");

    assertThat(res.getDummyB3().getExpressionValue()).isEqualTo("dummyB3 ${dummyB3} <+secrets.getValue(\"dummyB3\")>");

    assertThat(res.getDummyB1().getInt3().getExpressionValue()).isEqualTo("int3 ${int3} <+secrets.getValue(\"int3\")>");
    assertThat(res.getDummyB4().getValue().getInt3().getExpressionValue())
        .isEqualTo("int3 ${int3} <+secrets.getValue(\"int3\")>");
    assertThat(res.getAwsExpression()).isEqualTo("${AWS::StackName}");
  }

  @Value
  @Builder
  private static class DummyA {
    String str1;
    String str2;
    ParameterField<String> str3;
    DummyB dummyB1;
    DummyB dummyB2;
    ParameterField<DummyB> dummyB3;
    ParameterField<DummyB> dummyB4;
    String awsExpression;
  }

  @Value
  @Builder
  private static class DummyB {
    String str1;
    Integer int1;
    Integer int2;
    ParameterField<Integer> int3;
  }
}

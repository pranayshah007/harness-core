/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import org.joor.Reflect;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MigratorExpressionUtilsTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
  MigratorResolveFunctor migratorResolveFunctor = new MigratorResolveFunctor(
      MigratorExpressionUtils.prepareContextMap(MigrationContext.builder().build(), new HashMap<>(),
          ImmutableMap.of("workflow.variables.var2", "<+pqr>", "app.name", "<+org.name>"), CaseFormat.CAMEL_CASE));

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testRender() {
    Reflect.on(migratorResolveFunctor).set("expressionEvaluator", expressionEvaluator);
    String input =
        "echo ${workflow.variables.var1} ${pipeline.variables.var1} ${infra.kubernetes.namespace} ${workflow.variables.var2} ${app.name}";
    String output = migratorResolveFunctor.processString(input);

    assertThat(output).isEqualTo(
        "echo <+stage.variables.var1> <+pipeline.variables.var1> <+infra.namespace> <+stage.variables.var2> <+org.name>");
  }
}

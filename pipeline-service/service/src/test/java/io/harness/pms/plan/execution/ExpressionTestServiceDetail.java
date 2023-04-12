/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.execution.ExpressionDetails;
import io.harness.pms.expressions.ExpressionDetailServiceImpl;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ExpressionTestServiceDetail extends CategoryTest {
  ExpressionDetailServiceImpl expressionTestService = new ExpressionDetailServiceImpl();

  @Before
  public void setUp() {}

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void test() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode = objectMapper.readTree(getResource("cfStageSchema.json"));
    String exp = "pipeline.stages.cd.spec.execution.steps.ShellScript_1.name";
    List<ExpressionDetails> response = expressionTestService.getAllExpressions(jsonNode, exp);
    assertNotNull(response);
  }

  private String getResource(String path) throws IOException {
    return IOUtils.resourceToString(path, StandardCharsets.UTF_8, this.getClass().getClassLoader());
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CI)
public class GithubApiFunctorTestHelper {
  public static final String EXPRESSION = "<+gitApp.token()>";
  public static final String RESOLVED_EXPRESSION_REGEX = "\\$\\{GIT_APP_TOKEN_[0123456789ABCDEF]+}";

  public static TestObject getTestObject() {
    return GithubApiFunctorTestHelper.TestObject.builder()
        .expression(ParameterField.createExpressionField(true, EXPRESSION, null, true))
        .expressionAsString(EXPRESSION)
        .build();
  }

  @Value
  @Builder
  public static class TestObject {
    ParameterField<String> expression;
    String expressionAsString;
  }
}

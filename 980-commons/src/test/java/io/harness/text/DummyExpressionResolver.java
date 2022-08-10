/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.text;

import io.harness.text.resolver.ExpressionResolver;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class DummyExpressionResolver implements ExpressionResolver {
  private final List<String> expressions = new ArrayList<>();
  private int index = 0;

  @Override
  public String resolveInternal(String expression) {
    expressions.add(expression);
    index++;
    return String.valueOf(index);
  }
}

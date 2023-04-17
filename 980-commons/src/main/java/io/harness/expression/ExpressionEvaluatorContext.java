/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.expression;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.Map;

@OwnedBy(CDP)
public class ExpressionEvaluatorContext {
  private static final ThreadLocal<Map<String, Object>> ctx = new ThreadLocal<>();

  public static void set(Map<String, Object> map) {
    ctx.set(map);
  }

  public static Map<String, Object> get() {
    return ctx.get();
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.functors.ExpressionFunctor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OwnedBy(CDC)
public class RegexFunctor implements ExpressionFunctor {
  public String extract(String pattern, String source) {
    if (source == null) {
      return "";
    }
    final Pattern compiled = Pattern.compile(pattern);
    final Matcher matcher = compiled.matcher(source);
    while (matcher.find()) {
      String match = matcher.group();
      if (!match.isEmpty()) {
        return match;
      }
    }
    return "";
  }

  public String replace(String pattern, String replacement, String source) {
    final Pattern compiled = Pattern.compile(pattern);
    final Matcher matcher = compiled.matcher(source);
    return matcher.replaceAll(replacement);
  }

  public boolean match(String pattern, String source) {
    final Pattern compiled = Pattern.compile(pattern);
    final Matcher matcher = compiled.matcher(source);
    return matcher.find();
  }
}

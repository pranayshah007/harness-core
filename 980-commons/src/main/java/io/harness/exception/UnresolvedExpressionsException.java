/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.UNRESOLVED_EXPRESSIONS_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.Level;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.PIPELINE)
public class UnresolvedExpressionsException extends WingsException {
  private static final String NULL_STR = "null";

  public static final String EXPRESSIONS_ARG = "expressions";

  public UnresolvedExpressionsException(List<String> expressions) {
    super(String.format("Unresolved expressions: %s", prepareExpressionsString(expressions)), null,
        UNRESOLVED_EXPRESSIONS_ERROR, Level.ERROR, null, null);
    super.param(EXPRESSIONS_ARG, prepareExpressionsString(expressions));
  }

  public UnresolvedExpressionsException(List<String> expressions, String hintMessage) {
    super(String.format("Unresolved expressions: %s. %s", prepareExpressionsString(expressions), hintMessage), null,
        UNRESOLVED_EXPRESSIONS_ERROR, Level.ERROR, null, null);
    super.param(EXPRESSIONS_ARG, prepareExpressionsString(expressions));
  }

  public UnresolvedExpressionsException(String key, List<String> expressions) {
    super(String.format("Unresolved expressions: %s", prepareExpressionsAndFieldsString(key, expressions)), null,
        UNRESOLVED_EXPRESSIONS_ERROR, Level.ERROR, null, null);
    super.param(EXPRESSIONS_ARG, prepareExpressionsAndFieldsString(key, expressions));
  }

  public Collection<String> fetchExpressions() {
    String expressionsParam = ((String) getParams().get(EXPRESSIONS_ARG)).trim();
    if (EmptyPredicate.isEmpty(expressionsParam) || expressionsParam.equals(NULL_STR)) {
      return Collections.emptyList();
    }
    return Arrays.stream(StringUtils.split(expressionsParam, ","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
  }

  private static String prepareExpressionsString(List<String> expressions) {
    return expressions == null ? NULL_STR
                               : expressions.stream().filter(Objects::nonNull).collect(Collectors.joining(", "));
  }

  // This function also includes the field name in the error message
  private static String prepareExpressionsAndFieldsString(String key, List<String> expressions) {
    return expressions == null
        ? NULL_STR
        : "'" + key + "': " + expressions.stream().filter(Objects::nonNull).collect(Collectors.joining(", "));
  }
}

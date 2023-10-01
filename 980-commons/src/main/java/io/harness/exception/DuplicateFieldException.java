/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.DUPLICATE_FIELD;

import static java.util.stream.Collectors.joining;

import io.harness.eraro.Level;

import java.util.EnumSet;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;

public class DuplicateFieldException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public DuplicateFieldException(Pair<String, String> args) {
    super(null, null, DUPLICATE_FIELD, Level.ERROR, null, null);
    super.param("args", Stream.of(args).map(pair -> pair.getKey() + ": " + pair.getValue()).collect(joining("; ")));
  }

  public DuplicateFieldException(String message) {
    super(message, null, DUPLICATE_FIELD, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public DuplicateFieldException(String message, EnumSet<ReportTarget> reportTarget, Throwable e) {
    super(message, e, DUPLICATE_FIELD, Level.ERROR, reportTarget, null);
    super.param(MESSAGE_ARG, message);
  }

  public DuplicateFieldException(String message, EnumSet<ReportTarget> reportTarget) {
    super(message, null, DUPLICATE_FIELD, Level.ERROR, reportTarget, null);
    super.param(MESSAGE_ARG, message);
  }
}

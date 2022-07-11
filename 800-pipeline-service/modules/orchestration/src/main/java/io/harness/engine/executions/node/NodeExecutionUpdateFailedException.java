/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.ENGINE_ENTITY_UPDATE_EXCEPTION;
import static io.harness.eraro.ErrorCode.ENGINE_OUTCOME_EXCEPTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

@OwnedBy(CDC)
@SuppressWarnings("squid:CallToDeprecatedMethod")
public class NodeExecutionUpdateFailedException extends WingsException {
  private static final String DETAILS_KEY = "details";

  public NodeExecutionUpdateFailedException(String message) {
    super(message, null, ENGINE_ENTITY_UPDATE_EXCEPTION, Level.ERROR, null, null);
    super.param(DETAILS_KEY, message);
  }

  public NodeExecutionUpdateFailedException(String message, Throwable cause) {
    super(message, cause, ENGINE_OUTCOME_EXCEPTION, Level.ERROR, null, null);
    super.param(DETAILS_KEY, message);
  }
}

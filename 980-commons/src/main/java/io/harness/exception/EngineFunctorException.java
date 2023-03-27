/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.ENGINE_FUNCTOR_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.PIPELINE)
public class EngineFunctorException extends WingsException {
  private static final String REASON_ARG = "reason";

  public EngineFunctorException(String reason) {
    super(reason, null, ENGINE_FUNCTOR_ERROR, Level.ERROR, null, null);
    super.param(REASON_ARG, StringUtils.isBlank(reason) ? "Unknown reason" : reason);
  }

  public EngineFunctorException(String reason, Throwable throwable) {
    super(reason, throwable, ENGINE_FUNCTOR_ERROR, Level.ERROR, null, null);
    super.param(REASON_ARG, StringUtils.isBlank(reason) ? "Unknown reason" : reason);
  }

  public EngineFunctorException(Throwable throwable) {
    super(throwable.getMessage(), throwable, ENGINE_FUNCTOR_ERROR, Level.ERROR, null, null);
    super.param(REASON_ARG, StringUtils.isBlank(throwable.getMessage()) ? "Unknown reason" : throwable.getMessage());
  }
  public String getReason() {
    return (String) getParams().get(REASON_ARG);
  }
}

/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema.exception;

import static io.harness.eraro.ErrorCode.CACHE_NOT_FOUND_EXCEPTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

@OwnedBy(HarnessTeam.PIPELINE)
public class YamlSchemaCacheException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public YamlSchemaCacheException(String message) {
    super(message, null, CACHE_NOT_FOUND_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public YamlSchemaCacheException(String message, Throwable cause) {
    super(message, cause, CACHE_NOT_FOUND_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}

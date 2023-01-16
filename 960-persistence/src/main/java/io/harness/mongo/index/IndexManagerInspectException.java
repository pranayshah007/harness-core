/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class IndexManagerInspectException extends WingsException {
  public IndexManagerInspectException() {
    super(null, null, GENERAL_ERROR, Level.ERROR, null, null);
  }

  public IndexManagerInspectException(String message) {
    super(message, null, GENERAL_ERROR, Level.ERROR, null, null);
  }
}

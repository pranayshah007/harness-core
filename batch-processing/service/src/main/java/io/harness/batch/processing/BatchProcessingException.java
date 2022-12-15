/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing;

import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

/**
 * Generic exception to indicate an error during any of the batch processing steps.
 */
public class BatchProcessingException extends WingsException {
  @SuppressWarnings("squid:CallToDeprecatedMethod")
  public BatchProcessingException(String message, Throwable cause) {
    super(message, cause, ErrorCode.BATCH_PROCESSING_ERROR, Level.ERROR, SRE, null);
  }
}

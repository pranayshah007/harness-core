/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf;

import lombok.Builder;
import lombok.Data;

@Data
public class RetryPolicy {
  private int retry;
  private String userMessageOnFailure = "";
  private String finalErrorMessage = "";
  private boolean throwError;

  @Builder
  public RetryPolicy(int retry, String userMessageOnFailure, String finalErrorMessage, boolean throwError) {
    this.retry = retry == 0 ? RetryAbleTaskExecutor.MIN_RETRY : retry;
    this.userMessageOnFailure = userMessageOnFailure;
    this.finalErrorMessage = finalErrorMessage;
    this.throwError = throwError;
  }
}

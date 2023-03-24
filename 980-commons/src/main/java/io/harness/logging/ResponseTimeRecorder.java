/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.logging;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ResponseTimeRecorder implements AutoCloseable {
  long startTime;
  String message;

  public ResponseTimeRecorder(String message) {
    this.startTime = currentTimeMillis();
    this.message = message;
  }

  @Override
  public void close() {
    log.info(message + ": TIME TAKEN (ms) : {}", currentTimeMillis() - startTime);
  }
}

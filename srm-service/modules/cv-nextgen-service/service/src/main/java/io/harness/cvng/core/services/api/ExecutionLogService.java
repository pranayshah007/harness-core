/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.core.entities.VerificationTaskExecutionInstance;

import java.time.Instant;

public interface ExecutionLogService {
  ExecutionLogger getLogger(VerificationTaskExecutionInstance verificationTaskExecutionInstance);

  ExecutionLogger getLogger(String verificationTaskId, Instant startTime, Instant endTime);
}

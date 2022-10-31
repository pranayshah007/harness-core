/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import java.util.concurrent.Future;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class DelegateTaskExecutionData {
  private long executionStartTime;
  private Future<?> taskFuture;
}

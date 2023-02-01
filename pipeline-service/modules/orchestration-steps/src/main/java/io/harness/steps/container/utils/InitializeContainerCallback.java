/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import java.util.Map;
import java.util.function.Supplier;

public class InitializeContainerCallback implements NotifyCallbackWithErrorHandling {
  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {}
}

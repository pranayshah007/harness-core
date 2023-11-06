/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping.handlers;

import io.harness.delegate.core.beans.TaskPayload;
import io.harness.delegate.service.handlermapping.context.Context;

import com.google.inject.Singleton;
import java.util.Map;

@Singleton
public class CleanupHandler implements Handler {
  @Override
  public void handle(
      String runnerType, TaskPayload taskPayload, Map<String, char[]> decryptedSecrets, Context context) {}
}

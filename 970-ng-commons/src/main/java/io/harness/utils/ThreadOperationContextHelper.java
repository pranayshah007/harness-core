/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitx.ThreadOperationContext;
import io.harness.gitx.USER_FLOW;
import io.harness.manage.GlobalContextManager;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class ThreadOperationContextHelper {
  public USER_FLOW getThreadOperationContextUserFlow() {
    ThreadOperationContext threadOperationContext =
        GlobalContextManager.get(ThreadOperationContext.THREAD_OPERATION_CONTEXT);
    if (threadOperationContext == null) {
      return null;
    }
    return threadOperationContext.getUserFlow();
  }
}

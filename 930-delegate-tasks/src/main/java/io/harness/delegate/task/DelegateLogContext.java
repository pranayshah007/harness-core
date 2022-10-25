/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.logging.logcontext.AutoLogContext;

@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class DelegateLogContext extends AutoLogContext {
  public static final String ACCOUNT_ID = "accountId";
  public static final String DELEGATE_ID = "delegateId";
  public static final String DELEGATE_INSTANCE_ID = "delegateInstanceId";

  public DelegateLogContext(String delegateId, OverrideBehavior behavior) {
    super(DELEGATE_ID, delegateId, behavior);
  }

  public DelegateLogContext(String accountId, String delegateId, String delegateInstanceId, OverrideBehavior behavior) {
    super(NullSafeImmutableMap.<String, String>builder()
              .putIfNotNull(ACCOUNT_ID, accountId)
              .putIfNotNull(DELEGATE_ID, delegateId)
              .putIfNotNull(DELEGATE_INSTANCE_ID, delegateInstanceId)
              .build(),
        behavior);
  }
}

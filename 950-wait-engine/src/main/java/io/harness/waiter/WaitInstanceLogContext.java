/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.waiter;

import io.harness.logging.logcontext.AutoLogContext;
import io.harness.persistence.LogKeyUtils;

public class WaitInstanceLogContext extends AutoLogContext {
  public static final String ID = LogKeyUtils.calculateLogKeyForId(WaitInstance.class);

  public WaitInstanceLogContext(String waitInstanceId, OverrideBehavior behavior) {
    super(ID, waitInstanceId, behavior);
  }
}

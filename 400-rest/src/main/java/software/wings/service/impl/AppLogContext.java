/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.logging.logcontext.AutoLogContext;
import io.harness.persistence.LogKeyUtils;

import software.wings.beans.Application;

public class AppLogContext extends AutoLogContext {
  public static final String ID = LogKeyUtils.logKeyForId(Application.class);

  public AppLogContext(String appId, OverrideBehavior behavior) {
    super(ID, appId, behavior);
  }
}

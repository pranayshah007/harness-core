/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework;

import io.harness.logging.logcontext.AutoLogContext;

public class NgEventLogContext extends AutoLogContext {
  private static String MESSAGE_ID = "messageId";

  public NgEventLogContext(String messageId, OverrideBehavior behavior) {
    super(MESSAGE_ID, messageId, behavior);
  }
}

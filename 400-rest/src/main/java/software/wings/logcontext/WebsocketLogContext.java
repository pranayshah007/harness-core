/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.logcontext;

import io.harness.logging.AutoLogContext;

public class WebsocketLogContext extends AutoLogContext {
  public static final String WEBSOCKET_ID = "websocketId";

  public WebsocketLogContext(final String websocketId, final OverrideBehavior behavior) {
    super(WEBSOCKET_ID, websocketId, behavior);
  }
}

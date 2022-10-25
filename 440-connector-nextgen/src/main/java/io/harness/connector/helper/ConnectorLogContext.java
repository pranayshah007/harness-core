/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.helper;

import static io.harness.NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY;

import io.harness.logging.logcontext.AutoLogContext;

public class ConnectorLogContext extends AutoLogContext {
  public ConnectorLogContext(String connectorIdentifier, OverrideBehavior behavior) {
    super(CONNECTOR_IDENTIFIER_KEY, connectorIdentifier, behavior);
  }
}

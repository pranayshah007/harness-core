/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task;

import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorValidationParams;

/**
 * Validation handler is called by heartbeat during perpetual task on validate to check if ConnectorDisconnectHandler is working fine.
 */

public interface ConnectorValidationHandler {
  ConnectorValidationResult validate(ConnectorValidationParams connectorValidationParams, String accountIdentifier);
}

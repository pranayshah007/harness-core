/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;

/**
 * Marker interface for ConnectorDisconnectHandler heartbeat data which will be sent to delegate via rest call and Validation Handler's
 * validate will be called in perpetual task. If the implementors of this interface must also implement {@link
 * io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander}.
 */
public interface ConnectorValidationParams extends ExecutionCapabilityDemander {
  ConnectorType getConnectorType();
  String getConnectorName();
}

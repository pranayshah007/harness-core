/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(DX)
@UtilityClass
public class ConnectorEvent {
  public static final String CONNECTOR_CREATED = "ConnectorCreated";
  public static final String CONNECTOR_UPDATED = "ConnectorUpdated";
  public static final String CONNECTOR_DELETED = "ConnectorDeleted";
  public static final String CONNECTOR_FORCE_DELETED = "ConnectorForceDeleted";
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.ChangeHandler;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changehandlers.ConnectorsChangeDataHandler;
import io.harness.connector.entities.Connector;

import com.google.inject.Inject;

@OwnedBy(CDP)
public class ConnectorCDCEntity implements CDCEntity<Connector> {
  @Inject private ConnectorsChangeDataHandler connectorsChangeDataHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    return connectorsChangeDataHandler;
  }

  @Override
  public Class<Connector> getSubscriptionEntity() {
    return Connector.class;
  }
}

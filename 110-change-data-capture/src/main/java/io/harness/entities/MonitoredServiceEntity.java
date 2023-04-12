/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.MonitoredServiceChangeDataHandler;
import io.harness.cvng.core.entities.MonitoredService;

import com.google.inject.Inject;

public class MonitoredServiceEntity implements CDCEntity<MonitoredService> {
  @Inject private MonitoredServiceChangeDataHandler monitoredServiceChangeDataHandler;
  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    return monitoredServiceChangeDataHandler;
  }

  @Override
  public Class<MonitoredService> getSubscriptionEntity() {
    return MonitoredService.class;
  }
}

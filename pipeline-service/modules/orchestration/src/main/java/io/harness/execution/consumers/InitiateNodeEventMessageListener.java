/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.consumers;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.execution.InitiateNodeHandler;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.pms.events.base.PmsAbstractMessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class InitiateNodeEventMessageListener
    extends PmsAbstractMessageListener<InitiateNodeEvent, InitiateNodeHandler> {
  @Inject
  public InitiateNodeEventMessageListener(InitiateNodeHandler initiateNodeHandler) {
    super(ModuleType.PMS.name(), InitiateNodeEvent.class, initiateNodeHandler);
  }

  @Override
  public boolean isProcessable(Message message) {
    return true;
  }
}

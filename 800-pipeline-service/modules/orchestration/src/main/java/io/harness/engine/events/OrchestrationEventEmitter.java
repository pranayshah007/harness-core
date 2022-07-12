/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.events;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.OrchestrationEventUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class OrchestrationEventEmitter {
  @Inject private PmsEventSender eventSender;

  public void emitEvent(OrchestrationEvent event) {
    try (AutoLogContext ignore = OrchestrationEventUtils.obtainLogContext(event)) {
      String serviceName =
          isEmpty(event.getServiceName()) ? ModuleType.PMS.name().toLowerCase() : event.getServiceName();
      eventSender.sendEvent(
          event.getAmbiance(), event.toByteString(), PmsEventCategory.ORCHESTRATION_EVENT, serviceName, true);
    } catch (Exception ex) {
      log.error("Failed to create orchestration event of type {}", event.getEventType(), ex);
      throw ex;
    }
  }
}

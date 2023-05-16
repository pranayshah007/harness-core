/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions;

import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.sdk.core.execution.events.orchestration.SdkOrchestrationEventHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Set;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class CDSdkOrchestrationEventHandler extends SdkOrchestrationEventHandler {
  @Inject @Named(SDK_SERVICE_NAME) String serviceName;
  private static final Set<OrchestrationEventType> ORCHESTRATION_START_AND_END_EVENTS =
      Set.of(OrchestrationEventType.ORCHESTRATION_START, OrchestrationEventType.ORCHESTRATION_END);
  @Override
  protected void handleEventWithContext(OrchestrationEvent event) {
    // Handle the event if event.service name is equals to serviceName. Or if event.eventType is one of the
    // orchestrationStartEvent or orchestrationEndEvent.
    if (serviceName.equals(event.getServiceName())
        || ORCHESTRATION_START_AND_END_EVENTS.contains(event.getEventType())) {
      super.handleEventWithContext(event);
    }
  }
}

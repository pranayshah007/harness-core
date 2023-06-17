/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions;

import static io.harness.pms.events.PmsEventFrameworkConstants.SERVICE_NAME;
import static io.harness.pms.sdk.PmsSdkModuleUtils.ORCHESTRATION_EVENT_EXECUTOR_NAME;
import static io.harness.pms.sdk.PmsSdkModuleUtils.SDK_SERVICE_NAME;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.events.base.PmsAbstractMessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class CdngOrchestrationEventMessageListener
    extends PmsAbstractMessageListener<OrchestrationEvent, CDSdkOrchestrationEventHandler> {
  @Inject
  public CdngOrchestrationEventMessageListener(@Named(SDK_SERVICE_NAME) String serviceName,
      CDSdkOrchestrationEventHandler cdSdkOrchestrationEventHandler,
      @Named(ORCHESTRATION_EVENT_EXECUTOR_NAME) ExecutorService executorService) {
    super(serviceName, OrchestrationEvent.class, cdSdkOrchestrationEventHandler, executorService);
  }

  @Override
  public boolean isProcessable(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      return metadataMap.get(SERVICE_NAME) != null
          && (serviceName.equals(metadataMap.get(SERVICE_NAME))
              || ModuleType.PMS.name().toLowerCase().equalsIgnoreCase(metadataMap.get(SERVICE_NAME)));
    }
    return false;
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.pollingevent;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgPollingAutoLogContext;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.pms.sdk.execution.events.PmsCommonsBaseEventHandler;
import io.harness.pms.triggers.build.eventmapper.BuildTriggerEventMapper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.polling.contracts.PollingResponse;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
public class PollingResponseHandler implements PmsCommonsBaseEventHandler<PollingResponse> {
  @Inject private BuildTriggerEventMapper mapper;
  @Inject private TriggerEventExecutionHelper triggerEventExecutionHelper;
  @Inject private TriggerEventHistoryRepository triggerEventHistoryRepository;

  @Override
  public void handleEvent(
      PollingResponse response, Map<String, String> metadataMap, long messageTimeStamp, long readTs) {
    SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));

    if (response == null) {
      return;
    }

    try (AccountLogContext ignore1 = new AccountLogContext(response.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new NgPollingAutoLogContext(response.getPollingDocId(), OVERRIDE_ERROR)) {
      WebhookEventMappingResponse webhookEventMappingResponse = mapper.consumeBuildTriggerEvent(response);
      if (!webhookEventMappingResponse.isFailedToFindTrigger()) {
        List<TriggerEventResponse> responses = triggerEventExecutionHelper.processTriggersForActivation(
            webhookEventMappingResponse.getTriggers(), response);
        if (isNotEmpty(responses)) {
          // TODO: This can be converted to a saveAll call rather
          responses.forEach(resp -> triggerEventHistoryRepository.save(TriggerEventResponseHelper.toEntity(resp)));
        }
      }
    }
  }
}

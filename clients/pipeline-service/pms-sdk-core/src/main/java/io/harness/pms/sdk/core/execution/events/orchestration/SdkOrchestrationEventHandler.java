/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.events.orchestration;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.sdk.PmsSdkModuleUtils;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.registries.OrchestrationEventHandlerRegistry;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class SdkOrchestrationEventHandler extends PmsBaseEventHandler<OrchestrationEvent> {
  @Inject private OrchestrationEventHandlerRegistry handlerRegistry;
  @Inject @Named(PmsSdkModuleUtils.ORCHESTRATION_EVENT_EXECUTOR_NAME) private ExecutorService executorService;

  @Override
  protected Map<String, String> extraLogProperties(OrchestrationEvent event) {
    return ImmutableMap.<String, String>builder().put("eventType", event.getEventType().name()).build();
  }

  @Override
  protected Map<String, String> extractMetricContext(Map<String, String> metadataMap, OrchestrationEvent message) {
    return ImmutableMap.<String, String>builder()
        .put("accountId", AmbianceUtils.getAccountId(message.getAmbiance()))
        .put("orgIdentifier", AmbianceUtils.getOrgIdentifier(message.getAmbiance()))
        .put("projectIdentifier", AmbianceUtils.getProjectIdentifier(message.getAmbiance()))
        .build();
  }

  @Override
  protected String getMetricPrefix(OrchestrationEvent message) {
    return "orchestration_event";
  }

  @Override
  protected Ambiance extractAmbiance(OrchestrationEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected void handleEventWithContext(OrchestrationEvent event) {
    Set<OrchestrationEventHandler> handlers = handlerRegistry.obtain(event.getEventType());
    if (isNotEmpty(handlers)) {
      handlers.forEach(handler -> executorService.submit(() -> {
        try (PmsGitSyncBranchContextGuard ignore1 = gitSyncContext(event);
             AutoLogContext ignore2 = autoLogContext(event)) {
          handler.handleEvent(buildSdkOrchestrationEvent(event));
        } catch (Exception ex) {
          log.warn("Exception occurred while handling orchestrationEvent", ex);
        }
      }));
    }
  }

  protected io.harness.pms.sdk.core.events.OrchestrationEvent buildSdkOrchestrationEvent(OrchestrationEvent event) {
    return io.harness.pms.sdk.core.events.OrchestrationEvent.builder()
        .eventType(event.getEventType())
        .ambiance(event.getAmbiance())
        .status(event.getStatus())
        .resolvedStepParameters(
            RecastOrchestrationUtils.fromJson(event.getStepParameters().toStringUtf8(), StepParameters.class))
        .serviceName(event.getServiceName())
        .triggerPayload(event.getTriggerPayload())
        .endTs(event.getEndTs())
        .moduleInfo(RecastOrchestrationUtils.fromJson(event.getModuleInfo().toStringUtf8(), PipelineModuleInfo.class))
        .tags(generateTagList(event))
        .build();
  }

  private List<String> generateTagList(OrchestrationEvent event) {
    if (event.getTagsList() == null || event.getTagsCount() == 0) {
      return emptyList();
    }

    List<String> tags = new ArrayList<>();
    for (int i = 0; i < event.getTagsCount(); i++) {
      tags.add(event.getTags(i));
    }

    return tags;
  }
}

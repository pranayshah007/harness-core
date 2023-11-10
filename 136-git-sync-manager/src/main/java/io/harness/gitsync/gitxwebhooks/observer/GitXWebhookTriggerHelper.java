/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.gitsync.gitxwebhooks.observer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.gitsync.common.beans.GitXWebhookEventStatus;
import io.harness.gitsync.gitxwebhooks.dtos.GitXWebhookEventUpdateInfo;
import io.harness.gitsync.gitxwebhooks.loggers.GitXWebhookEventLogContext;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})

@Slf4j
@OwnedBy(PIPELINE)
public class GitXWebhookTriggerHelper implements GitXWebhookEventUpdateObserver {
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject @Named(WEBHOOK_EVENTS_STREAM) private Producer eventProducer;

  @Override
  public void onGitXWebhookEventUpdate(GitXWebhookEventUpdateInfo gitXWebhookEventUpdateInfo) {
    try (GitXWebhookEventLogContext context =
             new GitXWebhookEventLogContext(gitXWebhookEventUpdateInfo.getWebhookDTO())) {
      if (ngFeatureFlagHelperService.isEnabled(gitXWebhookEventUpdateInfo.getWebhookDTO().getAccountId(),
              FeatureName.PIE_PROCESS_TRIGGER_SEQUENTIALLY)) {
        if (shouldStartTriggerExecution(gitXWebhookEventUpdateInfo.getEventStatus())) {
          try {
            log.info(String.format(
                "Starting the trigger execution for event %s", gitXWebhookEventUpdateInfo.getEventStatus()));
            eventProducer.send(
                Message.newBuilder().setData(gitXWebhookEventUpdateInfo.getWebhookDTO().toByteString()).build());
          } catch (Exception exception) {
            log.error("Faced exception while sequentially executing the trigger for the event: {} ",
                gitXWebhookEventUpdateInfo.getWebhookDTO().getEventId(), exception);
          }
        }
      }
    }
  }

  private boolean shouldStartTriggerExecution(String eventStatus) {
    return GitXWebhookEventStatus.SUCCESSFUL.name().equals(eventStatus)
        || GitXWebhookEventStatus.FAILED.name().equals(eventStatus)
        || GitXWebhookEventStatus.SKIPPED.name().equals(eventStatus);
  }
}

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.gitsync.gitxwebhooks.loggers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.NgTriggerAutoLogContext.ACCOUNT_KEY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.logging.AutoLogContext;

import java.util.HashMap;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(PIPELINE)
public class GitXWebhookEventLogContext extends AutoLogContext {
  public static final String WEBHOOK_IDENTIFIER_KEY = "webhookIdentifier";
  public static final String EVENT_IDENTIFIER_KEY = "eventIdentifier";
  public static final String EVENT_STATUS = "eventStatus";
  public static final String CONTEXT_KEY = "contextKey";
  public static final String REPO_KEY = "repoName";

  public GitXWebhookEventLogContext(GitXWebhookEvent gitXWebhookEvent) {
    super(setContextMap(gitXWebhookEvent.getAccountIdentifier(), gitXWebhookEvent.getWebhookIdentifier(),
              gitXWebhookEvent.getEventIdentifier(), gitXWebhookEvent.getEventStatus(), null),
        OverrideBehavior.OVERRIDE_NESTS);
  }

  public GitXWebhookEventLogContext(WebhookDTO webhookDTO) {
    super(setContextMap(webhookDTO.getAccountId(), null, webhookDTO.getEventId(), null,
              webhookDTO.getParsedResponse().getPush().getRepo().getName()),
        OverrideBehavior.OVERRIDE_NESTS);
  }

  private static Map<String, String> setContextMap(
      String accountIdentifier, String webhookIdentifier, String eventIdentifier, String eventStatus, String repoName) {
    Map<String, String> logContextMap = new HashMap<>();
    setContextIfNotNull(logContextMap, ACCOUNT_KEY, accountIdentifier);
    setContextIfNotNull(logContextMap, WEBHOOK_IDENTIFIER_KEY, webhookIdentifier);
    setContextIfNotNull(logContextMap, EVENT_IDENTIFIER_KEY, eventIdentifier);
    setContextIfNotNull(logContextMap, EVENT_STATUS, eventStatus);
    setContextIfNotNull(logContextMap, REPO_KEY, repoName);
    setContextIfNotNull(logContextMap, CONTEXT_KEY, String.valueOf(java.util.UUID.randomUUID()));
    return logContextMap;
  }

  private static void setContextIfNotNull(Map<String, String> logContextMap, String key, String value) {
    if (isNotEmpty(value)) {
      logContextMap.putIfAbsent(key, value);
    }
  }
}
